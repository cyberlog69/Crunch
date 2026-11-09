package com.kappi.crunch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadItemTest {

    @Test
    fun defaultValues_areSensible() {
        val item = DownloadItem(
            id = "test-uuid",
            url = "https://example.com/watch?v=123",
            title = "Sample Video"
        )

        assertEquals("test-uuid", item.id)
        assertEquals("https://example.com/watch?v=123", item.url)
        assertEquals("Sample Video", item.title)
        assertEquals(DownloadType.VIDEO, item.downloadType)
        assertEquals(DownloadStatus.QUEUED, item.status)
        assertEquals(0f, item.progress, 0.001f)
        assertEquals("", item.filePath)
        assertEquals(0L, item.fileSize)
        assertEquals("", item.errorMessage)
        assertFalse(item.useExternalDownloader)
    }

    @Test
    fun stateTransitions_viaCopy() {
        val queued = DownloadItem(
            id = "test-id",
            url = "https://example.com",
            title = "Test Video"
        )
        assertEquals(DownloadStatus.QUEUED, queued.status)

        val downloading = queued.copy(
            status = DownloadStatus.DOWNLOADING,
            progress = 0.45f,
            speed = "5.2 MiB/s",
            eta = "00:15"
        )
        assertEquals(DownloadStatus.DOWNLOADING, downloading.status)
        assertEquals(0.45f, downloading.progress, 0.001f)
        assertEquals("5.2 MiB/s", downloading.speed)
        assertEquals("00:15", downloading.eta)

        val completed = downloading.copy(
            status = DownloadStatus.COMPLETED,
            progress = 1.0f,
            filePath = "/storage/emulated/0/Download/Crunch/Test Video.mp4",
            fileSize = 10485760L,
            completedAt = 1700000000L
        )
        assertEquals(DownloadStatus.COMPLETED, completed.status)
        assertEquals(1.0f, completed.progress, 0.001f)
        assertEquals(10485760L, completed.fileSize)

        val failed = queued.copy(
            status = DownloadStatus.FAILED,
            errorMessage = "HTTP 404: Not Found"
        )
        assertEquals(DownloadStatus.FAILED, failed.status)
        assertEquals("HTTP 404: Not Found", failed.errorMessage)

        val cancelled = downloading.copy(
            status = DownloadStatus.CANCELLED
        )
        assertEquals(DownloadStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun downloadTypeEnum_hasExpectedValues() {
        val types = DownloadType.values()
        assertEquals(2, types.size)
        assertTrue(types.contains(DownloadType.VIDEO))
        assertTrue(types.contains(DownloadType.AUDIO))
    }

    @Test
    fun downloadStatusEnum_hasExpectedValues() {
        val statuses = DownloadStatus.values()
        assertEquals(7, statuses.size)
        assertTrue(statuses.contains(DownloadStatus.QUEUED))
        assertTrue(statuses.contains(DownloadStatus.FETCHING))
        assertTrue(statuses.contains(DownloadStatus.DOWNLOADING))
        assertTrue(statuses.contains(DownloadStatus.PROCESSING))
        assertTrue(statuses.contains(DownloadStatus.COMPLETED))
        assertTrue(statuses.contains(DownloadStatus.FAILED))
        assertTrue(statuses.contains(DownloadStatus.CANCELLED))
    }
}
