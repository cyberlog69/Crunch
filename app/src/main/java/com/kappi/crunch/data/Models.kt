package com.kappi.crunch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadType { VIDEO, AUDIO }
enum class DownloadStatus { QUEUED, FETCHING, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELLED }

/**
 * Represents a single download task, either a standalone video or one item in a playlist.
 */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,            // UUID
    val url: String,
    val title: String,
    val uploader: String = "",
    val thumbnailUrl: String = "",
    val duration: Long = 0L,               // seconds
    val downloadType: DownloadType = DownloadType.VIDEO,
    val formatId: String = "",             // yt-dlp format id
    val formatLabel: String = "",          // human-readable quality label
    val audioFormat: String = "mp3",       // mp3, m4a, opus, …
    val subtitleLang: String = "",         // "" = none, "en", "auto", …
    val embedSubtitles: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,              // 0..1
    val speed: String = "",                // e.g. "3.2 MiB/s"
    val eta: String = "",                  // e.g. "00:42"
    val filePath: String = "",
    val fileSize: Long = 0L,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    // Playlist grouping
    val playlistId: String = "",           // empty if not part of a playlist
    val playlistIndex: Int = 0,
    val workerId: String = "",             // WorkManager ID for cancellation
    val useExternalDownloader: Boolean = false,
    val customTemplate: String = "",
)

/**
 * Represents a fetched format option returned by yt-dlp.
 */
data class FormatOption(
    val id: String,
    val label: String,          // e.g. "1080p (Full HD) • MP4"
    val ext: String,
    val resolution: String = "",
    val height: Int = 0,
    val fps: Int = 0,
    val filesize: Long = 0,
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false,
    val vcodec: String = "",
    val acodec: String = "",
    val tbr: Float = 0f,
    val abr: Float = 0f,
    val qualityLabel: String = "", // clean badge, e.g. "1080p (FHD)", "Best Audio"
)

/**
 * Metadata fetched from yt-dlp --dump-json for a URL.
 */
data class VideoInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val duration: Long,
    val description: String,
    val isPlaylist: Boolean,
    val playlistCount: Int,
    val playlistId: String,
    val formats: List<FormatOption>,
    val videoFormats: List<FormatOption> = emptyList(),
    val audioFormats: List<FormatOption> = emptyList(),
    val subtitleLanguages: List<String>,       // available subtitle langs
    val autoSubtitleLanguages: List<String>,   // auto-generated langs
)

/**
 * A saved user-defined command template for advanced usage.
 */
data class CommandTemplate(
    val id: String,
    val name: String,
    val extraArgs: String,           // e.g. "--remux-video mkv --no-playlist"
)
