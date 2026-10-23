package com.kappi.crunch.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kappi.crunch.data.*
import com.kappi.crunch.download.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CrunchDatabase.getInstance(application)
    private val dao = db.downloadDao()

    val activeDownloads: StateFlow<List<DownloadItem>> =
        dao.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads: StateFlow<List<DownloadItem>> =
        dao.observeCompleted()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun cancelDownload(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadManager.cancel(getApplication(), item)
            dao.updateStatus(item.id, DownloadStatus.CANCELLED)
        }
    }

    fun deleteDownload(item: DownloadItem, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile && item.filePath.isNotEmpty()) {
                File(item.filePath).delete()
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
}
