package com.kappi.crunch.ui.downloads

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kappi.crunch.data.*
import com.kappi.crunch.download.DownloadManager
import com.kappi.crunch.util.FileLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CrunchDatabase.getInstance(application)
    private val dao = db.downloadDao()

    private val _syncState = MutableStateFlow(
        SyncUiState(hasFolderAccess = DownloadHistorySync.hasPersistedFolderAccess(application))
    )
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val result = DownloadHistorySync.sync(application, dao, removeMissing = false)
            _syncState.update {
                it.copy(
                    hasFolderAccess = result.hasFolderAccess,
                    isSyncing = false,
                )
            }
        }
    }

    /**
     * Both lists come from one query so a status change can never be observed as
     * "in active" and "in completed" at the same time, which would give the
     * LazyColumn duplicate keys.
     */
    val uiState: StateFlow<DownloadsUiState> =
        dao.observeAll()
            .map { items ->
                DownloadsUiState(
                    active = items.filter { it.status in ACTIVE_STATUSES }
                        .sortedBy { it.createdAt },
                    completed = items.filter { it.status in COMPLETED_STATUSES }
                        .sortedByDescending { it.completedAt },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState())

    fun cancelDownload(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadManager.cancel(getApplication(), item)
            dao.updateStatus(item.id, DownloadStatus.CANCELLED)
        }
    }

    fun deleteDownload(item: DownloadItem, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile && item.filePath.isNotEmpty()) {
                FileLocation.delete(getApplication(), item.filePath)
            }
            dao.deleteById(item.id)
        }
    }

    fun retryDownload(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val reset = item.copy(
                status = DownloadStatus.QUEUED,
                progress = 0f,
                speed = "",
                eta = "",
                errorMessage = "",
            )
            dao.update(reset)
            val updated = DownloadManager.enqueue(getApplication(), reset)
            dao.update(updated)
        }
    }

    fun rescanDownloadsFolder(manual: Boolean = true) {
        if (_syncState.value.isSyncing) return
        viewModelScope.launch(Dispatchers.IO) {
            _syncState.update { it.copy(isSyncing = true, feedbackMessage = null) }
            val result = DownloadHistorySync.sync(
                context = getApplication(),
                dao = dao,
                removeMissing = manual,
            )
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    hasFolderAccess = result.hasFolderAccess,
                    feedbackMessage = if (manual) result.statusMessage() else null,
                )
            }
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = DownloadHistorySync.saveFolderAccess(getApplication(), treeUri)
            if (saved) {
                _syncState.update { it.copy(hasFolderAccess = true) }
                rescanDownloadsFolder(manual = true)
            } else {
                _syncState.update {
                    it.copy(feedbackMessage = "Could not persist folder access. Please select Downloads/Crunch.")
                }
            }
        }
    }

    fun dismissSyncFeedback() {
        _syncState.update { it.copy(feedbackMessage = null) }
    }

    private companion object {
        val ACTIVE_STATUSES = setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.FETCHING,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.PROCESSING,
        )
        val COMPLETED_STATUSES = setOf(
            DownloadStatus.COMPLETED,
            DownloadStatus.FAILED,
            DownloadStatus.CANCELLED,
        )
    }
}

data class DownloadsUiState(
    val active: List<DownloadItem> = emptyList(),
    val completed: List<DownloadItem> = emptyList(),
)

data class SyncUiState(
    val isSyncing: Boolean = false,
    val hasFolderAccess: Boolean = false,
    val feedbackMessage: String? = null,
)
