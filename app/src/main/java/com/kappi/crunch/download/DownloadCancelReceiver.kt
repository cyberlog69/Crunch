package com.kappi.crunch.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

const val ACTION_CANCEL_DOWNLOAD = "com.kappi.crunch.ACTION_CANCEL_DOWNLOAD"

/**
 * BroadcastReceiver triggered by the "Cancel" action button on the downloading notification.
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CANCEL_DOWNLOAD) {
            val downloadId = intent.getStringExtra(KEY_DOWNLOAD_ID) ?: return
            DownloadManager.cancelById(context, downloadId)
        }
    }
}
