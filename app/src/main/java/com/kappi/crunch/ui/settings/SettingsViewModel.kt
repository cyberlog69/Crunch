package com.kappi.crunch.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kappi.crunch.BuildConfig
import com.kappi.crunch.data.CommandTemplate
import com.kappi.crunch.data.UserPreferences
import com.kappi.crunch.updater.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SettingsState(
    val useFastDownloader: Boolean = true,
    val downloadThreads: Int = 16,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val defaultAudioFormat: String = "mp3",
    val defaultVideoContainer: String = "mkv",
    val commandTemplates: List<CommandTemplate> = emptyList(),

    // Engine & Components
    val autoUpdateEngine: Boolean = true,
    val updateWifiOnly: Boolean = true,
    val ytdlpVersion: String = "unknown",
    val ffmpegVersion: String = "7.1.1 (Native)",
    val aria2cVersion: String = "1.37.0 (Native)",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val lastUpdateCheckTime: Long = 0L,
    val isCheckingUpdate: Boolean = false,
    val updateStatusMessage: String? = null,
    val isUpdateSuccess: Boolean? = null,

    // App Self-Updater (GitHub Releases)
    val autoCheckAppUpdates: Boolean = true,
    val availableAppUpdate: AppUpdateInfo? = null,
    val isCheckingAppUpdate: Boolean = false,
    val isDownloadingAppUpdate: Boolean = false,
    val appUpdateDownloadProgress: Float = 0f,
    val appUpdateDownloadBytes: Long = 0L,
    val appUpdateDownloadTotalBytes: Long = 0L,
    val downloadedApkFile: File? = null,
    val showAppUpdateDialog: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val appUpdateStatusMessage: String? = null,
    val isAppUpdateSuccess: Boolean? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferences(application)
    private val updateManager = UpdateManager(application)
    private val appUpdateManager = AppUpdateManager(application)

    private val _state = MutableStateFlow(
        SettingsState(
            useFastDownloader = prefs.useFastDownloader,
            downloadThreads = prefs.downloadThreads,
            embedThumbnail = prefs.embedThumbnail,
            embedMetadata = prefs.embedMetadata,
            defaultAudioFormat = prefs.defaultAudioFormat,
            defaultVideoContainer = prefs.defaultVideoContainer,
            commandTemplates = prefs.commandTemplates,
            autoUpdateEngine = prefs.autoUpdateEngine,
            updateWifiOnly = prefs.updateWifiOnly,
            lastUpdateCheckTime = prefs.lastUpdateCheck,
            autoCheckAppUpdates = prefs.autoCheckAppUpdates,
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadComponentVersions()
        // If auto-check is enabled, run an initial check
        if (prefs.autoCheckAppUpdates) {
            val elapsed = System.currentTimeMillis() - prefs.lastAppUpdateCheck
            if (elapsed > 4 * 60 * 60 * 1000L) { // Check if more than 4h elapsed
                checkForAppUpdates(manual = false)
            }
        }
    }

    private fun loadComponentVersions() {
        viewModelScope.launch(Dispatchers.IO) {
            val versions = updateManager.getComponentVersions()
            _state.update {
                it.copy(
                    ytdlpVersion = versions.ytdlpVersion,
                    ffmpegVersion = versions.ffmpegVersion,
                    aria2cVersion = versions.aria2cVersion,
                    appVersion = versions.appVersion,
                    lastUpdateCheckTime = versions.lastCheckedTimestamp,
                )
            }
        }
    }

    fun setAutoUpdateEngine(enabled: Boolean) {
        prefs.autoUpdateEngine = enabled
        _state.update { it.copy(autoUpdateEngine = enabled) }
        updateManager.schedulePeriodicAutoUpdate()
    }

    fun setAutoCheckAppUpdates(enabled: Boolean) {
        prefs.autoCheckAppUpdates = enabled
        _state.update { it.copy(autoCheckAppUpdates = enabled) }
    }

    fun setUpdateWifiOnly(wifiOnly: Boolean) {
        prefs.updateWifiOnly = wifiOnly
        _state.update { it.copy(updateWifiOnly = wifiOnly) }
        updateManager.schedulePeriodicAutoUpdate()
    }

    /**
     * Checks both the yt-dlp download engine and Crunch app GitHub releases concurrently.
     */
    fun checkForUpdates() {
        if (_state.value.isCheckingUpdate || _state.value.isCheckingAppUpdate) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingUpdate = true,
                    isCheckingAppUpdate = true,
                    updateStatusMessage = null,
                    isUpdateSuccess = null,
                    appUpdateStatusMessage = null,
                    isAppUpdateSuccess = null,
                )
            }

            // Launch both checks in parallel
            val engineDeferred = async(Dispatchers.IO) {
                updateManager.checkAndUpdateEngine(manual = true)
            }
            val appDeferred = async(Dispatchers.IO) {
                appUpdateManager.checkForAppUpdate()
            }

            val engineResult = engineDeferred.await()
            val appResult = appDeferred.await()

            var newAvailableUpdate: AppUpdateInfo? = null
            var appMsg: String? = null
            var appSuccess: Boolean? = null
            var openDialog = false

            when (appResult) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    newAvailableUpdate = appResult.updateInfo
                    appMsg = "New Crunch release available: ${appResult.updateInfo.versionTag} (${appResult.updateInfo.formattedSize})"
                    appSuccess = true
                    openDialog = true
                }
                is AppUpdateCheckResult.UpToDate -> {
                    appMsg = "Crunch app is up to date (${appResult.currentVersion})"
                    appSuccess = true
                }
                is AppUpdateCheckResult.NoReleasesFound -> {
                    appMsg = appResult.message
                    appSuccess = true
                }
                is AppUpdateCheckResult.Error -> {
                    appMsg = "App update check failed: ${appResult.message}"
                    appSuccess = false
                }
            }

            val combinedMsg = buildString {
                append(engineResult.message)
                append("\n• ").append(appMsg)
            }

            _state.update {
                it.copy(
                    isCheckingUpdate = false,
                    isCheckingAppUpdate = false,
                    ytdlpVersion = engineResult.currentVersion,
                    lastUpdateCheckTime = System.currentTimeMillis(),
                    updateStatusMessage = combinedMsg,
                    isUpdateSuccess = engineResult.isSuccess && (appSuccess != false),
                    availableAppUpdate = newAvailableUpdate,
                    showAppUpdateDialog = openDialog,
                    appUpdateStatusMessage = appMsg,
                    isAppUpdateSuccess = appSuccess,
                )
            }
        }
    }

    /**
     * Checks specifically for GitHub App updates.
     */
    fun checkForAppUpdates(manual: Boolean = true) {
        if (_state.value.isCheckingAppUpdate) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingAppUpdate = true,
                    appUpdateStatusMessage = null,
                    isAppUpdateSuccess = null,
                )
            }

            val result = withContext(Dispatchers.IO) {
                appUpdateManager.checkForAppUpdate()
            }

            when (result) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    _state.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            availableAppUpdate = result.updateInfo,
                            showAppUpdateDialog = manual,
                            appUpdateStatusMessage = "New Crunch release available: ${result.updateInfo.versionTag}",
                            isAppUpdateSuccess = true,
                        )
                    }
                }
                is AppUpdateCheckResult.UpToDate -> {
                    _state.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            availableAppUpdate = null,
                            appUpdateStatusMessage = "Crunch app is up to date (v${result.currentVersion})",
                            isAppUpdateSuccess = true,
                        )
                    }
                }
                is AppUpdateCheckResult.NoReleasesFound -> {
                    _state.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            availableAppUpdate = null,
                            appUpdateStatusMessage = result.message,
                            isAppUpdateSuccess = true,
                        )
                    }
                }
                is AppUpdateCheckResult.Error -> {
                    _state.update {
                        it.copy(
                            isCheckingAppUpdate = false,
                            appUpdateStatusMessage = "Check failed: ${result.message}",
                            isAppUpdateSuccess = false,
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts downloading the APK for the available update.
     */
    fun downloadAndInstallAppUpdate() {
        val updateInfo = _state.value.availableAppUpdate ?: return
        if (_state.value.isDownloadingAppUpdate) return

        // If already downloaded and valid, jump straight to install
        val existingFile = _state.value.downloadedApkFile
        if (existingFile != null && existingFile.exists() && existingFile.length() > 0) {
            installDownloadedApk()
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isDownloadingAppUpdate = true,
                    appUpdateDownloadProgress = 0f,
                    appUpdateDownloadBytes = 0L,
                    appUpdateDownloadTotalBytes = updateInfo.apkAsset.size,
                    appUpdateStatusMessage = "Downloading ${updateInfo.versionTag}…",
                )
            }

            try {
                val apkFile = appUpdateManager.downloadApk(updateInfo.apkAsset) { progress ->
                    _state.update {
                        it.copy(
                            appUpdateDownloadProgress = progress.progress,
                            appUpdateDownloadBytes = progress.bytesDownloaded,
                            appUpdateDownloadTotalBytes = progress.totalBytes,
                        )
                    }
                }

                _state.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        downloadedApkFile = apkFile,
                        appUpdateDownloadProgress = 1f,
                        appUpdateStatusMessage = "Download complete. Ready to install.",
                    )
                }

                // Automatically attempt install
                installDownloadedApk()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDownloadingAppUpdate = false,
                        appUpdateStatusMessage = "Download failed: ${e.message}",
                        isAppUpdateSuccess = false,
                    )
                }
            }
        }
    }

    /**
     * Launches the Android package installer for the downloaded APK.
     */
    fun installDownloadedApk() {
        val file = _state.value.downloadedApkFile ?: return
        when (val result = appUpdateManager.installApk(file)) {
            is InstallResult.Success -> {
                _state.update { it.copy(showAppUpdateDialog = false) }
            }
            is InstallResult.PermissionRequired -> {
                _state.update { it.copy(showPermissionDialog = true) }
            }
            is InstallResult.Error -> {
                _state.update {
                    it.copy(
                        appUpdateStatusMessage = result.message,
                        isAppUpdateSuccess = false
                    )
                }
            }
        }
    }

    fun openInstallPermissionSettings() {
        appUpdateManager.openInstallPermissionSettings()
        _state.update { it.copy(showPermissionDialog = false) }
    }

    fun dismissPermissionDialog() {
        _state.update { it.copy(showPermissionDialog = false) }
    }

    fun showAppUpdateDialog(show: Boolean) {
        _state.update { it.copy(showAppUpdateDialog = show) }
    }

    fun dismissUpdateMessage() {
        _state.update {
            it.copy(
                updateStatusMessage = null,
                isUpdateSuccess = null,
                appUpdateStatusMessage = null,
                isAppUpdateSuccess = null
            )
        }
    }

    fun setFastDownloader(enabled: Boolean) {
        prefs.useFastDownloader = enabled
        _state.update { it.copy(useFastDownloader = enabled) }
    }

    fun setDownloadThreads(threads: Int) {
        prefs.downloadThreads = threads
        _state.update { it.copy(downloadThreads = threads) }
    }

    fun setEmbedThumbnail(embed: Boolean) {
        prefs.embedThumbnail = embed
        _state.update { it.copy(embedThumbnail = embed) }
    }

    fun setEmbedMetadata(embed: Boolean) {
        prefs.embedMetadata = embed
        _state.update { it.copy(embedMetadata = embed) }
    }

    fun setDefaultAudioFormat(fmt: String) {
        prefs.defaultAudioFormat = fmt
        _state.update { it.copy(defaultAudioFormat = fmt) }
    }

    fun setDefaultVideoContainer(container: String) {
        prefs.defaultVideoContainer = container
        _state.update { it.copy(defaultVideoContainer = container) }
    }

    fun addTemplate(name: String, args: String) {
        if (name.isBlank() || args.isBlank()) return
        val newTpl = CommandTemplate(id = UUID.randomUUID().toString(), name = name, extraArgs = args)
        val updated = _state.value.commandTemplates + newTpl
        prefs.commandTemplates = updated
        _state.update { it.copy(commandTemplates = updated) }
    }

    fun deleteTemplate(tpl: CommandTemplate) {
        val updated = _state.value.commandTemplates.filter { it.id != tpl.id }
        prefs.commandTemplates = updated
        _state.update { it.copy(commandTemplates = updated) }
    }
}
