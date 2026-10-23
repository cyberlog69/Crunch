package com.kappi.crunch.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.kappi.crunch.MainActivity
import com.kappi.crunch.R
import com.kappi.crunch.data.UserPreferences
import com.kappi.crunch.download.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "UpdateManager"
const val CHANNEL_UPDATES_ID = "crunch_engine_updates"
const val WORK_ENGINE_UPDATE_PERIODIC = "crunch_engine_periodic_update"
private const val NOTIFICATION_UPDATE_ID = 5001

data class ComponentVersionInfo(
    val ytdlpVersion: String,
    val ffmpegVersion: String,
    val aria2cVersion: String,
    val appVersion: String,
    val lastCheckedTimestamp: Long,
)

data class EngineUpdateResult(
    val isUpdated: Boolean,
    val currentVersion: String,
    val previousVersion: String,
    val message: String,
    val isSuccess: Boolean,
)

class UpdateManager(private val context: Context) {

    private val prefs = UserPreferences(context)
    private val engine = YtDlpEngine(context)

    fun getComponentVersions(): ComponentVersionInfo {
        val ytdlp = engine.getYtDlpVersion() ?: "2025.11.12"
        return ComponentVersionInfo(
            ytdlpVersion = ytdlp,
            ffmpegVersion = "7.1.1 (Native NDK)",
            aria2cVersion = "1.37.0 (Native NDK)",
            appVersion = "1.0",
            lastCheckedTimestamp = prefs.lastUpdateCheck,
        )
    }

    suspend fun checkAndUpdateEngine(manual: Boolean = false): EngineUpdateResult = withContext(Dispatchers.IO) {
        val oldVer = engine.getYtDlpVersion() ?: "2025.11.12"
        Log.i(TAG, "Checking for yt-dlp updates. Current version: $oldVer")

        try {
            val (updated, msg) = engine.updateYoutubeDL()
            val newVer = engine.getYtDlpVersion() ?: oldVer
            prefs.lastUpdateCheck = System.currentTimeMillis()

            if (updated) {
                Log.i(TAG, "yt-dlp updated: $oldVer -> $newVer")
                if (!manual) {
                    showEngineUpdatedNotification(newVer, oldVer)
                }
                EngineUpdateResult(
                    isUpdated = true,
                    currentVersion = newVer,
                    previousVersion = oldVer,
                    message = "yt-dlp engine updated to $newVer (was $oldVer)",
                    isSuccess = true
                )
            } else {
                Log.i(TAG, "yt-dlp is already up to date: $newVer")
                EngineUpdateResult(
                    isUpdated = false,
                    currentVersion = newVer,
                    previousVersion = oldVer,
                    message = "yt-dlp engine is up to date ($newVer)",
                    isSuccess = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp", e)
            EngineUpdateResult(
                isUpdated = false,
                currentVersion = oldVer,
                previousVersion = oldVer,
                message = "Update check failed: ${e.message ?: "Connection error"}",
                isSuccess = false
            )
        }
    }

    fun schedulePeriodicAutoUpdate() {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.autoUpdateEngine) {
            Log.i(TAG, "Auto update is disabled in preferences. Canceling periodic worker.")
            workManager.cancelUniqueWork(WORK_ENGINE_UPDATE_PERIODIC)
            return
        }

        val networkType = if (prefs.updateWifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_ENGINE_UPDATE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
        Log.i(TAG, "Enqueued periodic engine update worker (every 24h, wifiOnly=${prefs.updateWifiOnly})")
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_UPDATES_ID,
                "Engine & Component Updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies when download components like yt-dlp are updated in the background"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showEngineUpdatedNotification(newVersion: String, oldVersion: String) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIcon = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_crunch_notification)
        } catch (e: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setLargeIcon(appIcon)
            .setContentTitle("Crunch Engine Updated")
            .setContentText("yt-dlp updated to $newVersion for maximum site compatibility")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "The download engine was updated from $oldVersion to $newVersion in the background. Stream extraction and format support are refreshed."
                )
            )
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_UPDATE_ID, notification)
    }
}
