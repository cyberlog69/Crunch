package com.kappi.crunch

import android.app.Application
import android.util.Log
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
                YoutubeDL.getInstance().init(this@CrunchApp)
                Log.i(TAG, "YoutubeDL initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize YoutubeDL", e)
            }
            try {
                FFmpeg.getInstance().init(this@CrunchApp)
                Log.i(TAG, "FFmpeg initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize FFmpeg", e)
            }
            try {
                Aria2c.getInstance().init(this@CrunchApp)
                Log.i(TAG, "Aria2c initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Aria2c", e)
            }

            try {
                val updateManager = com.kappi.crunch.updater.UpdateManager(this@CrunchApp)
                updateManager.createNotificationChannel()
                updateManager.schedulePeriodicAutoUpdate()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure UpdateManager", e)
            }
        }
    }
}
