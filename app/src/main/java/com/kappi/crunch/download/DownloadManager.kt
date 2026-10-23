package com.kappi.crunch.download

import android.content.Context
import androidx.work.*
import com.kappi.crunch.data.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * High-level coordinator for queuing and cancelling downloads.
 */
object DownloadManager {

    /**
     * Queues a single video/audio download for [item].
     * Returns the updated item with workerId set.
     */
    fun enqueue(context: Context, item: DownloadItem): DownloadItem {
        val workId = UUID.randomUUID()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setId(workId)
            .setInputData(workDataOf(KEY_DOWNLOAD_ID to item.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(item.id)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return item.copy(workerId = workId.toString())
    }

    /**
     * Cancels a running or queued download by item object.
     */
    fun cancel(context: Context, item: DownloadItem) {
        cancelById(context, item.id)
    }

    /**
     * Cancels a running or queued download by download ID.
     */
    fun cancelById(context: Context, downloadId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(downloadId)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(1000 + downloadId.hashCode())
        val db = CrunchDatabase.getInstance(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            db.downloadDao().updateStatus(downloadId, DownloadStatus.CANCELLED)
        }
    }
}
