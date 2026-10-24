package com.kappi.crunch.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatFilesize_emptyForNonPositiveValues() {
        assertEquals("", FormatUtils.formatFilesize(0))
        assertEquals("", FormatUtils.formatFilesize(-1))
    }

    @Test
    fun formatFilesize_usesMegabytesBelowOneThousand() {
        assertEquals("1.0 MB", FormatUtils.formatFilesize(1024 * 1024))
        assertEquals("500.0 MB", FormatUtils.formatFilesize(500L * 1024 * 1024))
    }

    @Test
    fun formatFilesize_switchesToGigabytes() {
        assertEquals("1.5 GB", FormatUtils.formatFilesize((1536L * 1024 * 1024)))
    }

    @Test
    fun mimeTypeFor_knownAndUnknownExtensions() {
        assertEquals("video/x-matroska", FormatUtils.mimeTypeFor("clip.MKV"))
        assertEquals("audio/mpeg", FormatUtils.mimeTypeFor("song.mp3"))
        assertEquals("*/*", FormatUtils.mimeTypeFor("archive.zip"))
        assertEquals("*/*", FormatUtils.mimeTypeFor("no-extension"))
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
            listOf("--sub-lang", "en"),
            FormatUtils.splitArgs("--sub-lang 'en'"),
        )
        assertEquals(emptyList<String>(), FormatUtils.splitArgs("   "))
    }
}
