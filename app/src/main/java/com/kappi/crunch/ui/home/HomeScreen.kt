package com.kappi.crunch.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kappi.crunch.R
import com.kappi.crunch.data.DownloadType
import com.kappi.crunch.data.FormatOption
import com.kappi.crunch.data.VideoInfo
import com.kappi.crunch.theme.StatusActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    sharedUrl: String? = null,
    onSharedUrlConsumed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val opts by viewModel.downloadOptions.collectAsStateWithLifecycle()
    val binaryProgress by viewModel.binaryProgress.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current

    // Automatically load and fetch when a URL is shared from an external app (YouTube, Facebook, etc.)
    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank()) {
            viewModel.onUrlChange(sharedUrl)
            viewModel.onFetchClicked()
            onSharedUrlConsumed?.invoke()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ── App header ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_crunch_logo),
                    contentDescription = "Crunch Logo",
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Crunch",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Download anything, beautifully.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 24.dp)
        )

        // ── URL input ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = urlInput,
            onValueChange = viewModel::onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.paste_link_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (urlInput.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onUrlChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { text ->
                                val clean = text.trim()
                                viewModel.onUrlChange(clean)
                                if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
                                    viewModel.onFetchClicked()
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste")
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = {
                focusManager.clearFocus()
                viewModel.onFetchClicked()
            }),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        Spacer(Modifier.height(12.dp))

        // ── Fetch button ──────────────────────────────────────────────────
        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.onFetchClicked()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            enabled = uiState !is HomeUiState.FetchingInfo && uiState !is HomeUiState.FetchingBinary,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.btn_fetch))
        }

        // ── Status indicators ─────────────────────────────────────────────
        AnimatedVisibility(uiState is HomeUiState.FetchingBinary) {
            Column(Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.downloading_ytdlp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { binaryProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        AnimatedVisibility(uiState is HomeUiState.FetchingInfo) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.label_fetching),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Error state ───────────────────────────────────────────────────
        (uiState as? HomeUiState.Error)?.let { err ->
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        err.message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    IconButton(onClick = viewModel::onClearError) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        // ── Info ready state ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState is HomeUiState.InfoReady,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            (uiState as? HomeUiState.InfoReady)?.let { infoState ->
                VideoInfoCard(
                    info = infoState.info,
                    opts = opts,
                    templates = viewModel.templates,
                    onTypeSelected = viewModel::onTypeSelected,
                    onVideoFormatSelected = viewModel::onVideoFormatSelected,
                    onAudioTrackSelected = viewModel::onAudioTrackSelected,
                    onAudioFormatSelected = viewModel::onAudioFormatSelected,
                    onSubtitleLangSelected = viewModel::onSubtitleLangSelected,
                    onEmbedSubtitlesChanged = viewModel::onEmbedSubtitlesChanged,
                    onExternalDownloaderChanged = viewModel::onExternalDownloaderChanged,
                    onTemplateSelected = viewModel::onTemplateSelected,
                    onDownloadClicked = viewModel::onDownloadClicked,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun VideoInfoCard(
    info: VideoInfo,
    opts: DownloadOptions,
    templates: List<com.kappi.crunch.data.CommandTemplate>,
    onTypeSelected: (DownloadType) -> Unit,
    onVideoFormatSelected: (FormatOption) -> Unit,
    onAudioTrackSelected: (FormatOption) -> Unit,
    onAudioFormatSelected: (String) -> Unit,
    onSubtitleLangSelected: (String) -> Unit,
    onEmbedSubtitlesChanged: (Boolean) -> Unit,
    onExternalDownloaderChanged: (Boolean) -> Unit,
    onTemplateSelected: (com.kappi.crunch.data.CommandTemplate?) -> Unit,
    onDownloadClicked: () -> Unit,
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        // Thumbnail + title
        Card(
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = info.thumbnailUrl,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 60f,
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.uploader.isNotEmpty()) {
                        Text(
                            text = info.uploader,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    if (info.isPlaylist) {
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.label_playlist_count, info.playlistCount),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.PlaylistPlay, contentDescription = null, Modifier.size(16.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                labelColor = Color.White,
                                leadingIconContentColor = Color.White,
                            ),
                        )
                    }
                }
                // Duration badge
                if (info.duration > 0) {
                    Text(
                        text = formatDuration(info.duration),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Type toggle ─────────────────────────────────────────────────
        Text(
            "Download as",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = opts.type == DownloadType.VIDEO,
                onClick = { onTypeSelected(DownloadType.VIDEO) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    Icon(Icons.Filled.Videocam, contentDescription = null, Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.label_video)) }
            )
            SegmentedButton(
                selected = opts.type == DownloadType.AUDIO,
                onClick = { onTypeSelected(DownloadType.AUDIO) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, Modifier.size(18.dp))
                },
                label = { Text(stringResource(R.string.label_audio)) }
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Video Mode: Video Quality & Audio Quality (Split Up) ──────────
        if (opts.type == DownloadType.VIDEO) {
            val vFormats = if (info.videoFormats.isNotEmpty()) info.videoFormats else info.formats.filter { !it.isAudioOnly }
            if (vFormats.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.label_video_quality),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FormatSelector(
                    formats = vFormats,
                    selected = opts.selectedVideoFormat ?: opts.selectedFormat,
                    onSelect = onVideoFormatSelected,
                )
                Spacer(Modifier.height(16.dp))
            }

            val aFormats = if (info.audioFormats.isNotEmpty()) info.audioFormats else info.formats.filter { it.isAudioOnly }
            if (aFormats.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.label_audio_quality),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FormatSelector(
                    formats = aFormats,
                    selected = opts.selectedAudioTrack,
                    onSelect = onAudioTrackSelected,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Audio Mode: Audio Format & Audio Source Quality ─────────────
        if (opts.type == DownloadType.AUDIO) {
            Text(
                stringResource(R.string.label_audio_format),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val audioFormats = listOf("mp3", "m4a", "opus", "flac", "wav")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(audioFormats) { fmt ->
                    FilterChip(
                        selected = opts.audioFormat == fmt,
                        onClick = { onAudioFormatSelected(fmt) },
                        label = { Text(fmt.uppercase()) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            val aFormats = if (info.audioFormats.isNotEmpty()) info.audioFormats else info.formats.filter { it.isAudioOnly }
            if (aFormats.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.label_audio_quality),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FormatSelector(
                    formats = aFormats,
                    selected = opts.selectedAudioTrack,
                    onSelect = onAudioTrackSelected,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Subtitles ───────────────────────────────────────────────────
        val allSubtitleLangs = buildList {
            addAll(info.subtitleLanguages)
            addAll(info.autoSubtitleLanguages.map { "auto:$it" })
        }
        if (allSubtitleLangs.isNotEmpty()) {
            SubtitleSection(
                langs = allSubtitleLangs,
                selected = opts.subtitleLang,
                embedSubtitles = opts.embedSubtitles,
                onLangSelected = onSubtitleLangSelected,
                onEmbedChanged = onEmbedSubtitlesChanged,
                showEmbed = opts.type == DownloadType.VIDEO,
            )
            Spacer(Modifier.height(16.dp))
        }

        // ── Advanced options (collapsible) ──────────────────────────────
        OutlinedCard(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Advanced options") },
                    leadingContent = {
                        Icon(Icons.Outlined.Tune, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(
                            if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable { showAdvanced = !showAdvanced }
                )
                AnimatedVisibility(showAdvanced) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        // External downloader
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Multithreaded Chunk Splitting",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "16-connection parallel split & concurrent fragments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = opts.useExternalDownloader,
                                onCheckedChange = onExternalDownloaderChanged,
                            )
                        }

                        if (templates.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Command template",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = opts.selectedTemplate == null,
                                        onClick = { onTemplateSelected(null) },
                                        label = { Text("Default") },
                                    )
                                }
                                items(templates) { tpl ->
                                    FilterChip(
                                        selected = opts.selectedTemplate == tpl,
                                        onClick = { onTemplateSelected(tpl) },
                                        label = { Text(tpl.name) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Download button ─────────────────────────────────────────────
        Button(
            onClick = onDownloadClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(
                if (info.isPlaylist)
                    "Download Playlist (${info.playlistCount})"
                else
                    stringResource(R.string.btn_download),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DownloadTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun FormatSelector(
    formats: List<FormatOption>,
    selected: FormatOption?,
    onSelect: (FormatOption) -> Unit,
) {
    val itemsList = formats.filter { it.label.isNotEmpty() }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(itemsList, key = { it.id + "_" + it.label }) { fmt ->
            val isSelected = selected?.id == fmt.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(fmt) },
                label = { Text(fmt.label) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

@Composable
private fun SubtitleSection(
    langs: List<String>,
    selected: String,
    embedSubtitles: Boolean,
    onLangSelected: (String) -> Unit,
    onEmbedChanged: (Boolean) -> Unit,
    showEmbed: Boolean,
) {
    Column {
        Text(
            stringResource(R.string.label_subtitles),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selected.isEmpty(),
                    onClick = { onLangSelected("") },
                    label = { Text("None") },
                )
            }
            items(langs.take(10)) { lang ->
                val displayLang = if (lang.startsWith("auto:")) "auto:${lang.removePrefix("auto:")}" else lang
                FilterChip(
                    selected = selected == lang,
                    onClick = { onLangSelected(lang) },
                    label = { Text(displayLang.take(8)) },
                )
            }
        }
        if (showEmbed && selected.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.label_embed_subtitles),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = embedSubtitles, onCheckedChange = onEmbedChanged)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
