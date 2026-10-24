package com.kappi.crunch.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.kappi.crunch.MainActivity
import com.kappi.crunch.R
import com.kappi.crunch.data.*
import com.kappi.crunch.util.FileLocation
import com.kappi.crunch.util.FormatUtils
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

const val KEY_DOWNLOAD_ID = "download_id"
private const val NOTIFICATION_ID_BASE = 1000
private const val COMPLETED_NOTIFICATION_ID_BASE = 2000
private const val CHANNEL_DOWNLOADING_ID = "crunch_downloads"
private const val CHANNEL_COMPLETED_ID = "crunch_completed"
private const val PROGRESS_THROTTLE_MS = 500L

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

    private fun getAppIcon(): Bitmap? {
        return try {
            BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_crunch_notification)
        } catch (e: Exception) {
            null
        }
    }

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
            .setSmallIcon(R.drawable.ic_stat_crunch)
            .setLargeIcon(getAppIcon())
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

        val notifId = NOTIFICATION_ID_BASE + downloadId.hashCode()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            // Mark as downloading
            dao.update(item.copy(status = DownloadStatus.DOWNLOADING, progress = 0f))
            try {
                setForeground(buildForegroundInfo(item, 0f, "", ""))
            } catch (e: Exception) {
                Log.w("DownloadWorker", "Unable to set foreground: ${e.message}")
            }

            // yt-dlp writes into app-private staging first; the finished file is
            // published to the public Downloads/Crunch collection afterwards.
            val outputDir = stagingDir()

            // Inherit fast downloader preference if item doesn't explicitly disable it
            val useFast = item.useExternalDownloader || prefs.useFastDownloader
            val threads = prefs.downloadThreads
            val lastPersist = AtomicLong(0L)

            val stagedPath = engine.download(
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
                processId = downloadId,
                onProgress = { progress, speed, eta ->
                    // yt-dlp emits several lines per second; persist at most twice a second.
                    val now = SystemClock.elapsedRealtime()
                    val previous = lastPersist.get()
                    if (now - previous < PROGRESS_THROTTLE_MS && progress < 1f) return@download
                    if (!lastPersist.compareAndSet(previous, now)) return@download

                    kotlinx.coroutines.runBlocking {
                        dao.updateProgress(downloadId, progress, speed, eta)
                    }
                    setProgressAsync(workDataOf(
                        "progress" to progress,
                        "speed" to speed,
                        "eta" to eta,
                    ))
                    nm.notify(notifId, buildNotification(item, progress, speed, eta).build())
                }
            )

            if (stagedPath.isEmpty()) throw IOException("yt-dlp finished without producing a file")

            val stagedFile = File(stagedPath)
            val fileSize = stagedFile.takeIf { it.exists() }?.length() ?: 0L
            val filePath = try {
                publishToDownloads(stagedFile)
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Could not publish to Downloads, keeping staging copy", e)
                stagedFile.absolutePath
            }

            // Mark completed in database
            dao.markCompleted(downloadId, filePath, fileSize)

            // Dismiss active progress notification
            nm.cancel(notifId)

            // Post Download Completed notification (sound, vibration, tap-to-play, share)
            showCompletedNotification(item, filePath, fileSize)

            return Result.success(workDataOf("filePath" to filePath))

        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                YtDlpEngine.cancelProcess(downloadId)
                dao.updateStatus(downloadId, DownloadStatus.CANCELLED)
                nm.cancel(notifId)
            }
            throw e
        } catch (e: Exception) {
            // A user cancellation destroys the yt-dlp process, which surfaces here
            // as a CanceledException. Do not overwrite CANCELLED with FAILED.
            val cancelled = e is YoutubeDL.CanceledException ||
                withContext(NonCancellable) { dao.getById(downloadId)?.status == DownloadStatus.CANCELLED }
            if (cancelled) {
                withContext(NonCancellable) {
                    dao.updateStatus(downloadId, DownloadStatus.CANCELLED)
                    nm.cancel(notifId)
                }
                return Result.failure()
            }

            Log.e("DownloadWorker", "Download failed for item $downloadId", e)
            val errorMessage = FormatUtils.conciseError(e.message)
            withContext(NonCancellable) {
                dao.updateStatus(downloadId, DownloadStatus.FAILED, errorMessage)
                nm.cancel(notifId)
            }

            // Post Download Failed notification
            showFailedNotification(item, errorMessage)

            return Result.failure(workDataOf("error" to errorMessage))
        }
    }

    private fun stagingDir(): String {
        val base = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(applicationContext.filesDir, "Download")
        val dir = File(base, "Crunch")
        dir.mkdirs()
        return dir.absolutePath
    }

    /**
     * Copies a finished download into the public Downloads/Crunch folder and
     * returns its content URI (API 29+) or absolute path (legacy storage).
     */
    private fun publishToDownloads(file: File): String {
        if (!file.exists()) throw IOException("Finished file is missing: ${file.absolutePath}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, FormatUtils.mimeTypeFor(file.name))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Crunch"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IOException("MediaStore rejected ${file.name}")

            try {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: throw IOException("Could not open MediaStore output for ${file.name}")

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }

            file.delete()
            return uri.toString()
        }

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloads, "Crunch").apply { mkdirs() }
        val destination = uniqueDestination(targetDir, file.name)
        file.copyTo(destination, overwrite = false)
        file.delete()
        return destination.absolutePath
    }

    private fun uniqueDestination(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate

        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val name = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            candidate = File(dir, name)
            index++
        }
        return candidate
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
            .setSmallIcon(R.drawable.ic_stat_crunch)
            .setLargeIcon(getAppIcon())
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

        val sizeStr = FormatUtils.formatFilesize(fileSize)
        val subtitle = if (sizeStr.isNotEmpty()) "Finished downloading • $sizeStr • Tap to open" else "Finished downloading • Tap to open"

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_COMPLETED_ID)
            .setSmallIcon(R.drawable.ic_stat_crunch)
            .setLargeIcon(getAppIcon())
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
            .setSmallIcon(R.drawable.ic_stat_crunch)
            .setLargeIcon(getAppIcon())
            .setContentTitle("Download Failed: ${item.title.take(50)}")
            .setContentText(errorMsg)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n$errorMsg"))
            .setContentIntent(defaultPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        nm.notify(notifId, builder.build())
    }

    private fun createOpenFilePendingIntent(filePath: String, notificationId: Int): PendingIntent? {
        val uri = FileLocation.toUri(applicationContext, filePath) ?: return null
        val mimeType = FormatUtils.mimeTypeFor(filePath)

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
        val uri = FileLocation.toUri(applicationContext, filePath) ?: return null
        val mimeType = FormatUtils.mimeTypeFor(filePath)

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
}
