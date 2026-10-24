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
 * Background worker that automatically checks and applies:
 * 1. Over-the-air updates for the yt-dlp download engine
 * 2. New Crunch app releases published on GitHub
 */
class UpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = UserPreferences(applicationContext)

        // 1. Engine check (yt-dlp)
        if (prefs.autoUpdateEngine) {
            val timeSinceLastCheck = System.currentTimeMillis() - prefs.lastUpdateCheck
            if (timeSinceLastCheck >= 12 * 60 * 60 * 1000L) {
                Log.i(TAG, "Starting periodic yt-dlp engine update check...")
                try {
                    val manager = UpdateManager(applicationContext)
                    val result = manager.checkAndUpdateEngine(manual = false)
                    Log.i(TAG, "Engine update check finished: isSuccess=${result.isSuccess}, isUpdated=${result.isUpdated}")
                } catch (t: Throwable) {
                    Log.w(TAG, "Engine update check failed", t)
                }
            }
        }

        // 2. App check (GitHub Releases)
        if (prefs.autoCheckAppUpdates) {
            val timeSinceLastAppCheck = System.currentTimeMillis() - prefs.lastAppUpdateCheck
            if (timeSinceLastAppCheck >= 12 * 60 * 60 * 1000L) {
                Log.i(TAG, "Starting periodic Crunch app update check from GitHub...")
                try {
                    val appUpdateManager = AppUpdateManager(applicationContext)
                    val result = appUpdateManager.checkForAppUpdate()
                    if (result is AppUpdateCheckResult.UpdateAvailable) {
                        if (result.updateInfo.versionName != prefs.ignoredAppUpdateVersion) {
                            Log.i(TAG, "New app release found in background: ${result.updateInfo.versionTag}")
                            appUpdateManager.showAppUpdateNotification(result.updateInfo)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "App release check failed", t)
                }
            }
        }

        Result.success()
    }
}
