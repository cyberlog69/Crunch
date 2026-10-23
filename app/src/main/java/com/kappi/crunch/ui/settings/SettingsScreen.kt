package com.kappi.crunch.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kappi.crunch.R
import com.kappi.crunch.data.CommandTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddTemplateSheet by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.setOutputFolder(it.path ?: it.toString()) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(20.dp))

        // ── Download folder ───────────────────────────────────────────
        SettingsSection(title = "Storage") {
            SettingsItem(
                title = stringResource(R.string.settings_output_folder),
                subtitle = state.outputFolder.ifEmpty { "Default (Downloads/Crunch)" },
                icon = Icons.Outlined.Folder,
                onClick = {
                    folderPickerLauncher.launch(null)
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Audio defaults ────────────────────────────────────────────
        SettingsSection(title = "Audio") {
            SwitchSettingsItem(
                title = stringResource(R.string.settings_thumbnail_embed),
                subtitle = "Add cover art to audio files",
                icon = Icons.Outlined.Image,
                checked = state.embedThumbnail,
                onCheckedChange = viewModel::setEmbedThumbnail,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            SwitchSettingsItem(
                title = stringResource(R.string.settings_metadata_embed),
                subtitle = "Embed title, artist, etc.",
                icon = Icons.Outlined.Tag,
                checked = state.embedMetadata,
                onCheckedChange = viewModel::setEmbedMetadata,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            // Default audio format
            val audioFormats = listOf("mp3", "m4a", "opus", "flac", "wav")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.settings_default_audio_format),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    audioFormats.forEach { fmt ->
                        val isSelected = state.defaultAudioFormat == fmt
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setDefaultAudioFormat(fmt) },
                            label = { Text(fmt.uppercase(), maxLines = 1) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Video defaults ────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_video_title)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.settings_video_container),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_video_container_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                val videoContainers = listOf("mkv", "mp4")
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    videoContainers.forEachIndexed { index, fmt ->
                        val isSelected = state.defaultVideoContainer.equals(fmt, ignoreCase = true)
                        SegmentedButton(
                            selected = isSelected,
                            onClick = { viewModel.setDefaultVideoContainer(fmt) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = videoContainers.size),
                            label = {
                                Text(
                                    if (fmt == "mkv") "MKV (Recommended)" else "MP4",
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Performance ───────────────────────────────────────────────
        SettingsSection(title = "Performance") {
            SwitchSettingsItem(
                title = stringResource(R.string.settings_fast_downloader),
                subtitle = stringResource(R.string.settings_fast_downloader_desc),
                icon = Icons.Outlined.Speed,
                checked = state.useFastDownloader,
                onCheckedChange = viewModel::setFastDownloader,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AltRoute,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Parallel Connections / Chunk Splitting",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Splits video files into parallel segments for up to 10x faster downloads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val threadOptions = listOf(4 to "4x", 8 to "8x", 16 to "16x", 32 to "32x")
                    threadOptions.forEachIndexed { index, (threads, label) ->
                        SegmentedButton(
                            selected = state.downloadThreads == threads,
                            onClick = { viewModel.setDownloadThreads(threads) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = threadOptions.size),
                            label = { Text(label, maxLines = 1) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (state.downloadThreads) {
                        4 -> "4 connections (Standard)"
                        8 -> "8 connections (Balanced)"
                        16 -> "16 connections (Fast • Recommended)"
                        32 -> "32 connections (Maximum turbo)"
                        else -> "${state.downloadThreads} connections"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            HorizontalDivider(Modifier.padding(start = 56.dp))
            // Max concurrent downloads
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Layers, contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_max_concurrent),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${state.maxConcurrent} simultaneous tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { if (state.maxConcurrent > 1) viewModel.setMaxConcurrent(state.maxConcurrent - 1) },
                            enabled = state.maxConcurrent > 1,
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Decrease", Modifier.size(16.dp))
                        }
                        Text(
                            "${state.maxConcurrent}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 10.dp),
                        )
                        FilledTonalIconButton(
                            onClick = { if (state.maxConcurrent < 5) viewModel.setMaxConcurrent(state.maxConcurrent + 1) },
                            enabled = state.maxConcurrent < 5,
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Command templates ─────────────────────────────────────────
        SettingsSection(title = "Advanced: Command templates") {
            if (state.commandTemplates.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No custom templates yet. Tap + to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                state.commandTemplates.forEachIndexed { i, tpl ->
                    TemplateItem(
                        template = tpl,
                        onDelete = { viewModel.deleteTemplate(tpl) },
                    )
                    if (i < state.commandTemplates.lastIndex) {
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
            HorizontalDivider()
            TextButton(
                onClick = { showAddTemplateSheet = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add_template))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Engine & Updates ──────────────────────────────────────────
        SettingsSection(title = "Engine & Updates") {
            // yt-dlp item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("yt-dlp Engine", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "OTA Updatable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                supportingContent = {
                    Text(
                        "v${state.ytdlpVersion} • Core extraction & stream analyzer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))

            // FFmpeg item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FFmpeg", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Native NDK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                supportingContent = {
                    Text(
                        "${state.ffmpegVersion} • Stream muxing, remuxing & audio encoding",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Transform,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))

            // aria2c item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("aria2c", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Native NDK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                supportingContent = {
                    Text(
                        "${state.aria2cVersion} • Multi-segmented parallel socket downloader",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp))

            // Auto-update toggle
            SwitchSettingsItem(
                title = "Automatic engine updates",
                subtitle = "Silently keep yt-dlp updated in background to prevent extraction breakages",
                icon = Icons.Outlined.Autorenew,
                checked = state.autoUpdateEngine,
                onCheckedChange = viewModel::setAutoUpdateEngine,
            )

            if (state.autoUpdateEngine) {
                HorizontalDivider(Modifier.padding(start = 56.dp))
                SwitchSettingsItem(
                    title = "Update on Wi-Fi only",
                    subtitle = "Conserve cellular mobile data for component downloads",
                    icon = Icons.Outlined.Wifi,
                    checked = state.updateWifiOnly,
                    onCheckedChange = viewModel::setUpdateWifiOnly,
                )
            }

            HorizontalDivider()

            // Update status alert banner if present
            if (state.updateStatusMessage != null) {
                val isSuccess = state.isUpdateSuccess == true
                Surface(
                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            state.updateStatusMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissUpdateMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Check for updates action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    val relativeTime = if (state.lastUpdateCheckTime > 0L) {
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            state.lastUpdateCheckTime,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } else {
                        "Never"
                    }
                    Text(
                        "Last checked: $relativeTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { viewModel.checkForUpdates() },
                    enabled = !state.isCheckingUpdate,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Updating…")
                    } else {
                        Icon(
                            Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Check for updates")
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── About Card ──────────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_crunch_logo),
                        contentDescription = "Crunch Logo",
                        modifier = Modifier.size(50.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Crunch",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "High-Speed Media & Stream Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version 1.0 • Powered by yt-dlp, FFmpeg & aria2c",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }

    // Add template bottom sheet
    if (showAddTemplateSheet) {
        AddTemplateSheet(
            onDismiss = { showAddTemplateSheet = false },
            onAdd = { name, args ->
                viewModel.addTemplate(name, args)
                showAddTemplateSheet = false
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Filled.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchSettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun TemplateItem(
    template: CommandTemplate,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(template.name) },
        supportingContent = {
            Text(
                template.extraArgs,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        leadingContent = {
            Icon(Icons.Outlined.Code, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete template",
                    tint = MaterialTheme.colorScheme.error)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTemplateSheet(
    onDismiss: () -> Unit,
    onAdd: (name: String, args: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var args by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("New Command Template", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_template_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                label = { Text(stringResource(R.string.settings_template_args_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("e.g. --remux-video mkv --no-playlist") },
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAdd(name.trim(), args.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && args.isNotBlank(),
            ) {
                Text("Add Template")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
