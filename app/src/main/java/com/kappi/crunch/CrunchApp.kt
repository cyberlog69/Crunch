package com.kappi.crunch

import android.app.Application
import android.util.Log
import com.kappi.crunch.download.EngineRepair
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "CrunchApp"

class CrunchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
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
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to configure UpdateManager", t)
            }
        }
    }
}
