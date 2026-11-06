package com.kappi.crunch

import android.app.Application
import android.util.Log
import com.kappi.crunch.data.CrunchDatabase
import com.kappi.crunch.data.DownloadHistorySync
import com.kappi.crunch.download.EngineRepair
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "CrunchApp"

class CrunchApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Configure writable temporary directories so Python/yt-dlp/native binaries
        // never attempt to write to Android's read-only root /tmp (preventing Errno 30).
        try {
            val tmpDir = File(cacheDir, "tmp").apply { mkdirs() }
            System.setProperty("java.io.tmpdir", tmpDir.absolutePath)
            android.system.Os.setenv("TMPDIR", tmpDir.absolutePath, true)
            android.system.Os.setenv("TEMP", tmpDir.absolutePath, true)
            android.system.Os.setenv("TMP", tmpDir.absolutePath, true)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not set environment TMPDIR: ${t.message}")
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Recover and sync existing downloads from storage so history is never lost on update
                val dao = CrunchDatabase.getInstance(this@CrunchApp).downloadDao()
                DownloadHistorySync.sync(this@CrunchApp, dao)
            } catch (t: Throwable) {
                Log.w(TAG, "Download history sync failed on startup", t)
            }

            try {
                // Fix interrupted extractions before the libraries trust their
                // "already extracted" version markers.
                EngineRepair.run(this@CrunchApp)
            } catch (t: Throwable) {
                Log.e(TAG, "Engine repair failed", t)
            }
            try {
                YoutubeDL.getInstance().init(this@CrunchApp)
                Log.i(TAG, "YoutubeDL initialized successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize YoutubeDL", t)
            }
            try {
                FFmpeg.getInstance().init(this@CrunchApp)
                Log.i(TAG, "FFmpeg initialized successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize FFmpeg", t)
            }
            try {
                Aria2c.getInstance().init(this@CrunchApp)
                Log.i(TAG, "Aria2c initialized successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize Aria2c", t)
            }

            try {
                val updateManager = com.kappi.crunch.updater.UpdateManager(this@CrunchApp)
                updateManager.createNotificationChannel()
                updateManager.schedulePeriodicAutoUpdate()

                val appUpdateManager = com.kappi.crunch.updater.AppUpdateManager(this@CrunchApp)
                appUpdateManager.createNotificationChannel()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to configure UpdateManager", t)
            }
        }
    }
}
