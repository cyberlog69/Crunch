package com.kappi.crunch.download

import android.content.Context
import android.util.Log
import com.kappi.crunch.data.FormatOption
import com.kappi.crunch.data.PlaylistEntry
import com.kappi.crunch.data.VideoInfo
import com.kappi.crunch.util.FormatUtils
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

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

        /**
         * yt-dlp updates replace the zipapp in place. Python imports modules from
         * that archive lazily, so rewriting it while a fetch or download runs
         * corrupts the running process ("ZipImportError: bad local file header").
         * Fetches and downloads share the read lock; updates need the write lock.
         */
        private val engineLock = ReentrantReadWriteLock()

        private fun <T> withEngineRead(block: () -> T): T {
            val lock = engineLock.readLock()
            lock.lock()
            try {
                return block()
            } finally {
                lock.unlock()
            }
        }

        private fun isCorruptEngineError(message: String?): Boolean =
            message != null && (
                message.contains("ZipImportError") ||
                    message.contains("bad local file header")
                )

        /** Stops a running yt-dlp process started with the given process id. */
        fun cancelProcess(processId: String): Boolean = try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            Log.w(TAG, "cancelProcess($processId): ${e.message}")
            false
        }
    }

    @Volatile
    private var initialized = false

    val isBinaryAvailable: Boolean
        get() = try {
            ensureInitialized()
            true
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp is not available: ${e.message}")
            false
        }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val lock = engineLock.writeLock()
            lock.lock()
            try {
                EngineRepair.run(context.applicationContext)
                YoutubeDL.getInstance().init(context.applicationContext)
                initialized = true
            } catch (t: Throwable) {
                Log.e(TAG, "yt-dlp engine failed to initialize", t)
                throw IllegalStateException("yt-dlp engine failed to initialize: ${t.message}", t)
            } finally {
                lock.unlock()
            }
        }
    }

    fun getYtDlpVersion(): String? {
        return try {
            ensureInitialized()
            withEngineRead { YoutubeDL.getInstance().version(context) }
        } catch (e: Exception) {
            Log.w(TAG, "getYtDlpVersion: ${e.message}")
            null
        }
    }

    /**
     * @param waitForIdle background updates should give up when a fetch or
     * download is running instead of stalling the worker.
     */
    suspend fun updateYoutubeDL(waitForIdle: Boolean = true): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        ensureInitialized()

        val lock = engineLock.writeLock()
        val acquired = try {
            if (waitForIdle) lock.tryLock(5, TimeUnit.SECONDS) else lock.tryLock()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!acquired) {
            Log.i(TAG, "updateYoutubeDL skipped: engine is busy")
            return@withContext Pair(false, "yt-dlp is busy; update postponed")
        }

        try {
            val oldVersion = getYtDlpVersion() ?: "Unknown"
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val newVersion = getYtDlpVersion() ?: oldVersion
            if (status == YoutubeDL.UpdateStatus.DONE) {
                Pair(true, "Updated yt-dlp to $newVersion (was $oldVersion)")
            } else {
                Pair(false, "yt-dlp is already up to date ($newVersion)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateYoutubeDL error", e)
            Pair(false, "Update failed: ${FormatUtils.conciseError(e.message)}")
        } finally {
            lock.unlock()
        }
    }

    /**
     * Runs an engine call under the read lock. If the zipapp turns out to be
     * corrupt (for example an update interrupted by the OS), restore the copy
     * bundled in the APK and retry once.
     */
    private fun <T> runEngine(block: () -> T): T {
        try {
            return withEngineRead(block)
        } catch (e: YoutubeDLException) {
            if (!isCorruptEngineError(e.message)) throw e
            Log.w(TAG, "yt-dlp archive is corrupt; restoring bundled copy and retrying", e)
            if (!EngineRepair.restoreYtDlp(context.applicationContext)) throw e
            return withEngineRead(block)
        }
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
        }

        val response = try {
            runEngine { YoutubeDL.getInstance().execute(request) }
        } catch (e: Exception) {
            Log.e(TAG, "fetchInfo failed for $cleanUrl", e)
            throw e
        }
        val output = response.out
        if (output.isBlank()) throw IllegalStateException("Empty output from yt-dlp")

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
                    val sizeStr = if (vf.filesize > 0) " • ${FormatUtils.formatFilesize(vf.filesize)}" else ""
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
                    val sizeStr = if (af.filesize > 0) " • ${FormatUtils.formatFilesize(af.filesize)}" else ""
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

        val playlistEntries = if (isPlaylist) parsePlaylistEntries(firstObj, lines) else emptyList()

        return VideoInfo(
            url = url,
            title = firstObj.optString("title", "Unknown title"),
            uploader = firstObj.optString("uploader", firstObj.optString("channel", "")),
            thumbnailUrl = firstObj.optString("thumbnail", ""),
            duration = firstObj.optLong("duration", 0L),
            description = firstObj.optString("description", ""),
            isPlaylist = isPlaylist,
            playlistCount = if (playlistEntries.isNotEmpty()) playlistEntries.size else lines.size,
            playlistId = firstObj.optString("playlist_id", ""),
            formats = formats,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            subtitleLanguages = subtitleLangs,
            autoSubtitleLanguages = autoSubLangs,
            playlistEntries = playlistEntries,
        )
    }

    private fun parsePlaylistEntries(firstObj: JSONObject, lines: List<String>): List<PlaylistEntry> {
        val entries = mutableListOf<PlaylistEntry>()

        // Non-flat metadata returns a single playlist object with an "entries" array.
        val array = firstObj.optJSONArray("entries")
        if (array != null) {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { entries.add(it.toPlaylistEntry()) }
            }
        } else {
            // --flat-playlist emits one JSON object per entry.
            for (line in lines) {
                val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
                if (obj.optString("_type") == "playlist") continue
                entries.add(obj.toPlaylistEntry())
            }
        }

        return entries.filter { it.url.isNotBlank() }
    }

    private fun JSONObject.toPlaylistEntry(): PlaylistEntry {
        val entryUrl = optString("webpage_url").ifEmpty { optString("url") }
        val thumb = optString("thumbnail").ifEmpty {
            val thumbs = optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) {
                thumbs.optJSONObject(thumbs.length() - 1)?.optString("url", "") ?: ""
            } else {
                ""
            }
        }
        return PlaylistEntry(
            url = entryUrl,
            title = optString("title", "Untitled"),
            uploader = optString("uploader", optString("channel", "")),
            duration = optLong("duration", 0L),
            thumbnailUrl = thumb,
        )
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
        processId: String = java.util.UUID.randomUUID().toString(),
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
            for (arg in FormatUtils.splitArgs(customExtraArgs)) {
                request.addOption(arg)
            }
        }

        var lastFilePath = ""

        runEngine {
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
}
