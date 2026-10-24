package com.kappi.crunch.updater

import com.google.gson.annotations.SerializedName
import java.io.File

data class GitHubRelease(
    @SerializedName("tag_name")
    val tagName: String = "",

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("body")
    val body: String? = null,

    @SerializedName("published_at")
    val publishedAt: String? = null,

    @SerializedName("html_url")
    val htmlUrl: String? = null,

    @SerializedName("prerelease")
    val prerelease: Boolean = false,

    @SerializedName("draft")
    val draft: Boolean = false,

    @SerializedName("assets")
    val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name")
    val name: String = "",

    @SerializedName("size")
    val size: Long = 0L,

    @SerializedName("browser_download_url")
    val downloadUrl: String = "",

    @SerializedName("content_type")
    val contentType: String? = null
)

data class AppUpdateInfo(
    val versionName: String,
    val versionTag: String,
    val title: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkAsset: GitHubAsset,
    val formattedSize: String,
    val releaseUrl: String,
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val updateInfo: AppUpdateInfo) : AppUpdateCheckResult
    data class UpToDate(val currentVersion: String) : AppUpdateCheckResult
    data class NoReleasesFound(val message: String) : AppUpdateCheckResult
    data class Error(val message: String, val throwable: Throwable? = null) : AppUpdateCheckResult
}

data class AppDownloadProgress(
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isDone: Boolean = false
)

sealed interface InstallResult {
    data object Success : InstallResult
    data object PermissionRequired : InstallResult
    data class Error(val message: String) : InstallResult
}
