package com.kappi.crunch.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatFilesize_emptyForNonPositiveValues() {
        assertEquals("", FormatUtils.formatFilesize(0))
        assertEquals("", FormatUtils.formatFilesize(-1))
        assertEquals("", FormatUtils.formatFilesize(-999999))
    }

    @Test
    fun formatFilesize_usesMegabytesBelowOneThousand() {
        assertEquals("0.5 MB", FormatUtils.formatFilesize(512 * 1024))
        assertEquals("1.0 MB", FormatUtils.formatFilesize(1024 * 1024))
        assertEquals("500.0 MB", FormatUtils.formatFilesize(500L * 1024 * 1024))
        assertEquals("999.0 MB", FormatUtils.formatFilesize(999L * 1024 * 1024))
    }

    @Test
    fun formatFilesize_switchesToGigabytes() {
        assertEquals("1.0 GB", FormatUtils.formatFilesize(1000L * 1024 * 1024))
        assertEquals("1.5 GB", FormatUtils.formatFilesize(1536L * 1024 * 1024))
        assertEquals("10.0 GB", FormatUtils.formatFilesize(10L * 1024 * 1024 * 1024))
    }

    @Test
    fun mimeTypeFor_allAudioAndVideoExtensions() {
        assertEquals("video/x-matroska", FormatUtils.mimeTypeFor("clip.MKV"))
        assertEquals("video/x-matroska", FormatUtils.mimeTypeFor("video.mkv"))
        assertEquals("video/mp4", FormatUtils.mimeTypeFor("sample.mp4"))
        assertEquals("video/mp4", FormatUtils.mimeTypeFor("sample.MP4"))
        assertEquals("video/webm", FormatUtils.mimeTypeFor("stream.webm"))
        assertEquals("audio/mpeg", FormatUtils.mimeTypeFor("song.mp3"))
        assertEquals("audio/mp4", FormatUtils.mimeTypeFor("audio.m4a"))
        assertEquals("audio/opus", FormatUtils.mimeTypeFor("voice.opus"))
        assertEquals("audio/opus", FormatUtils.mimeTypeFor("voice.OPUS"))
        assertEquals("audio/flac", FormatUtils.mimeTypeFor("lossless.flac"))
        assertEquals("audio/wav", FormatUtils.mimeTypeFor("sound.wav"))
        assertEquals("*/*", FormatUtils.mimeTypeFor("archive.zip"))
        assertEquals("*/*", FormatUtils.mimeTypeFor("no-extension"))
        assertEquals("*/*", FormatUtils.mimeTypeFor(""))
    }

    @Test
    fun splitArgs_handlesPlainAndQuotedTokens() {
        assertEquals(
            listOf("--embed-metadata", "--concurrent-fragments", "16"),
            FormatUtils.splitArgs("--embed-metadata  --concurrent-fragments 16"),
        )
        assertEquals(
            listOf("--postprocessor-args", "FFmpeg:-c:a aac"),
            FormatUtils.splitArgs("--postprocessor-args \"FFmpeg:-c:a aac\""),
        )
        assertEquals(
            listOf("--sub-lang", "en,es"),
            FormatUtils.splitArgs("--sub-lang 'en,es'"),
        )
        assertEquals(
            listOf("--flag", "value with spaces", "--another-flag"),
            FormatUtils.splitArgs("--flag \"value with spaces\" --another-flag")
        )
        assertEquals(emptyList<String>(), FormatUtils.splitArgs(""))
        assertEquals(emptyList<String>(), FormatUtils.splitArgs("   "))
    }

    @Test
    fun conciseError_cleansErrorPrefixAndTraceback() {
        assertEquals("Unknown error", FormatUtils.conciseError(null))
        assertEquals("Unknown error", FormatUtils.conciseError(""))
        assertEquals("Unknown error", FormatUtils.conciseError("    "))
        assertEquals(
            "unable to open for writing: [Errno 36] File name too long",
            FormatUtils.conciseError("ERROR: unable to open for writing: [Errno 36] File name too long")
        )

        val tb = """
            Traceback (most recent call last):
              File "yt_dlp/downloader/common.py", line 370, in download
            ERROR: Video unavailable
        """.trimIndent()
        assertEquals("Video unavailable", FormatUtils.conciseError(tb))

        val tbException = """
            Traceback (most recent call last):
              File "yt_dlp/extractor/youtube.py", line 123
            yt_dlp.utils.ExtractorError: Private video. Sign in if you've been granted access.
        """.trimIndent()
        assertEquals(
            "yt_dlp.utils.ExtractorError: Private video. Sign in if you've been granted access.",
            FormatUtils.conciseError(tbException)
        )
    }

    @Test
    fun conciseError_truncatesExtremelyLongErrors() {
        val longError = "A".repeat(500)
        val concise = FormatUtils.conciseError(longError)
        assertTrue(concise.length <= 400)
    }
}
