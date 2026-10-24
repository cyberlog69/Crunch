package com.kappi.crunch.ui.downloads

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kappi.crunch.R
import com.kappi.crunch.data.DownloadItem
import com.kappi.crunch.data.DownloadStatus
import com.kappi.crunch.data.DownloadType
import com.kappi.crunch.theme.*
import com.kappi.crunch.util.FileLocation
import com.kappi.crunch.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val active = state.active
    val completed = state.completed

    if (active.isEmpty() && completed.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (active.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.active_downloads), count = active.size)
            }
            items(active, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onCancel = { viewModel.cancelDownload(item) },
                    onDelete = { deleteFile -> viewModel.deleteDownload(item, deleteFile) },
                    onRetry = { viewModel.retryDownload(item) },
                )
            }
        }

        if (completed.isNotEmpty()) {
            item {
                if (active.isNotEmpty()) Spacer(Modifier.height(8.dp))
                SectionHeader(title = stringResource(R.string.completed_downloads), count = completed.size)
            }
            items(completed, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onCancel = { viewModel.cancelDownload(item) },
                    onDelete = { deleteFile -> viewModel.deleteDownload(item, deleteFile) },
                    onRetry = { viewModel.retryDownload(item) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Badge { Text("$count") }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onDelete: (deleteFile: Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            hasFile = item.filePath.isNotEmpty() &&
                (item.fileSize > 0 || FileLocation.exists(context, item.filePath)),
            onDismiss = { showDeleteDialog = false },
            onConfirm = { deleteFile ->
                showDeleteDialog = false
                onDelete(deleteFile)
            },
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // ── Top row: thumbnail + title ────────────────────────────
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Thumbnail
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (item.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = if (item.downloadType == DownloadType.AUDIO)
                                Icons.Filled.MusicNote else Icons.Filled.Videocam,
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title & metadata
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.uploader.isNotEmpty()) {
                        Text(
                            text = item.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(item.status)
                        if (item.formatLabel.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.formatLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Type icon
                Icon(
                    imageVector = if (item.downloadType == DownloadType.AUDIO)
                        Icons.Outlined.Audiotrack else Icons.Outlined.VideoFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ── Progress bar (active downloads) ───────────────────────
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.PROCESSING) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = StatusActive,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.speed.isNotEmpty()) {
                            Text(
                                text = "${item.speed} · ETA ${item.eta}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Fetching indicator ────────────────────────────────────
            if (item.status == DownloadStatus.FETCHING) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                )
            }

            // ── Error message ─────────────────────────────────────────
            if (item.status == DownloadStatus.FAILED && item.errorMessage.isNotEmpty()) {
                Text(
                    text = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    maxLines = 2,
                )
            }

            // ── Completed info ────────────────────────────────────────
            if (item.status == DownloadStatus.COMPLETED && item.fileSize > 0) {
                Text(
                    text = formatSize(item.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 4.dp),
                )
            }

            // ── Action row ────────────────────────────────────────────
            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                when (item.status) {
                    DownloadStatus.QUEUED,
                    DownloadStatus.DOWNLOADING,
                    DownloadStatus.FETCHING,
                    DownloadStatus.PROCESSING -> {
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Filled.Cancel, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
                    DownloadStatus.FAILED,
                    DownloadStatus.CANCELLED -> {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_retry))
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_delete))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        // Open file
                        val fileExists = item.filePath.isNotEmpty() &&
                            (item.fileSize > 0 || FileLocation.exists(context, item.filePath))
                        if (fileExists) {
                            val mimeType = FormatUtils.mimeTypeFor(item.filePath)
                            TextButton(onClick = {
                                val uri = FileLocation.toUri(context, item.filePath) ?: return@TextButton
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, mimeType)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(intent) }
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_open))
                            }
                            // Share
                            TextButton(onClick = {
                                val uri = FileLocation.toUri(context, item.filePath) ?: return@TextButton
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Share"))
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.btn_share))
                            }
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_delete))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: DownloadStatus) {
    val (color, label) = when (status) {
        DownloadStatus.QUEUED -> StatusQueued to "Queued"
        DownloadStatus.FETCHING -> StatusActive to "Fetching"
        DownloadStatus.DOWNLOADING -> StatusActive to "Downloading"
        DownloadStatus.PROCESSING -> StatusActive to "Processing"
        DownloadStatus.COMPLETED -> StatusCompleted to "Completed"
        DownloadStatus.FAILED -> StatusFailed to "Failed"
        DownloadStatus.CANCELLED -> StatusCancelled to "Cancelled"
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    hasFile: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (deleteFile: Boolean) -> Unit,
) {
    var deleteFile by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_confirm_body))
                if (hasFile) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteFile, onCheckedChange = { deleteFile = it })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete_file_only))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFile) }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.no_downloads),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Downloaded videos and audio will be saved and organized here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }
}
