package com.outletscanner.app.util

import android.content.Context
import com.outletscanner.app.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DataSyncManager(private val context: Context) {

    private val repository = ProductRepository(context)
    private val prefsManager = PrefsManager(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Check if server file has been modified since our last sync.
     * Uses HTTP HEAD request to check Last-Modified header.
     * Returns true if file is newer or if check fails (download to be safe).
     */
    private fun isServerFileNewer(url: String, lastSyncTimestamp: String?): Boolean {
        if (lastSyncTimestamp.isNullOrBlank()) return true
        try {
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val lastModified = response.header("Last-Modified") ?: return true
            response.close()

            val serverFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            val localFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val serverDate = serverFormat.parse(lastModified) ?: return true
            val localDate = localFormat.parse(lastSyncTimestamp) ?: return true
            return serverDate.after(localDate)
        } catch (_: Exception) {
            return true // Download to be safe if check fails
        }
    }

    /**
     * Download product data file from the configured server URL and parse it.
     * Only downloads if server file is newer than last sync.
     * Returns the number of items synced, or -1 if skipped (already up to date).
     */
    suspend fun syncData(
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured. Go to Settings to set it up.")
        }

        // Daily price file: /data/price/{outlet}.txt
        val url = if (serverUrl.endsWith(".txt", ignoreCase = true)) {
            serverUrl
        } else {
            "$serverUrl/data/price/${outlet}.txt"
        }

        // Skip if file hasn't changed since last sync
        if (!isServerFileNewer(url, prefsManager.lastSyncTimestamp)) {
            return@withContext -1
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}: ${response.message}")
        }

        val body = response.body
            ?: throw Exception("Empty response from server")

        val count = repository.parseAndInsert(body.byteStream(), outlet, onProgress)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastSyncTimestamp = sdf.format(Date())

        count
    }

    /**
     * Download barcode mapping file from the configured server URL and parse it.
     * Only downloads if server file is newer than last sync.
     * Returns the number of barcode mappings synced, or -1 if skipped.
     */
    suspend fun syncBarcodeMappings(
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured. Go to Settings to set it up.")
        }

        // Daily barcode file: /data/barcode/HHT_Barcode.txt
        val url = "$serverUrl/data/barcode/HHT_Barcode.txt"

        // Skip if file hasn't changed since last sync
        if (!isServerFileNewer(url, prefsManager.lastBarcodeSyncTimestamp)) {
            return@withContext -1
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}: ${response.message}")
        }

        val body = response.body
            ?: throw Exception("Empty response from server")

        val count = repository.parseAndInsertBarcodeMappings(body.byteStream(), onProgress)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastBarcodeSyncTimestamp = sdf.format(Date())

        count
    }

    /**
     * Download hourly stock file from server and update QOH.
     * Stock files change frequently, so always download.
     */
    suspend fun syncStockData(
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured.")
        }

        // Hourly stock file: /data/stock/{outlet}.txt
        val url = "$serverUrl/data/stock/${outlet}.txt"

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Server returned ${response.code}: ${response.message}")
        }

        val body = response.body
            ?: throw Exception("Empty response from server")

        val count = repository.parseAndUpdateQoh(body.byteStream(), outlet, onProgress)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastStockSyncTimestamp = sdf.format(Date())

        count
    }

    /**
     * Parse a local product data file and import it.
     */
    suspend fun importFromStream(
        inputStream: InputStream,
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int {
        val count = repository.parseAndInsert(inputStream, outlet, onProgress)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastSyncTimestamp = sdf.format(Date())

        return count
    }

    /**
     * Parse a local barcode mapping file and import it.
     */
    suspend fun importBarcodeMappingsFromStream(
        inputStream: InputStream,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int {
        val count = repository.parseAndInsertBarcodeMappings(inputStream, onProgress)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastBarcodeSyncTimestamp = sdf.format(Date())

        return count
    }
}
