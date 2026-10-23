package com.kappi.crunch.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kappi.crunch.data.CommandTemplate
import com.kappi.crunch.data.UserPreferences
import com.kappi.crunch.updater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class SettingsState(
    val outputFolder: String = "",
    val useFastDownloader: Boolean = true,
    val downloadThreads: Int = 16,
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
    val maxConcurrent: Int = 2,
    val defaultAudioFormat: String = "mp3",
    val defaultVideoContainer: String = "mkv",
    val commandTemplates: List<CommandTemplate> = emptyList(),
    // Engine & Updates
    val autoUpdateEngine: Boolean = true,
    val updateWifiOnly: Boolean = true,
    val ytdlpVersion: String = "2025.11.12",
    val ffmpegVersion: String = "7.1.1 (Native)",
    val aria2cVersion: String = "1.37.0 (Native)",
    val appVersion: String = "1.0",
    val lastUpdateCheckTime: Long = 0L,
    val isCheckingUpdate: Boolean = false,
    val updateStatusMessage: String? = null,
    val isUpdateSuccess: Boolean? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferences(application)
    private val updateManager = UpdateManager(application)

    private val _state = MutableStateFlow(
        SettingsState(
            outputFolder = prefs.outputFolder,
            useFastDownloader = prefs.useFastDownloader,
            downloadThreads = prefs.downloadThreads,
            embedThumbnail = prefs.embedThumbnail,
            embedMetadata = prefs.embedMetadata,
            maxConcurrent = prefs.maxConcurrentDownloads,
            defaultAudioFormat = prefs.defaultAudioFormat,
            defaultVideoContainer = prefs.defaultVideoContainer,
            commandTemplates = prefs.commandTemplates,
            autoUpdateEngine = prefs.autoUpdateEngine,
            updateWifiOnly = prefs.updateWifiOnly,
            lastUpdateCheckTime = prefs.lastUpdateCheck,
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadComponentVersions()
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

    fun setUpdateWifiOnly(wifiOnly: Boolean) {
        prefs.updateWifiOnly = wifiOnly
        _state.update { it.copy(updateWifiOnly = wifiOnly) }
        updateManager.schedulePeriodicAutoUpdate()
    }

    fun checkForUpdates() {
        if (_state.value.isCheckingUpdate) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateStatusMessage = null,
                    isUpdateSuccess = null,
                )
            }

            val result = withContext(Dispatchers.IO) {
                updateManager.checkAndUpdateEngine(manual = true)
            }

            _state.update {
                it.copy(
                    isCheckingUpdate = false,
                    ytdlpVersion = result.currentVersion,
                    lastUpdateCheckTime = System.currentTimeMillis(),
                    updateStatusMessage = result.message,
                    isUpdateSuccess = result.isSuccess,
                )
            }
        }
    }

    fun dismissUpdateMessage() {
        _state.update { it.copy(updateStatusMessage = null, isUpdateSuccess = null) }
    }

    fun setOutputFolder(path: String) {
        prefs.outputFolder = path
        _state.update { it.copy(outputFolder = path) }
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

    fun setMaxConcurrent(n: Int) {
        prefs.maxConcurrentDownloads = n
        _state.update { it.copy(maxConcurrent = n) }
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
