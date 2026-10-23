package com.kappi.crunch.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Stores user preferences using SharedPreferences.
 * Intentionally thin — no dependency injection required.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("crunch_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var outputFolder: String
        get() = prefs.getString(KEY_OUTPUT_FOLDER, "") ?: ""
        set(v) = prefs.edit { putString(KEY_OUTPUT_FOLDER, v) }

    var useFastDownloader: Boolean
        get() = prefs.getBoolean(KEY_FAST_DOWNLOADER, true)
        set(v) = prefs.edit { putBoolean(KEY_FAST_DOWNLOADER, v) }

    var downloadThreads: Int
        get() = prefs.getInt(KEY_DOWNLOAD_THREADS, 16)
        set(v) = prefs.edit { putInt(KEY_DOWNLOAD_THREADS, v) }

    var embedThumbnail: Boolean
        get() = prefs.getBoolean(KEY_EMBED_THUMBNAIL, true)
        set(v) = prefs.edit { putBoolean(KEY_EMBED_THUMBNAIL, v) }

    var embedMetadata: Boolean
        get() = prefs.getBoolean(KEY_EMBED_METADATA, true)
        set(v) = prefs.edit { putBoolean(KEY_EMBED_METADATA, v) }

    var maxConcurrentDownloads: Int
        get() = prefs.getInt(KEY_MAX_CONCURRENT, 2)
        set(v) = prefs.edit { putInt(KEY_MAX_CONCURRENT, v) }

    var defaultAudioFormat: String
        get() = prefs.getString(KEY_DEFAULT_AUDIO_FORMAT, "mp3") ?: "mp3"
        set(v) = prefs.edit { putString(KEY_DEFAULT_AUDIO_FORMAT, v) }

    var defaultVideoFormatPref: String
        get() = prefs.getString(KEY_DEFAULT_VIDEO_FORMAT, "bestvideo+bestaudio") ?: "bestvideo+bestaudio"
        set(v) = prefs.edit { putString(KEY_DEFAULT_VIDEO_FORMAT, v) }

    var defaultVideoContainer: String
        get() = prefs.getString(KEY_DEFAULT_VIDEO_CONTAINER, "mkv") ?: "mkv"
        set(v) = prefs.edit { putString(KEY_DEFAULT_VIDEO_CONTAINER, v) }

    var remuxToMkv: Boolean
        get() = prefs.getBoolean(KEY_REMUX_TO_MKV, true)
        set(v) = prefs.edit { putBoolean(KEY_REMUX_TO_MKV, v) }

    var autoUpdateEngine: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE_ENGINE, true)
        set(v) = prefs.edit { putBoolean(KEY_AUTO_UPDATE_ENGINE, v) }

    var updateWifiOnly: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_WIFI_ONLY, true)
        set(v) = prefs.edit { putBoolean(KEY_UPDATE_WIFI_ONLY, v) }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_UPDATE_CHECK, v) }

    var commandTemplates: List<CommandTemplate>
        get() {
            val json = prefs.getString(KEY_TEMPLATES, "[]") ?: "[]"
            val type = object : TypeToken<List<CommandTemplate>>() {}.type
            return gson.fromJson(json, type)
        }
        set(v) = prefs.edit { putString(KEY_TEMPLATES, gson.toJson(v)) }

    companion object {
        private const val KEY_OUTPUT_FOLDER = "output_folder"
        private const val KEY_FAST_DOWNLOADER = "fast_downloader"
        private const val KEY_DOWNLOAD_THREADS = "download_threads"
        private const val KEY_EMBED_THUMBNAIL = "embed_thumbnail"
        private const val KEY_EMBED_METADATA = "embed_metadata"
        private const val KEY_MAX_CONCURRENT = "max_concurrent"
        private const val KEY_DEFAULT_AUDIO_FORMAT = "default_audio_format"
        private const val KEY_DEFAULT_VIDEO_FORMAT = "default_video_format"
        private const val KEY_DEFAULT_VIDEO_CONTAINER = "default_video_container"
        private const val KEY_REMUX_TO_MKV = "remux_to_mkv"
        private const val KEY_AUTO_UPDATE_ENGINE = "auto_update_engine"
        private const val KEY_UPDATE_WIFI_ONLY = "update_wifi_only"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_TEMPLATES = "command_templates"
    }
}
