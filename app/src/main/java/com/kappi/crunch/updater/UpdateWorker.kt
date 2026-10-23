package com.kappi.crunch.updater

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kappi.crunch.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "UpdateWorker"

/**
 * Background worker that automatically checks and applies over-the-air updates
 * for the yt-dlp download engine when network conditions match user preferences.
 */
class UpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = UserPreferences(applicationContext)
        if (!prefs.autoUpdateEngine) {
            Log.d(TAG, "Skipping engine update: autoUpdateEngine is disabled")
            return@withContext Result.success()
        }

        // Avoid checking more than once every 12 hours
        val timeSinceLastCheck = System.currentTimeMillis() - prefs.lastUpdateCheck
        if (timeSinceLastCheck < 12 * 60 * 60 * 1000L) {
            Log.d(TAG, "Skipping engine update: checked recently ($timeSinceLastCheck ms ago)")
            return@withContext Result.success()
        }

        Log.i(TAG, "Starting periodic yt-dlp engine update check...")
        val manager = UpdateManager(applicationContext)
        val result = manager.checkAndUpdateEngine(manual = false)
        Log.i(TAG, "Engine update check finished: isSuccess=${result.isSuccess}, isUpdated=${result.isUpdated}, msg=${result.message}")

        if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
