package com.outletscanner.app.worker

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.outletscanner.app.data.database.AppDatabase
import com.outletscanner.app.util.DataSyncManager
import com.outletscanner.app.util.PrefsManager
import com.outletscanner.app.util.ServerUserManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "periodic_sync"
        const val ACTION_SYNC_COMPLETE = "com.outletscanner.app.SYNC_COMPLETE"
        const val EXTRA_SYNC_TIME = "sync_time"
        const val EXTRA_SYNC_SUCCESS = "sync_success"
        const val EXTRA_ITEMS_SYNCED = "items_synced"
        const val ACTION_SESSION_INVALID = "com.outletscanner.app.SESSION_INVALID"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun uploadPendingMinMax(outlet: String) {
        val dao = AppDatabase.getInstance(applicationContext).minMaxChangeDao()
        val changes = dao.getByOutlet(outlet)
        if (changes.isEmpty()) return

        val prefsManager = PrefsManager(applicationContext)
        val serverUrl = prefsManager.serverUrl.trimEnd('/')

        for (change in changes) {
            val json = JSONObject().apply {
                put("outlet", change.outlet)
                put("itemcode", change.itemcode)
                put("min_qty", change.minQty)
                put("max_qty", change.maxQty)
            }
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$serverUrl/api_minmax.php")
                .post(body)
                .build()
            httpClient.newCall(request).execute().close()
        }

        dao.deleteByOutlet(outlet)
        Log.i(TAG, "Uploaded ${changes.size} min/max changes for $outlet")
    }

    override suspend fun doWork(): Result {
        val prefsManager = PrefsManager(applicationContext)
        val outlet = prefsManager.selectedOutlet
        val serverUrl = prefsManager.serverUrl

        if (outlet.isBlank() || serverUrl.isBlank()) {
            Log.w(TAG, "Skipping sync - outlet or server URL not configured")
            return Result.success()
        }

        return try {
            // Validate session first
            val serverUserManager = ServerUserManager(applicationContext)
            val sessionValid = serverUserManager.validateSession()
            if (!sessionValid) {
                Log.w(TAG, "Session invalid - another device logged in")
                val kickIntent = Intent(ACTION_SESSION_INVALID).apply {
                    setPackage(applicationContext.packageName)
                }
                applicationContext.sendBroadcast(kickIntent)
                return Result.success()
            }

            val syncManager = DataSyncManager(applicationContext)

            val priceCount = syncManager.syncData(outlet)
            Log.i(TAG, "Background price sync: $priceCount items for outlet $outlet")

            val stockCount = syncManager.syncStockData(outlet)
            Log.i(TAG, "Background stock sync: $stockCount QOH updates for outlet $outlet")

            try {
                val poCount = syncManager.syncPoData(outlet)
                Log.i(TAG, "Background PO sync: $poCount records for outlet $outlet")
            } catch (e: Exception) {
                Log.w(TAG, "PO sync failed (non-fatal)", e)
            }

            try {
                uploadPendingMinMax(outlet)
            } catch (e: Exception) {
                Log.w(TAG, "MinMax upload failed (non-fatal)", e)
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val syncTime = sdf.format(Date())

            val intent = Intent(ACTION_SYNC_COMPLETE).apply {
                setPackage(applicationContext.packageName)
                putExtra(EXTRA_SYNC_TIME, syncTime)
                putExtra(EXTRA_SYNC_SUCCESS, true)
                putExtra(EXTRA_ITEMS_SYNCED, stockCount)
            }
            applicationContext.sendBroadcast(intent)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed", e)

            val intent = Intent(ACTION_SYNC_COMPLETE).apply {
                setPackage(applicationContext.packageName)
                putExtra(EXTRA_SYNC_SUCCESS, false)
            }
            applicationContext.sendBroadcast(intent)

            Result.retry()
        }
    }
}
