package com.kappi.crunch.download

import android.content.Context
import android.util.Log
import com.kappi.crunch.data.FormatOption
import com.kappi.crunch.data.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "YtDlpEngine"

/**
 * Wraps yt-dlp execution using the native youtubedl-android engine.
 * Runs Python, yt-dlp, FFmpeg, and Aria2c via Android NDK native libraries,
 * fully compliant with Android 10+ W^X security policies.
 */
class YtDlpEngine(private val context: Context) {

    companion object {
        // Cache VideoInfo in memory for 10 minutes to make repeat/re-fetched links instantaneous (0ms)
        private val cache = ConcurrentHashMap<String, Pair<Long, VideoInfo>>()
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    val isBinaryAvailable: Boolean
        get() {
            return try {
                ensureInitialized()
                true
            } catch (e: Exception) {
                false
            }
        }

    private fun ensureInitialized() {
        try {
            YoutubeDL.getInstance().init(context)
        } catch (e: Exception) {
            Log.w(TAG, "YoutubeDL.init: ${e.message}")
        }
    }

    fun getYtDlpVersion(): String? {
        ensureInitialized()
        return try {
            YoutubeDL.getInstance().version(context)
        } catch (e: Exception) {
            Log.w(TAG, "getYtDlpVersion: ${e.message}")
            null
        }
    }

    suspend fun updateYoutubeDL(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val oldVersion = getYtDlpVersion() ?: "Unknown"
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val newVersion = getYtDlpVersion() ?: oldVersion
            if (status == YoutubeDL.UpdateStatus.DONE) {
                Pair(true, "Updated yt-dlp to $newVersion (was $oldVersion)")
            } else {
                Pair(false, "yt-dlp is already up to date ($newVersion)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateYoutubeDL error", e)
            Pair(false, "Update failed: ${e.message ?: "Network error"}")
        }
    }

    suspend fun downloadBinary(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        ensureInitialized()
        onProgress(0.5f)
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        } catch (e: Exception) {
            Log.w(TAG, "Update check: ${e.message}")
        }
        onProgress(1.0f)
    }


    private fun isLikelyPlaylist(url: String): Boolean {
        val lower = url.lowercase()
        // Shorts and direct video links are NEVER playlists
        if (lower.contains("/shorts/") || lower.contains("youtu.be/")) return false
        if (lower.contains("watch?") && lower.contains("v=")) return false
        return lower.contains("/playlist") ||
                lower.contains("list=") ||
                lower.contains("/sets/") ||
                lower.contains("/album/") ||
                lower.contains("/series/") ||
                lower.contains("/channel/") ||
                lower.contains("/@") ||
                lower.contains("/c/") ||
                lower.contains("/user/")
    }

    suspend fun fetchInfo(url: String): VideoInfo = withContext(Dispatchers.IO) {
        ensureInitialized()

        val cleanUrl = url.trim()

        // 1. Instant cache hit for already-fetched or re-opened URLs
        val cached = cache[cleanUrl]
        if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
            Log.d(TAG, "fetchInfo: cache HIT for $cleanUrl")
            return@withContext cached.second
        }

        val likelyPlaylist = isLikelyPlaylist(cleanUrl)

        // 2. Optimized single-pass yt-dlp request (no double execution)
        val request = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-json")
            if (likelyPlaylist) {
                addOption("--flat-playlist")
            } else {
                addOption("--no-playlist")
            }
            addOption("--no-warnings")
            addOption("--no-check-certificates")
            addOption("--socket-timeout", "10")
            addOption("--geo-bypass")
            addOption("--extractor-args", "youtube:player_client=android,web")
        }

        val response = YoutubeDL.getInstance().execute(request)
        val output = response.out ?: throw IllegalStateException("Empty output from yt-dlp")

        val lines = output.lines().filter { it.trimStart().startsWith("{") }
        if (lines.isEmpty()) throw IllegalStateException("No JSON output from yt-dlp")

        val firstObj = JSONObject(lines.first())
        val isPlaylist = likelyPlaylist ||
                firstObj.optString("_type") == "playlist" ||
                firstObj.optString("webpage_url_basename").contains("playlist", ignoreCase = true) ||
                lines.size > 1

        val info = parseVideoInfo(cleanUrl, output, isPlaylist)

        // 3. Cache the parsed info
        if (cache.size > 50) cache.clear()
        cache[cleanUrl] = Pair(System.currentTimeMillis(), info)

        info
    }

    private fun parseVideoInfo(url: String, jsonOutput: String, isPlaylist: Boolean): VideoInfo {
        val lines = jsonOutput.lines().filter { it.trimStart().startsWith("{") }
        if (lines.isEmpty()) throw IllegalStateException("No JSON output from yt-dlp")

        val firstObj = JSONObject(lines.first())

        val formats = mutableListOf<FormatOption>()
        val videoFormats = mutableListOf<FormatOption>()
        val audioFormats = mutableListOf<FormatOption>()
        val subtitleLangs = mutableListOf<String>()
        val autoSubLangs = mutableListOf<String>()

        if (!isPlaylist) {
            val formatsArr = firstObj.optJSONArray("formats")
            if (formatsArr != null) {
                for (i in 0 until formatsArr.length()) {
                    val f = formatsArr.getJSONObject(i)
                    val formatId = f.optString("format_id")
                    if (formatId.isEmpty()) continue
                    val vcodec = f.optString("vcodec", "none")
                    val acodec = f.optString("acodec", "none")
                    val hasVideo = vcodec != "none" && vcodec.isNotEmpty()
                    val hasAudio = acodec != "none" && acodec.isNotEmpty()
                    val height = f.optInt("height", 0)
                    val fps = f.optInt("fps", 0)
                    val ext = f.optString("ext", "")
                    val tbr = f.optDouble("tbr", 0.0).toFloat()
                    val abr = f.optDouble("abr", 0.0).toFloat()
                    val size = when {
                        f.optLong("filesize", 0L) > 0L -> f.optLong("filesize", 0L)
                        f.optLong("filesize_approx", 0L) > 0L -> f.optLong("filesize_approx", 0L)
                        else -> 0L
                    }

                    val opt = FormatOption(
                        id = formatId,
                        label = when {
                            hasVideo && hasAudio -> "${height}p ($ext)"
                            hasVideo -> "${height}p ($ext)"
                            hasAudio -> "audio ($ext ${tbr.toInt()}kbps)"
                            else -> formatId
                        },
                        ext = ext,
                        resolution = if (height > 0) "${height}p" else "",
                        height = height,
                        fps = fps,
                        filesize = size,
                        isVideoOnly = hasVideo && !hasAudio,
                        isAudioOnly = !hasVideo && hasAudio,
                        vcodec = vcodec,
                        acodec = acodec,
                        tbr = tbr,
                        abr = abr,
                    )
                    formats.add(opt)
                }

                // ── Build Clean Video Formats (Deduplicated, sorted descending) ──
                videoFormats.add(
                    FormatOption(
                        id = "bestvideo",
                        label = "Best Video (Auto)",
                        ext = "mp4",
                        height = 99999,
                        isVideoOnly = true,
                        qualityLabel = "Best Video",
                    )
                )

                val rawVideoList = formats
                    .filter { it.height > 0 || (it.vcodec != "none" && it.vcodec.isNotEmpty()) }
                    .groupBy { "${it.height}_${if (it.fps >= 50) 60 else 30}_${it.ext.lowercase()}" }
                    .map { (_, group) -> group.maxByOrNull { it.tbr } ?: group.first() }
                    .sortedWith(
                        compareByDescending<FormatOption> { it.height }
                            .thenByDescending { it.fps }
                            .thenByDescending { it.tbr }
                    )

                for (vf in rawVideoList) {
                    val qName = when {
                        vf.height >= 4320 -> "8K (4320p)"
                        vf.height >= 2160 -> "4K (2160p)"
                        vf.height >= 1440 -> "1440p (2K)"
                        vf.height >= 1080 -> "1080p (FHD)"
                        vf.height >= 720 -> "720p (HD)"
                        vf.height >= 480 -> "480p"
                        vf.height >= 360 -> "360p"
                        vf.height >= 240 -> "240p"
                        vf.height > 0 -> "${vf.height}p"
                        else -> "Video"
                    }
                    val fpsStr = if (vf.fps >= 50) " ${vf.fps}fps" else ""
                    val extStr = if (vf.ext.isNotEmpty()) " • ${vf.ext.uppercase()}" else ""
                    val sizeStr = if (vf.filesize > 0) " • ${formatFilesize(vf.filesize)}" else ""
                    val displayLabel = "$qName$fpsStr$extStr$sizeStr"

                    videoFormats.add(
                        vf.copy(
                            label = displayLabel,
                            qualityLabel = "$qName$fpsStr",
                        )
                    )
                }

                // ── Build Clean Audio Formats (Deduplicated, sorted descending) ──
                audioFormats.add(
                    FormatOption(
                        id = "bestaudio",
                        label = "Best Audio (Auto)",
                        ext = "m4a",
                        tbr = 9999f,
                        isAudioOnly = true,
                        qualityLabel = "Best Audio",
                    )
                )

                val rawAudioList = formats
                    .filter { it.isAudioOnly || (it.acodec != "none" && it.acodec.isNotEmpty() && !it.isVideoOnly && it.height == 0) }
                    .groupBy {
                        val br = if (it.abr > 0) it.abr.toInt() else it.tbr.toInt()
                        "${br}_${it.ext.lowercase()}"
                    }
                    .map { (_, group) -> group.maxByOrNull { it.tbr } ?: group.first() }
                    .sortedWith(
                        compareByDescending<FormatOption> { if (it.abr > 0) it.abr else it.tbr }
                    )

                for (af in rawAudioList) {
                    val bitrate = if (af.abr > 0) af.abr.toInt() else af.tbr.toInt()
                    val tier = when {
                        bitrate >= 256 -> "Ultra"
                        bitrate >= 160 -> "High"
                        bitrate >= 120 -> "Standard"
                        bitrate >= 64 -> "Medium"
                        bitrate > 0 -> "Low"
                        else -> ""
                    }
                    val codec = when {
                        af.acodec.contains("opus", ignoreCase = true) -> "Opus"
                        af.acodec.contains("mp4a", ignoreCase = true) || af.ext.equals("m4a", ignoreCase = true) -> "AAC"
                        af.acodec != "none" && af.acodec.isNotEmpty() -> af.acodec.substringBefore(".")
                        af.ext.isNotEmpty() -> af.ext.uppercase()
                        else -> "Audio"
                    }
                    val sizeStr = if (af.filesize > 0) " • ${formatFilesize(af.filesize)}" else ""
                    val audioLabel = if (bitrate > 0) {
                        if (tier.isNotEmpty()) "$bitrate kbps ($tier • $codec)$sizeStr"
                        else "$bitrate kbps ($codec)$sizeStr"
                    } else {
                        "$codec$sizeStr"
                    }

                    audioFormats.add(
                        af.copy(
                            label = audioLabel,
                            qualityLabel = if (bitrate > 0) "$bitrate kbps ($codec)" else codec,
                        )
                    )
                }
            }

            val subs = firstObj.optJSONObject("subtitles")
            subs?.keys()?.forEach { subtitleLangs.add(it) }
            val autoSubs = firstObj.optJSONObject("automatic_captions")
            autoSubs?.keys()?.forEach { autoSubLangs.add(it) }
        }

        return VideoInfo(
            url = url,
            title = firstObj.optString("title", "Unknown title"),
            uploader = firstObj.optString("uploader", firstObj.optString("channel", "")),
            thumbnailUrl = firstObj.optString("thumbnail", ""),
            duration = firstObj.optLong("duration", 0L),
            description = firstObj.optString("description", ""),
            isPlaylist = isPlaylist,
            playlistCount = if (isPlaylist) lines.size else 1,
            playlistId = firstObj.optString("playlist_id", ""),
            formats = formats,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            subtitleLanguages = subtitleLangs,
            autoSubtitleLanguages = autoSubLangs,
        )
    }

    private fun formatFilesize(bytes: Long): String {
        if (bytes <= 0) return ""
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024)
        } else {
            String.format(Locale.getDefault(), "%.0f MB", mb)
        }
    }

    suspend fun download(
        url: String,
        downloadType: com.kappi.crunch.data.DownloadType,
        formatId: String,
        audioFormat: String,
        videoContainer: String = "mkv",
        outputDir: String,
        subtitleLang: String,
        embedSubtitles: Boolean,
        embedThumbnail: Boolean,
        embedMetadata: Boolean,
        useExternalDownloader: Boolean,
        downloadThreads: Int = 16,
        customExtraArgs: String,
        onProgress: (progress: Float, speed: String, eta: String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        ensureInitialized()

        val outDir = File(outputDir).apply { mkdirs() }
        val request = YoutubeDLRequest(url)

        request.addOption("-o", "${outDir.absolutePath}/%(title)s.%(ext)s")

        if (downloadType == com.kappi.crunch.data.DownloadType.AUDIO) {
            val audioSelector = when {
                formatId.isNotEmpty() && formatId != "best" && formatId != "bestaudio" -> formatId
                else -> "bestaudio/best"
            }
            request.addOption("-f", audioSelector)
            request.addOption("-x")
            request.addOption("--audio-format", audioFormat)
            request.addOption("--audio-quality", "0")
            if (embedThumbnail) request.addOption("--embed-thumbnail")
            if (embedMetadata) request.addOption("--embed-metadata")
        } else {
            val f = when {
                formatId.contains("+") -> formatId
                formatId.isNotEmpty() && formatId != "best" && formatId != "bestvideo" -> "$formatId+bestaudio/best"
                else -> "bestvideo+bestaudio/best"
            }
            request.addOption("-f", f)
            val container = if (videoContainer.isNotBlank()) videoContainer.lowercase() else "mkv"
            request.addOption("--merge-output-format", container)
            request.addOption("--remux-video", container)
            if (embedMetadata) request.addOption("--embed-metadata")
        }

        if (subtitleLang.isNotEmpty()) {
            if (subtitleLang.startsWith("auto:")) {
                request.addOption("--write-auto-subs")
                request.addOption("--sub-lang", subtitleLang.removePrefix("auto:"))
            } else {
                request.addOption("--write-subs")
                request.addOption("--sub-lang", subtitleLang)
            }
            if (embedSubtitles) {
                request.addOption("--embed-subs")
            }
        }

        // ── Multithreaded & Parallel Chunk Splitting ───────────────────────
        val threads = if (downloadThreads in 1..32) downloadThreads else 16

        // Concurrent fragment downloader for DASH/HLS segmented streams
        request.addOption("--concurrent-fragments", threads.toString())
        request.addOption("--buffer-size", "1024k")
        request.addOption("--http-chunk-size", "10M")

        // External multi-connection split downloader via aria2c
        if (useExternalDownloader) {
            request.addOption("--downloader", "http,https,ftp:aria2c")
            request.addOption("--downloader-args", "aria2c:-x $threads -s $threads -j $threads -k 1M --file-allocation=none --summary-interval=1")
        }

        if (customExtraArgs.isNotBlank()) {
            for (arg in splitArgs(customExtraArgs)) {
                request.addOption(arg)
            }
        }

        var lastFilePath = ""
        val processId = UUID.randomUUID().toString()

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, line ->
            if (line.isNotEmpty()) {
                Log.v(TAG, line)
                var speedStr = ""
                if (line.contains("at") && line.contains("/s")) {
                    speedStr = line.substringAfter("at").substringBefore("ETA").trim()
                }
                val etaStr = if (etaInSeconds > 0) {
                    val m = etaInSeconds / 60
                    val s = etaInSeconds % 60
                    String.format(Locale.getDefault(), "%02d:%02d", m, s)
                } else ""

                onProgress(progress / 100f, speedStr, etaStr)

                if (line.startsWith("[download] Destination:") ||
                    line.startsWith("[Merger] Merging") ||
                    line.contains("Remuxing video")) {
                    val path = when {
                        line.contains("Merging formats into") -> line.substringAfter("Merging formats into")
                        line.contains(" to \"") -> line.substringAfter(" to \"").substringBefore("\"")
                        line.contains(" to ") -> line.substringAfter(" to ")
                        line.startsWith("[download] Destination:") -> line.substringAfter("Destination:")
                        else -> ""
                    }.trim().removeSurrounding("\"")
                    if (path.isNotEmpty()) lastFilePath = path
                }
                if (line.startsWith("[ExtractAudio]")) {
                    val path = line.substringAfter("Destination:").trim()
                    if (path.isNotEmpty()) lastFilePath = path
                }
            }
        }

        if (lastFilePath.isEmpty() || !File(lastFilePath).exists()) {
            val candidates = outDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            if (downloadType == com.kappi.crunch.data.DownloadType.VIDEO) {
                val targetExt = if (videoContainer.isNotBlank()) videoContainer.lowercase() else "mkv"
                val match = candidates?.filter { it.extension.equals(targetExt, ignoreCase = true) }?.maxByOrNull { it.lastModified() }
                lastFilePath = match?.absolutePath ?: candidates?.maxByOrNull { it.lastModified() }?.absolutePath ?: ""
            } else {
                lastFilePath = candidates?.maxByOrNull { it.lastModified() }?.absolutePath ?: ""
            }
        }
        lastFilePath
    }

    private fun splitArgs(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = ' '
        for (c in input) {
            when {
                !inQuote && (c == '"' || c == '\'') -> { inQuote = true; quoteChar = c }
                inQuote && c == quoteChar -> { inQuote = false }
                !inQuote && c == ' ' -> {
                    if (current.isNotEmpty()) { result.add(current.toString()); current.clear() }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result
    }
}
