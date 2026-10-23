package com.kappi.crunch.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.*
import com.kappi.crunch.MainActivity
import com.kappi.crunch.R
import com.kappi.crunch.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

const val KEY_DOWNLOAD_ID = "download_id"
private const val NOTIFICATION_ID_BASE = 1000
private const val COMPLETED_NOTIFICATION_ID_BASE = 2000
private const val CHANNEL_DOWNLOADING_ID = "crunch_downloads"
private const val CHANNEL_COMPLETED_ID = "crunch_completed"

/**
 * WorkManager CoroutineWorker that runs yt-dlp downloading.
 * Supports multithreaded chunk splitting, concurrent DASH/HLS fragment fetching,
 * live downloading notifications with speed/ETA, and completed notifications with play/share actions.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = CrunchDatabase.getInstance(appContext)
    private val dao = db.downloadDao()
    private val engine = YtDlpEngine(appContext)
    private val prefs = UserPreferences(appContext)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannels()
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: ""
        val item = if (downloadId.isNotEmpty()) dao.getById(downloadId) else null
        val title = item?.title?.take(60)?.ifEmpty { "Crunch Download" } ?: "Crunch Download"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_DOWNLOADING_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("Crunch download in progress…")
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .build()
        val notifId = if (downloadId.isNotEmpty()) NOTIFICATION_ID_BASE + downloadId.hashCode() else NOTIFICATION_ID_BASE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return Result.failure()

        val item = dao.getById(downloadId) ?: return Result.failure()

        // Create notification channels
        createNotificationChannels()

        try {
            // Mark as downloading
            dao.update(item.copy(status = DownloadStatus.DOWNLOADING, progress = 0f))
            try {
                setForeground(buildForegroundInfo(item, 0f, "", ""))
            } catch (e: Exception) {
                Log.w("DownloadWorker", "Unable to set foreground: ${e.message}")
            }

            // Determine output directory
            val outputDir = resolveOutputDir()

            // Inherit fast downloader preference if item doesn't explicitly disable it
            val useFast = item.useExternalDownloader || prefs.useFastDownloader
            val threads = prefs.downloadThreads

            val filePath = engine.download(
                url = item.url,
                downloadType = item.downloadType,
                formatId = item.formatId,
                audioFormat = item.audioFormat,
                videoContainer = prefs.defaultVideoContainer,
                outputDir = outputDir,
                subtitleLang = item.subtitleLang,
                embedSubtitles = item.embedSubtitles,
                embedThumbnail = prefs.embedThumbnail,
                embedMetadata = prefs.embedMetadata,
                useExternalDownloader = useFast,
                downloadThreads = threads,
                customExtraArgs = item.customTemplate,
                onProgress = { progress, speed, eta ->
                    // Persist progress to Room
                    kotlinx.coroutines.runBlocking {
                        dao.updateProgress(downloadId, progress, speed, eta)
                    }
                    setProgressAsync(workDataOf(
                        "progress" to progress,
                        "speed" to speed,
                        "eta" to eta,
                    ))
                    // Update active downloading notification
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(
                        NOTIFICATION_ID_BASE + downloadId.hashCode(),
                        buildNotification(item, progress, speed, eta).build()
                    )
                }
            )

            // Mark completed in database
            val fileSize = File(filePath).takeIf { it.exists() }?.length() ?: 0L
            dao.markCompleted(downloadId, filePath, fileSize)

            // Dismiss active progress notification
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID_BASE + downloadId.hashCode())

            // Post Download Completed notification (sound, vibration, tap-to-play, share)
            showCompletedNotification(item, filePath, fileSize)

            return Result.success(workDataOf("filePath" to filePath))

        } catch (e: Exception) {
            Log.e("DownloadWorker", "Download failed for item $downloadId", e)
            dao.updateStatus(downloadId, DownloadStatus.FAILED, e.message ?: "Unknown error")

            // Dismiss active downloading notification
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID_BASE + downloadId.hashCode())

            // Post Download Failed notification
            showFailedNotification(item, e.message ?: "Download failed")

            return Result.failure(workDataOf("error" to (e.message ?: "")))
        }
    }

    private fun resolveOutputDir(): String {
        val prefFolder = prefs.outputFolder
        return if (prefFolder.isNotEmpty()) {
            prefFolder
        } else {
            // Default: Downloads/Crunch
            val dir = File(
                applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
                "Crunch"
            )
            dir.mkdirs()
            dir.absolutePath
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Channel for active downloads (low importance so it doesn't buzz on every progress update)
            val downloadingChannel = NotificationChannel(
                CHANNEL_DOWNLOADING_ID,
                "Active Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress, speed, and ETA"
                setShowBadge(false)
            }
            nm.createNotificationChannel(downloadingChannel)

            // 2. Channel for completed downloads (default importance with sound & vibration)
            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED_ID,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when a video or audio download completes"
                setShowBadge(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(completedChannel)
        }
    }

    private fun buildForegroundInfo(
        item: DownloadItem,
        progress: Float,
        speed: String = "",
        eta: String = ""
    ): ForegroundInfo {
        val notification = buildNotification(item, progress, speed, eta).build()
        val notifId = NOTIFICATION_ID_BASE + item.id.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun buildNotification(
        item: DownloadItem,
        progress: Float,
        speed: String = "",
        eta: String = ""
    ): NotificationCompat.Builder {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pct = (progress * 100).toInt().coerceIn(0, 100)

        val progressText = buildString {
            if (progress > 0) append("$pct%") else append("Starting…")
            if (speed.isNotBlank()) append(" • $speed")
            if (eta.isNotBlank()) append(" • ETA $eta")
        }

        // Cancel action
        val cancelIntent = Intent(applicationContext, DownloadCancelReceiver::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(KEY_DOWNLOAD_ID, item.id)
        }
        val cancelPi = PendingIntent.getBroadcast(
            applicationContext,
            item.id.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(applicationContext, CHANNEL_DOWNLOADING_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(item.title.take(60))
            .setContentText(progressText)
            .setProgress(100, pct, progress <= 0f)
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPi)
    }

    private fun showCompletedNotification(item: DownloadItem, filePath: String, fileSize: Long) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = COMPLETED_NOTIFICATION_ID_BASE + item.id.hashCode()

        val openPi = createOpenFilePendingIntent(filePath, notifId)
        val sharePi = createShareFilePendingIntent(filePath, notifId)

        val defaultIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val defaultPi = PendingIntent.getActivity(
            applicationContext,
            notifId,
            defaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sizeStr = formatFilesize(fileSize)
        val subtitle = if (sizeStr.isNotEmpty()) "Finished downloading • $sizeStr • Tap to open" else "Finished downloading • Tap to open"

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETED_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete: ${item.title.take(50)}")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n$subtitle"))
            .setContentIntent(openPi ?: defaultPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (openPi != null) {
            builder.addAction(android.R.drawable.ic_media_play, "Open", openPi)
        }
        if (sharePi != null) {
            builder.addAction(android.R.drawable.ic_menu_share, "Share", sharePi)
        }

        nm.notify(notifId, builder.build())
    }

    private fun showFailedNotification(item: DownloadItem, errorMsg: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = COMPLETED_NOTIFICATION_ID_BASE + item.id.hashCode()

        val defaultIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val defaultPi = PendingIntent.getActivity(
            applicationContext,
            notifId,
            defaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETED_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed: ${item.title.take(50)}")
            .setContentText(errorMsg)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n$errorMsg"))
            .setContentIntent(defaultPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notifId, builder.build())
    }

    private fun createOpenFilePendingIntent(filePath: String, notificationId: Int): PendingIntent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = try {
            FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Failed to get file URI", e)
            return null
        }

        val mimeType = when {
            file.extension.equals("mkv", ignoreCase = true) -> "video/x-matroska"
            file.extension.equals("mp4", ignoreCase = true) -> "video/mp4"
            file.extension.equals("webm", ignoreCase = true) -> "video/webm"
            file.extension.equals("mp3", ignoreCase = true) -> "audio/mpeg"
            file.extension.equals("m4a", ignoreCase = true) -> "audio/mp4"
            file.extension.equals("opus", ignoreCase = true) -> "audio/opus"
            file.extension.equals("flac", ignoreCase = true) -> "audio/flac"
            file.extension.equals("wav", ignoreCase = true) -> "audio/wav"
            else -> "*/*"
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            applicationContext,
            notificationId,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createShareFilePendingIntent(filePath: String, notificationId: Int): PendingIntent? {
        val file = File(filePath)
        if (!file.exists()) return null
        val uri = try {
            FileProvider.getUriForFile(
                applicationContext,
                "${applicationContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            return null
        }

        val mimeType = when {
            file.extension.equals("mkv", ignoreCase = true) -> "video/x-matroska"
            file.extension.equals("mp4", ignoreCase = true) -> "video/mp4"
            file.extension.equals("webm", ignoreCase = true) -> "video/webm"
            file.extension.equals("mp3", ignoreCase = true) -> "audio/mpeg"
            file.extension.equals("m4a", ignoreCase = true) -> "audio/mp4"
            file.extension.equals("opus", ignoreCase = true) -> "audio/opus"
            file.extension.equals("flac", ignoreCase = true) -> "audio/flac"
            file.extension.equals("wav", ignoreCase = true) -> "audio/wav"
            else -> "*/*"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Downloaded Media").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return PendingIntent.getActivity(
            applicationContext,
            notificationId + 1,
            chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatFilesize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }
}
