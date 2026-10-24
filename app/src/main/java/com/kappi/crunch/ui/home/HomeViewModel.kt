package com.kappi.crunch.ui.home

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kappi.crunch.data.*
import com.kappi.crunch.download.DownloadManager
import com.kappi.crunch.download.YtDlpEngine
import com.kappi.crunch.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object FetchingInfo : HomeUiState
    data class InfoReady(val info: VideoInfo) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

data class DownloadOptions(
    val type: DownloadType = DownloadType.VIDEO,
    val selectedVideoFormat: FormatOption? = null,
    val selectedAudioTrack: FormatOption? = null,
    val selectedFormat: FormatOption? = null,
    val audioFormat: String = "mp3",
    val subtitleLang: String = "",
    val embedSubtitles: Boolean = false,
    val useExternalDownloader: Boolean = false,
    val selectedTemplate: CommandTemplate? = null,
)

class HomeViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val engine = YtDlpEngine(application)
    private val prefs = UserPreferences(application)
    private val db = CrunchDatabase.getInstance(application)
    private val dao = db.downloadDao()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Guards against double taps enqueuing the same video/playlist twice.
    private val isEnqueuing = AtomicBoolean(false)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _downloadOptions = MutableStateFlow(DownloadOptions(useExternalDownloader = prefs.useFastDownloader))
    val downloadOptions: StateFlow<DownloadOptions> = _downloadOptions.asStateFlow()

    // Expose templates for the UI
    val templates: List<CommandTemplate> get() = prefs.commandTemplates

    init {
        // Check if launched via share intent — handled in Activity and passed here if needed
    }

    fun onUrlChange(url: String) {
        _urlInput.value = url
        // Reset state if user clears the field
        if (url.isEmpty()) _uiState.value = HomeUiState.Idle
    }

    fun onFetchClicked() {
        val url = _urlInput.value.trim()
        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            _uiState.value = HomeUiState.Error("Please enter a valid URL starting with http:// or https://")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            // yt-dlp is bundled as a native library; if it cannot initialize the
            // app is unusable, so surface that instead of pretending to fetch it.
            if (!engine.isBinaryAvailable) {
                _uiState.value = HomeUiState.Error(
                    "The yt-dlp engine could not be initialized on this device. " +
                        "Check that the installed APK matches your device ABI."
                )
                return@launch
            }

            _uiState.value = HomeUiState.FetchingInfo
            try {
                val info = engine.fetchInfo(url)
                _uiState.value = HomeUiState.InfoReady(info)

                // Select smart defaults:
                // For video: pick the top resolution actual format if available, or bestvideo
                val defaultVideoFormat = info.videoFormats.getOrNull(1)
                    ?: info.videoFormats.firstOrNull()
                    ?: info.formats.filter { !it.isAudioOnly }.maxByOrNull { it.tbr }

                // For audio track: pick Best Audio (Auto)
                val defaultAudioTrack = info.audioFormats.firstOrNull()

                _downloadOptions.value = _downloadOptions.value.copy(
                    selectedVideoFormat = defaultVideoFormat,
                    selectedAudioTrack = defaultAudioTrack,
                    selectedFormat = defaultVideoFormat,
                    audioFormat = prefs.defaultAudioFormat,
                    useExternalDownloader = prefs.useFastDownloader,
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching info for $url", e)
                _uiState.value = HomeUiState.Error(
                    "Could not fetch info: ${FormatUtils.conciseError(e.message)}"
                )
            }
        }
    }

    fun onTypeSelected(type: DownloadType) {
        _downloadOptions.value = _downloadOptions.value.copy(type = type)
    }

    fun onVideoFormatSelected(format: FormatOption) {
        _downloadOptions.value = _downloadOptions.value.copy(
            selectedVideoFormat = format,
            selectedFormat = format,
        )
    }

    fun onAudioTrackSelected(track: FormatOption) {
        _downloadOptions.value = _downloadOptions.value.copy(
            selectedAudioTrack = track,
        )
    }

    fun onFormatSelected(format: FormatOption) {
        onVideoFormatSelected(format)
    }

    fun onAudioFormatSelected(fmt: String) {
        _downloadOptions.value = _downloadOptions.value.copy(audioFormat = fmt)
    }

    fun onSubtitleLangSelected(lang: String) {
        _downloadOptions.value = _downloadOptions.value.copy(subtitleLang = lang)
    }

    fun onEmbedSubtitlesChanged(embed: Boolean) {
        _downloadOptions.value = _downloadOptions.value.copy(embedSubtitles = embed)
    }

    fun onExternalDownloaderChanged(use: Boolean) {
        _downloadOptions.value = _downloadOptions.value.copy(useExternalDownloader = use)
    }

    fun onTemplateSelected(template: CommandTemplate?) {
        _downloadOptions.value = _downloadOptions.value.copy(selectedTemplate = template)
    }

    fun onDownloadClicked() {
        val infoState = _uiState.value as? HomeUiState.InfoReady ?: return
        if (!isEnqueuing.compareAndSet(false, true)) return
        val info = infoState.info
        val opts = _downloadOptions.value

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (formatId, formatLabel) = if (opts.type == DownloadType.VIDEO) {
                    val video = opts.selectedVideoFormat ?: opts.selectedFormat
                    val audio = opts.selectedAudioTrack
                    val vId = video?.id?.ifEmpty { "bestvideo" } ?: "bestvideo"
                    val aId = audio?.id?.ifEmpty { "bestaudio" } ?: "bestaudio"

                    val finalFormatId = when {
                        vId == "bestvideo" && aId == "bestaudio" -> "bestvideo+bestaudio/best"
                        vId == "bestvideo" -> "bestvideo+$aId/best"
                        aId == "bestaudio" -> "$vId+bestaudio/best"
                        else -> "$vId+$aId"
                    }

                    val vLabel = video?.qualityLabel?.ifEmpty { video.label } ?: "Best Video"
                    val aLabel = audio?.qualityLabel?.ifEmpty { audio.label } ?: "Best Audio"
                    val containerExt = prefs.defaultVideoContainer.uppercase()
                    val finalLabel = "$vLabel • $aLabel • $containerExt"

                    Pair(finalFormatId, finalLabel)
                } else {
                    val audio = opts.selectedAudioTrack
                    val aId = audio?.id?.ifEmpty { "bestaudio" } ?: "bestaudio"
                    val finalFormatId = if (aId == "bestaudio") "bestaudio/best" else aId
                    val aLabel = audio?.qualityLabel?.ifEmpty { audio.label } ?: "Best Audio"
                    Pair(finalFormatId, "$aLabel (${opts.audioFormat.uppercase()})")
                }

                if (info.isPlaylist) {
                    val playlistId = UUID.randomUUID().toString()
                    val entries = info.playlistEntries.ifEmpty {
                        listOf(
                            PlaylistEntry(
                                url = info.url,
                                title = info.title,
                                uploader = info.uploader,
                                duration = info.duration,
                                thumbnailUrl = info.thumbnailUrl,
                            )
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        enqueueItem(
                            url = entry.url,
                            title = entry.title,
                            uploader = entry.uploader.ifEmpty { info.uploader },
                            thumbnailUrl = entry.thumbnailUrl.ifEmpty { info.thumbnailUrl },
                            duration = entry.duration,
                            opts = opts,
                            formatId = formatId,
                            formatLabel = formatLabel,
                            playlistId = playlistId,
                            playlistIndex = index,
                        )
                    }
                } else {
                    enqueueItem(
                        url = info.url,
                        title = info.title,
                        uploader = info.uploader,
                        thumbnailUrl = info.thumbnailUrl,
                        duration = info.duration,
                        opts = opts,
                        formatId = formatId,
                        formatLabel = formatLabel,
                    )
                }
                // Reset to idle after queuing
                _uiState.value = HomeUiState.Idle
                _urlInput.value = ""
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error enqueueing download", e)
                _uiState.value = HomeUiState.Error("Failed to start download: ${e.localizedMessage ?: "Unknown error"}")
            } finally {
                isEnqueuing.set(false)
            }
        }
    }

    private suspend fun enqueueItem(
        url: String,
        title: String,
        uploader: String,
        thumbnailUrl: String,
        duration: Long,
        opts: DownloadOptions,
        formatId: String,
        formatLabel: String,
        playlistId: String = "",
        playlistIndex: Int = 0,
    ) {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title,
            uploader = uploader,
            thumbnailUrl = thumbnailUrl,
            duration = duration,
            downloadType = opts.type,
            formatId = formatId,
            formatLabel = formatLabel,
            audioFormat = opts.audioFormat,
            subtitleLang = opts.subtitleLang,
            embedSubtitles = opts.embedSubtitles,
            status = DownloadStatus.QUEUED,
            playlistId = playlistId,
            playlistIndex = playlistIndex,
            useExternalDownloader = opts.useExternalDownloader,
            customTemplate = opts.selectedTemplate?.extraArgs ?: "",
        )
        dao.insert(item)
        val updated = DownloadManager.enqueue(getApplication(), item)
        dao.update(updated)
    }

    fun onClearError() {
        _uiState.value = HomeUiState.Idle
    }

    fun prefillUrl(url: String) {
        _urlInput.value = url
    }
}
