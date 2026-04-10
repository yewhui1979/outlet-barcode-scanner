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
     * Download product data file from the configured server URL and parse it.
     * Returns the number of items synced.
     */
    suspend fun syncData(
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured. Go to Settings to set it up.")
        }

        val url = if (serverUrl.endsWith(".txt", ignoreCase = true)) {
            serverUrl
        } else {
            "$serverUrl/data/${outlet}.txt"
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
     * Returns the number of barcode mappings synced.
     */
    suspend fun syncBarcodeMappings(
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured. Go to Settings to set it up.")
        }

        // Barcode mapping file at: {serverUrl}/data/HHT_Barcode.txt
        val url = "$serverUrl/data/HHT_Barcode.txt"

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
     * Download hourly stock file (PS__) from server and update QOH.
     * File pattern: {serverUrl}/data/{outlet}_stock.txt
     */
    suspend fun syncStockData(
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured.")
        }

        // Hourly stock file: {outlet}_stock.txt
        val url = "$serverUrl/data/${outlet}_stock.txt"

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
