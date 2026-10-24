package com.kappi.crunch.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kappi.crunch.BuildConfig
import com.kappi.crunch.MainActivity
import com.kappi.crunch.R
import com.kappi.crunch.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

private const val TAG = "AppUpdateManager"
const val CHANNEL_APP_UPDATES_ID = "crunch_app_updates"
private const val NOTIFICATION_APP_UPDATE_ID = 5002

class AppUpdateManager(private val context: Context) {

    private val prefs = UserPreferences(context)
    private val gson = Gson()

    companion object {
        const val GITHUB_OWNER = "cyberlog69"
        const val GITHUB_REPO = "Crunch"
        private const val API_BASE = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO"
        const val EXTRA_OPEN_UPDATE_DIALOG = "com.kappi.crunch.OPEN_UPDATE_DIALOG"

        /**
         * Compares semantic version strings (e.g., "1.0.3" vs "1.0.2", "v2.0" vs "1.0.2").
         * Returns true if remoteVersion is strictly greater than currentVersion.
         */
        fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
            val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val remoteParts = cleanRemote.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }
            val currentParts = cleanCurrent.split("-", "+")[0].split(".").mapNotNull { it.toIntOrNull() }

            if (remoteParts.isEmpty() || currentParts.isEmpty()) {
                return cleanRemote > cleanCurrent
            }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }

        /**
         * Formats byte size to readable MB string (e.g., "24.5 MB").
         */
        fun formatFileSize(bytes: Long): String {
            if (bytes < 0) return "Unknown size"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return "${DecimalFormat("#,##0.0").format(mb)} MB"
        }

        /**
         * Resolves the best APK asset for the current device architecture.
         */
        fun selectBestApkAsset(assets: List<GitHubAsset>): GitHubAsset? {
            val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAssets.isEmpty()) return null
            if (apkAssets.size == 1) return apkAssets.first()

            val supportedAbis = Build.SUPPORTED_ABIS ?: emptyArray()

            // 1. Exact or prefix match for device ABIs (e.g. arm64-v8a)
            for (abi in supportedAbis) {
                val normalizedAbi = abi.lowercase()
                val match = apkAssets.firstOrNull { it.name.lowercase().contains(normalizedAbi) }
                if (match != null) return match
            }

            // 2. Universal APK match
            val universalMatch = apkAssets.firstOrNull {
                it.name.contains("universal", ignoreCase = true)
            }
            if (universalMatch != null) return universalMatch

            // 3. Release APK match
            val releaseMatch = apkAssets.firstOrNull {
                it.name.contains("release", ignoreCase = true)
            }
            if (releaseMatch != null) return releaseMatch

            // 4. Default to the largest APK or first
            return apkAssets.maxByOrNull { it.size } ?: apkAssets.first()
        }
    }

    /**
     * Checks GitHub Releases for a new version of Crunch.
     */
    suspend fun checkForAppUpdate(): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestReleaseFromGitHub()
            prefs.lastAppUpdateCheck = System.currentTimeMillis()

            if (release == null) {
                return@withContext AppUpdateCheckResult.NoReleasesFound(
                    "No releases published on GitHub yet."
                )
            }

            val currentVer = BuildConfig.VERSION_NAME
            val remoteVer = release.tagName.removePrefix("v").removePrefix("V")

            if (!isNewerVersion(remoteVer, currentVer)) {
                Log.i(TAG, "Crunch is up to date: local=$currentVer, remote=$remoteVer")
                return@withContext AppUpdateCheckResult.UpToDate(currentVer)
            }

            val bestAsset = selectBestApkAsset(release.assets)
            if (bestAsset == null) {
                Log.w(TAG, "Release ${release.tagName} found but has no APK assets attached")
                return@withContext AppUpdateCheckResult.NoReleasesFound(
                    "Release ${release.tagName} has no APK package attached."
                )
            }

            val updateInfo = AppUpdateInfo(
                versionName = remoteVer,
                versionTag = release.tagName,
                title = release.name?.ifBlank { "Crunch $remoteVer" } ?: "Crunch $remoteVer",
                releaseNotes = release.body?.ifBlank { "Bug fixes and performance improvements." }
                    ?: "Bug fixes and performance improvements.",
                publishedAt = release.publishedAt ?: "",
                apkAsset = bestAsset,
                formattedSize = formatFileSize(bestAsset.size),
                releaseUrl = release.htmlUrl ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases",
            )

            Log.i(TAG, "New Crunch update available: $remoteVer (asset: ${bestAsset.name}, size: ${bestAsset.size})")
            AppUpdateCheckResult.UpdateAvailable(updateInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app updates from GitHub", e)
            AppUpdateCheckResult.Error(
                message = e.localizedMessage ?: "Failed to check for updates",
                throwable = e
            )
        }
    }

    private fun fetchLatestReleaseFromGitHub(): GitHubRelease? {
        // Try /releases/latest first
        try {
            val latestUrl = "$API_BASE/releases/latest"
            val conn = (URL(latestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Crunch-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val status = conn.responseCode
            if (status == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                return gson.fromJson(json, GitHubRelease::class.java)
            } else if (status != 404) {
                Log.w(TAG, "GitHub API returned status $status for $latestUrl")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch /releases/latest: ${e.message}")
        }

        // Fallback: check /releases array (in case releases are drafts/prereleases or latest tag is not assigned)
        try {
            val releasesUrl = "$API_BASE/releases?per_page=5"
            val conn = (URL(releasesUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Crunch-Android/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val status = conn.responseCode
            if (status == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<GitHubRelease>>() {}.type
                val list: List<GitHubRelease> = gson.fromJson(json, type) ?: emptyList()
                return list.firstOrNull { !it.draft }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch /releases list: ${e.message}")
        }

        return null
    }

    /**
     * Downloads the APK file to app cache with real-time progress callbacks.
     */
    suspend fun downloadApk(
        asset: GitHubAsset,
        onProgress: (AppDownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.externalCacheDir ?: context.cacheDir, "updates").apply {
            mkdirs()
        }

        // Clean any old APK files in updates dir
        updatesDir.listFiles()?.forEach { file ->
            if (file.extension.equals("apk", ignoreCase = true)) {
                file.delete()
            }
        }

        val targetFile = File(updatesDir, asset.name.ifBlank { "crunch_update.apk" })
        Log.i(TAG, "Downloading update APK from ${asset.downloadUrl} to ${targetFile.absolutePath}")

        var currentUrl = asset.downloadUrl
        var redirects = 0
        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            while (redirects < 6) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Crunch-Android/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/octet-stream")
                }

                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("HTTP redirect $status without Location header")
                    conn.disconnect()
                    currentUrl = location
                    redirects++
                    continue
                }

                if (status !in 200..299) {
                    conn.disconnect()
                    throw IOException("HTTP error $status: ${conn.responseMessage}")
                }

                break
            }

            val totalBytes = if (asset.size > 0) asset.size else (conn?.contentLengthLong ?: -1L)
            inputStream = conn?.inputStream ?: throw IOException("Failed to obtain input stream")
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalDownloaded = 0L
            var lastReportTime = 0L

            onProgress(
                AppDownloadProgress(
                    progress = 0f,
                    bytesDownloaded = 0L,
                    totalBytes = totalBytes,
                    isDone = false
                )
            )

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead

                val now = System.currentTimeMillis()
                if (now - lastReportTime > 100 || totalDownloaded == totalBytes) {
                    lastReportTime = now
                    val progressFloat = if (totalBytes > 0) {
                        (totalDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        -1f
                    }
                    onProgress(
                        AppDownloadProgress(
                            progress = progressFloat,
                            bytesDownloaded = totalDownloaded,
                            totalBytes = totalBytes,
                            isDone = false
                        )
                    )
                }
            }

            outputStream.flush()
            Log.i(TAG, "Download finished successfully: ${targetFile.length()} bytes written")

            // Validate APK integrity
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(targetFile.absolutePath, 0)
            if (pkgInfo == null) {
                targetFile.delete()
                throw IOException("Downloaded package is corrupt or not a valid Android APK")
            }

            onProgress(
                AppDownloadProgress(
                    progress = 1f,
                    bytesDownloaded = targetFile.length(),
                    totalBytes = targetFile.length(),
                    isDone = true
                )
            )

            targetFile
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Checks whether unknown app source installation is allowed.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens system Settings so user can toggle "Allow from this source".
     */
    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Launches the system Android package installer to install the downloaded APK.
     */
    fun installApk(apkFile: File): InstallResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return InstallResult.Error("Update file not found or empty.")
        }

        if (!canInstallPackages()) {
            return InstallResult.PermissionRequired
        }

        val apkUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create FileProvider URI for update APK", e)
            return InstallResult.Error("Failed to share update file: ${e.message}")
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(installIntent)
            InstallResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install Intent", e)
            InstallResult.Error("Could not launch package installer: ${e.message}")
        }
    }

    /**
     * Creates and registers the notification channel for app updates.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_APP_UPDATES_ID,
                "Crunch App Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a new release of Crunch is available on GitHub"
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Posts a notification informing the user that a new release is available.
     */
    fun showAppUpdateNotification(updateInfo: AppUpdateInfo) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE_DIALOG, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIcon = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_crunch_notification)
        } catch (e: Exception) {
            null
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_UPDATES_ID)
            .setSmallIcon(R.drawable.ic_crunch_notification_small)
            .setLargeIcon(appIcon)
            .setColor(0xFF12161C.toInt())
            .setContentTitle("Crunch Update Available (${updateInfo.versionTag})")
            .setContentText("A new release is ready on GitHub • ${updateInfo.formattedSize}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Crunch ${updateInfo.versionTag} is available!\n\n${updateInfo.releaseNotes.take(200)}\n\nTap to download and update."
                )
            )
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_APP_UPDATE_ID, notification)
    }
}
