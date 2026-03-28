package com.outletscanner.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.outletscanner.app.util.DataSyncManager
import com.outletscanner.app.util.PrefsManager

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "periodic_sync"
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
            val syncManager = DataSyncManager(applicationContext)
            val count = syncManager.syncData(outlet)
            Log.i(TAG, "Background sync complete: $count items for outlet $outlet")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed", e)
            Result.retry()
        }
    }
}
