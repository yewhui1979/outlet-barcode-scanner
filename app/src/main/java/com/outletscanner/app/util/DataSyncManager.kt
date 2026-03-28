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
        .readTimeout(5, TimeUnit.MINUTES) // Large files need more time
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Download data file from the configured server URL and parse it.
     * File naming: {OUTLET}_{timestamp}.txt
     * Returns the number of items synced, or throws an exception on failure.
     */
    suspend fun syncData(
        outlet: String,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val serverUrl = prefsManager.serverUrl.trimEnd('/')
        if (serverUrl.isBlank()) {
            throw IllegalStateException("Server URL not configured. Go to Settings to set it up.")
        }

        // Build the URL - try to download the file for this outlet
        // The server should serve files at: {serverUrl}/{OUTLET}_latest.txt
        // or we try the direct URL if it already contains the file name
        val url = if (serverUrl.endsWith(".txt", ignoreCase = true)) {
            serverUrl
        } else {
            "$serverUrl/${outlet}_latest.txt"
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

        val inputStream: InputStream = body.byteStream()

        val count = repository.parseAndInsert(inputStream, outlet, onProgress)

        // Update last sync timestamp
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        prefsManager.lastSyncTimestamp = sdf.format(Date())

        count
    }

    /**
     * Parse a local file (e.g., from file picker or assets) and import it.
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
}
