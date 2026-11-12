package com.kappi.crunch.ui.settings

import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kappi.crunch.R
import com.kappi.crunch.data.CommandTemplate
import com.kappi.crunch.data.DownloadHistorySync
import com.kappi.crunch.updater.AppUpdateInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddTemplateSheet by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.onFolderSelected(treeUri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Preferences & Engine Configuration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "v${state.appVersion}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Storage Section ───────────────────────────────────────────
        SettingsSection(
            title = "Storage & Downloads",
            icon = Icons.Outlined.Folder
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.settings_output_folder),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                supportingContent = {
                    Text(
                        if (state.hasFolderAccess)
                            "Downloads/Crunch • Full folder access linked"
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            "Downloads/Crunch • Link folder to scan files across reinstalls"
                        else
                            "Downloads/Crunch • Preserved safely across updates",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.hasFolderAccess) Icons.Outlined.FolderSpecial else Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                trailingContent = {
                    FilledTonalButton(
                        onClick = { viewModel.rescanDownloads() },
                        enabled = !state.isSyncingDownloads,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.isSyncingDownloads) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isSyncingDownloads) "Syncing" else "Sync", style = MaterialTheme.typography.labelMedium)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (state.hasFolderAccess) "Folder access granted" else "Scoped Storage access",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.hasFolderAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(DownloadHistorySync.getInitialFolderUri()) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.hasFolderAccess) "Relink Folder" else "Link Downloads/Crunch",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (state.syncStatusMessage != null) {
                val isSuccess = state.isSyncSuccess != false
                Surface(
                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.syncStatusMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissSyncMessage() },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Audio defaults ────────────────────────────────────────────
        SettingsSection(
            title = "Audio Defaults",
            icon = Icons.Outlined.AudioFile
        ) {
            SwitchSettingsItem(
                title = stringResource(R.string.settings_thumbnail_embed),
                subtitle = "Embed album artwork into downloaded audio tracks",
                icon = Icons.Outlined.Image,
                checked = state.embedThumbnail,
                onCheckedChange = viewModel::setEmbedThumbnail,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            SwitchSettingsItem(
                title = stringResource(R.string.settings_metadata_embed),
                subtitle = "Embed title, artist, year, and ID3 tags",
                icon = Icons.Outlined.Tag,
                checked = state.embedMetadata,
                onCheckedChange = viewModel::setEmbedMetadata,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // Default audio format
            val audioFormats = listOf("mp3", "m4a", "opus", "flac", "wav")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.settings_default_audio_format),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        )
                        val desc = when (state.defaultAudioFormat.lowercase()) {
                            "opus" -> "OPUS: High fidelity & smallest file size (Recommended)"
                            "mp3" -> "MP3: Universal compatibility across all car stereos & players"
                            "m4a" -> "M4A: Native Apple AAC audio format"
                            "flac" -> "FLAC: Lossless studio master audio quality"
                            "wav" -> "WAV: Uncompressed raw PCM audio"
                            else -> "${state.defaultAudioFormat.uppercase()} format"
                        }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    audioFormats.forEach { fmt ->
                        val isSelected = state.defaultAudioFormat.equals(fmt, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setDefaultAudioFormat(fmt) },
                            label = { Text(fmt.uppercase(), maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
        SettingsSection(
            title = stringResource(R.string.settings_video_title),
            icon = Icons.Outlined.VideoFile
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.settings_video_container),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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
                                    fmt.uppercase(),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.defaultVideoContainer.equals("mkv", ignoreCase = true)) {
                            "MKV: Recommended for 4K/1080p, multiple audio tracks & soft subtitles"
                        } else {
                            "MP4: Best for universal compatibility with older TVs & QuickTime"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Performance ───────────────────────────────────────────────
        SettingsSection(
            title = "Performance & Acceleration",
            icon = Icons.Outlined.Speed
        ) {
            SwitchSettingsItem(
                title = stringResource(R.string.settings_fast_downloader),
                subtitle = stringResource(R.string.settings_fast_downloader_desc),
                icon = Icons.Outlined.Bolt,
                checked = state.useFastDownloader,
                onCheckedChange = viewModel::setFastDownloader,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.AltRoute,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Parallel Connections / Chunk Splitting",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when (state.downloadThreads) {
                            4 -> "4 connections • Standard (Battery friendly)"
                            8 -> "8 connections • Balanced (Mobile data friendly)"
                            16 -> "16 connections • Fast & turbo chunking (Recommended)"
                            32 -> "32 connections • Extreme maximum throughput"
                            else -> "${state.downloadThreads} connections"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Engine Components ──────────────────────────────────────────
        SettingsSection(
            title = "Engine & Components",
            icon = Icons.Outlined.Memory
        ) {
            // Crunch App item
            val appUpdate = state.availableAppUpdate
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Crunch Application", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.width(8.dp))
                        if (appUpdate != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "${appUpdate.versionTag} Available",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "GitHub Releases",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                supportingContent = {
                    Text(
                        if (appUpdate != null)
                            "Version ${appUpdate.versionTag} ready • ${appUpdate.formattedSize} • Tap Update"
                        else
                            "v${state.appVersion} • Release build with permanent keystore",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        if (appUpdate != null) Icons.Outlined.NewReleases else Icons.Outlined.AppShortcut,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingContent = {
                    if (appUpdate != null) {
                        FilledTonalButton(
                            onClick = { viewModel.showAppUpdateDialog(true) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Update", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // yt-dlp item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("yt-dlp Engine", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
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
                        modifier = Modifier.size(22.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // FFmpeg item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FFmpeg", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
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
                        modifier = Modifier.size(22.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            // aria2c item
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("aria2c", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
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
                        modifier = Modifier.size(22.dp)
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Updates & Automation ──────────────────────────────────────
        SettingsSection(
            title = "Updates & Automation",
            icon = Icons.Outlined.Autorenew
        ) {
            SwitchSettingsItem(
                title = "Automatic app updates",
                subtitle = "Notify when a new Crunch APK is released on GitHub",
                icon = Icons.Outlined.SystemUpdate,
                checked = state.autoCheckAppUpdates,
                onCheckedChange = viewModel::setAutoCheckAppUpdates,
            )
            HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            SwitchSettingsItem(
                title = "Automatic engine updates",
                subtitle = "Silently keep yt-dlp updated in background to prevent extraction breakages",
                icon = Icons.Outlined.Autorenew,
                checked = state.autoUpdateEngine,
                onCheckedChange = viewModel::setAutoUpdateEngine,
            )

            if (state.autoUpdateEngine || state.autoCheckAppUpdates) {
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SwitchSettingsItem(
                    title = "Update on Wi-Fi only",
                    subtitle = "Conserve cellular mobile data for component and APK downloads",
                    icon = Icons.Outlined.Wifi,
                    checked = state.updateWifiOnly,
                    onCheckedChange = viewModel::setUpdateWifiOnly,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Update status alert banner if present
            if (state.updateStatusMessage != null) {
                val isSuccess = state.isUpdateSuccess == true
                Surface(
                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
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
                val isChecking = state.isCheckingUpdate || state.isCheckingAppUpdate
                FilledTonalButton(
                    onClick = { viewModel.checkForUpdates() },
                    enabled = !isChecking,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Checking…")
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

        Spacer(Modifier.height(16.dp))

        // ── Command templates ─────────────────────────────────────────
        SettingsSection(
            title = "Advanced: Command Templates",
            icon = Icons.Outlined.Terminal
        ) {
            if (state.commandTemplates.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Code,
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
                        HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            TextButton(
                onClick = { showAddTemplateSheet = true },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_add_template))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── About Card ──────────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
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
                        .size(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_crunch_logo),
                        contentDescription = "Crunch Logo",
                        modifier = Modifier.size(52.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Crunch",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "High-Speed Media & Stream Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version ${state.appVersion} • Powered by yt-dlp, FFmpeg & aria2c",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.openGitHubRepo() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("GitHub", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = { viewModel.rescanDownloads() },
                        enabled = !state.isSyncingDownloads,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        if (state.isSyncingDownloads) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isSyncingDownloads) "Scanning…" else "Rescan Files", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (state.syncStatusMessage != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = state.syncStatusMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Generous bottom spacer so navigation bar never obstructs any card
        Spacer(Modifier.height(100.dp))
    }

    // App Update Dialog
    val dialogUpdate = state.availableAppUpdate
    if (state.showAppUpdateDialog && dialogUpdate != null) {
        AppUpdateDialog(
            updateInfo = dialogUpdate,
            onDismiss = { viewModel.showAppUpdateDialog(false) },
            onDownload = { viewModel.openAppUpdateDownload() },
            onViewRelease = { viewModel.openAppUpdateReleasePage() }
        )
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
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SwitchSettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
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
        headlineContent = { Text(template.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)) },
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
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete template",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
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
            Text("New Command Template", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.settings_template_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
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
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onAdd(name.trim(), args.trim()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && args.isNotBlank(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Add Template")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onViewRelease: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Upgrade,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Update Available",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${updateInfo.versionTag} • ${updateInfo.formattedSize}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "What's New:",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Release notes available on GitHub." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Download the update safely via your browser to avoid Play Protect false-positive warnings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Download APK")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onViewRelease) {
                    Text("View on GitHub")
                }
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
