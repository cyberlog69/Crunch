package com.kappi.crunch.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kappi.crunch.BuildConfig
import com.kappi.crunch.MainActivity
import com.kappi.crunch.R
import com.kappi.crunch.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
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
     * Opens the browser to download the release APK directly.
     *
     * Routing the download through the browser eliminates the need for
     * `android.permission.REQUEST_INSTALL_PACKAGES` in Crunch's manifest, which prevents
     * Google Play Protect from misidentifying Crunch as an unauthorized "Trojan-Dropper".
     */
    fun openDownloadUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser for download URL: $url", e)
            false
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
