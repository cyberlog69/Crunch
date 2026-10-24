package com.kappi.crunch.download

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

private const val TAG = "EngineRepair"

private const val PREFS_NAME = "youtubedl-android"
private const val ROOT_DIR = "youtubedl-android"
private const val KEY_DLP_VERSION = "dlpVersion"
private const val KEY_DLP_VERSION_NAME = "dlpVersionName"
private const val MIN_YTDLP_BYTES = 1_000_000L

/**
 * Repairs the extracted youtubedl-android payload before it is used.
 *
 * The library records the extracted package version in SharedPreferences and
 * skips extraction when that value still matches the bundled zip. If extraction
 * was interrupted (process killed, storage full) the preference survives while
 * the files are gone, so Python later fails with
 * "library libandroid-support.so not found". Deleting the stale markers makes
 * the library extract the package again.
 *
 * The same applies to the yt-dlp zipapp: an interrupted self-update leaves a
 * truncated archive behind, which shows up as
 * "zipimport.ZipImportError: bad local file header".
 */
object EngineRepair {

    private data class PackageMarker(
        val prefKey: String,
        val dirName: String,
        val markerPath: String,
    )

    private val packages = listOf(
        PackageMarker("pythonLibVersion", "python", "usr/lib/libandroid-support.so"),
        PackageMarker("ffmpegLibVersion", "ffmpeg", "usr/lib/libavcodec.so"),
        PackageMarker("aria2cLibVersion", "aria2c", "usr/lib/libxml2.so"),
    )

    fun run(context: Context) {
        val root = File(context.noBackupFilesDir, ROOT_DIR)
        if (!root.exists()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stale = packages.filter { marker ->
            prefs.contains(marker.prefKey) && !File(root, "packages/${marker.dirName}/${marker.markerPath}").exists()
        }

        if (stale.isNotEmpty()) {
            Log.w(TAG, "Extracted engine files missing (${stale.joinToString { it.dirName }}); re-extracting")
            val editor = prefs.edit()
            stale.forEach { marker ->
                editor.remove(marker.prefKey)
                File(root, "packages/${marker.dirName}").deleteRecursively()
            }
            editor.apply()
        }

        val ytdlp = File(root, "yt-dlp/yt-dlp")
        if (ytdlp.exists() && !isValidYtDlp(ytdlp)) {
            Log.w(TAG, "yt-dlp archive is corrupt (${ytdlp.length()} bytes); restoring bundled copy")
            prefs.edit()
                .remove(KEY_DLP_VERSION)
                .remove(KEY_DLP_VERSION_NAME)
                .apply()
            if (!restoreYtDlp(context)) {
                // init_ytdlp copies R.raw.ytdlp when the file is absent.
                ytdlp.delete()
                Log.w(TAG, "Restore failed; deleted corrupt copy so init re-extracts it")
            }
        }
    }

    /** Copies the yt-dlp script bundled in the APK over the on-disk copy. */
    fun restoreYtDlp(context: Context): Boolean = try {
        val target = File(File(context.noBackupFilesDir, ROOT_DIR), "yt-dlp/yt-dlp")
        target.parentFile?.mkdirs()
        val rawId = com.yausername.youtubedl_android.R.raw.ytdlp
        context.resources.openRawResource(rawId).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        // The on-disk copy no longer matches the recorded OTA version.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_DLP_VERSION)
            .remove(KEY_DLP_VERSION_NAME)
            .apply()
        true
    } catch (e: Exception) {
        Log.e(TAG, "Could not restore bundled yt-dlp", e)
        false
    }

    /**
     * A yt-dlp release is a zipapp with a shebang prefix. A truncated download
     * keeps the shebang but loses the zip central directory at the end.
     */
    private fun isValidYtDlp(file: File): Boolean {
        val length = file.length()
        if (length < MIN_YTDLP_BYTES) return false

        val header = ByteArray(512)
        val headerRead = file.inputStream().use { it.read(header) }
        if (headerRead <= 0 || indexOf(header, headerRead, ZIP_LOCAL_HEADER) < 0) return false

        val tailSize = minOf(length, 65_536L).toInt()
        val tail = ByteArray(tailSize)
        java.io.RandomAccessFile(file, "r").use { raf ->
            raf.seek(length - tailSize)
            raf.readFully(tail)
        }
        return indexOf(tail, tailSize, ZIP_END_OF_CENTRAL_DIR) >= 0
    }

    private fun indexOf(data: ByteArray, length: Int, pattern: ByteArray): Int {
        if (pattern.isEmpty() || length < pattern.size) return -1
        outer@ for (i in 0..length - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private val ZIP_LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_END_OF_CENTRAL_DIR = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
}
