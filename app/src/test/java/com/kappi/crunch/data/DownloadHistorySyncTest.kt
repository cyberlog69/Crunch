package com.kappi.crunch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadHistorySyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun isMediaExtension_recognizesAllSupportedFormats() {
        val validExtensions = listOf(
            "mp4", "mkv", "webm", "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "m4v", "mov", "avi", "ts",
            "MP4", "MKV", "MP3", "FLAC", "OpUs"
        )
        for (ext in validExtensions) {
            assertTrue("Expected $ext to be recognized as media", DownloadHistorySync.isMediaExtension(ext))
        }

        val invalidExtensions = listOf(
            "txt", "part", "ytdl", "tmp", "exe", "apk", "json", "xml", "zip", "", " "
        )
        for (ext in invalidExtensions) {
            assertFalse("Expected $ext not to be recognized as media", DownloadHistorySync.isMediaExtension(ext))
        }
    }

    @Test
    fun isAudioExtension_correctlyDistinguishesAudioFromVideo() {
        val audioExts = listOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "MP3", "OPUS")
        for (ext in audioExts) {
            assertTrue("Expected $ext to be identified as audio", DownloadHistorySync.isAudioExtension(ext))
        }

        val videoExts = listOf("mp4", "mkv", "webm", "m4v", "mov", "avi", "ts")
        for (ext in videoExts) {
            assertFalse("Expected $ext not to be identified as audio", DownloadHistorySync.isAudioExtension(ext))
        }
    }

    @Test
    fun deterministicIds_areReproducible() {
        val id1 = DownloadHistorySync.generateFileDownloadId("song.mp3", 1024L)
        val id2 = DownloadHistorySync.generateFileDownloadId("song.mp3", 1024L)
        val idDiffSize = DownloadHistorySync.generateFileDownloadId("song.mp3", 2048L)
        val idDiffName = DownloadHistorySync.generateFileDownloadId("video.mp4", 1024L)

        assertEquals("Same name and size must produce identical IDs", id1, id2)
        assertFalse("Different sizes must produce different IDs", id1 == idDiffSize)
        assertFalse("Different names must produce different IDs", id1 == idDiffName)

        val msId1 = DownloadHistorySync.generateMediaStoreDownloadId("clip.mp4", 5000L)
        val msId2 = DownloadHistorySync.generateMediaStoreDownloadId("clip.mp4", 5000L)
        assertEquals("MediaStore IDs must be reproducible", msId1, msId2)
        assertFalse("File and MediaStore IDs should have different prefixes", id1 == msId1)
    }

    @Test
    fun processDiscoveredFile_skipsNonExistentOrEmptyFiles() {
        val outList = mutableListOf<DownloadItem>()
        val nonExistent = File(tempFolder.root, "does_not_exist.mp4")
        DownloadHistorySync.processDiscoveredFile(nonExistent, emptySet(), emptySet(), outList)
        assertTrue("Non-existent files must be ignored", outList.isEmpty())

        val emptyFile = tempFolder.newFile("empty.mp4")
        DownloadHistorySync.processDiscoveredFile(emptyFile, emptySet(), emptySet(), outList)
        assertTrue("0-byte files must be ignored", outList.isEmpty())
    }

    @Test
    fun processDiscoveredFile_skipsNonMediaFiles() {
        val outList = mutableListOf<DownloadItem>()
        val textFile = tempFolder.newFile("notes.txt").apply { writeText("Sample notes") }
        val partFile = tempFolder.newFile("video.mp4.part").apply { writeText("partial data") }

        DownloadHistorySync.processDiscoveredFile(textFile, emptySet(), emptySet(), outList)
        DownloadHistorySync.processDiscoveredFile(partFile, emptySet(), emptySet(), outList)
        assertTrue("Non-media files must be skipped", outList.isEmpty())
    }

    @Test
    fun processDiscoveredFile_skipsDuplicatesByPathOrTitle() {
        val outList = mutableListOf<DownloadItem>()
        val mediaFile = tempFolder.newFile("My Video.mp4").apply { writeBytes(ByteArray(100)) }

        // Test duplicate path
        DownloadHistorySync.processDiscoveredFile(
            mediaFile,
            existingPaths = setOf(mediaFile.absolutePath),
            existingTitles = emptySet(),
            outList = outList
        )
        assertTrue("Files matching existing paths must be skipped", outList.isEmpty())

        // Test duplicate title
        DownloadHistorySync.processDiscoveredFile(
            mediaFile,
            existingPaths = emptySet(),
            existingTitles = setOf("My Video"),
            outList = outList
        )
        assertTrue("Files matching existing titles must be skipped", outList.isEmpty())
    }

    @Test
    fun processDiscoveredFile_successfullyRestoresVideoAndAudio() {
        val outList = mutableListOf<DownloadItem>()
        val videoFile = tempFolder.newFile("Awesome Documentary.mkv").apply {
            writeBytes(ByteArray(5000))
        }
        val audioFile = tempFolder.newFile("Synthwave Track.opus").apply {
            writeBytes(ByteArray(3000))
        }

        DownloadHistorySync.processDiscoveredFile(videoFile, emptySet(), emptySet(), outList)
        DownloadHistorySync.processDiscoveredFile(audioFile, emptySet(), emptySet(), outList)

        assertEquals("Both files should be recovered", 2, outList.size)

        val recoveredVideo = outList.first { it.title == "Awesome Documentary" }
        assertEquals(DownloadType.VIDEO, recoveredVideo.downloadType)
        assertEquals(DownloadStatus.COMPLETED, recoveredVideo.status)
        assertEquals(1f, recoveredVideo.progress, 0.001f)
        assertEquals(5000L, recoveredVideo.fileSize)
        assertEquals("mkv", recoveredVideo.formatId)
        assertEquals("MKV", recoveredVideo.formatLabel)
        assertEquals(videoFile.absolutePath, recoveredVideo.filePath)

        val recoveredAudio = outList.first { it.title == "Synthwave Track" }
        assertEquals(DownloadType.AUDIO, recoveredAudio.downloadType)
        assertEquals(DownloadStatus.COMPLETED, recoveredAudio.status)
        assertEquals(1f, recoveredAudio.progress, 0.001f)
        assertEquals(3000L, recoveredAudio.fileSize)
        assertEquals("opus", recoveredAudio.formatId)
        assertEquals("opus", recoveredAudio.audioFormat)
        assertEquals(audioFile.absolutePath, recoveredAudio.filePath)
    }

    @Test
    fun processDiscoveredFile_deduplicatesByFileNameAcrossSources() {
        val outList = mutableListOf<DownloadItem>()
        val existingFileNames = mutableSetOf<String>()
        val file1 = tempFolder.newFile("Duplicate Clip.mp4").apply { writeBytes(ByteArray(2048)) }

        DownloadHistorySync.processDiscoveredFile(
            file1,
            existingPaths = emptySet(),
            existingTitles = emptySet(),
            outList = outList,
            existingFileNames = existingFileNames
        )
        // Simulate second discovery source finding the same filename (case-insensitive)
        DownloadHistorySync.processDiscoveredFile(
            file1,
            existingPaths = emptySet(),
            existingTitles = emptySet(),
            outList = outList,
            existingFileNames = existingFileNames
        )

        assertEquals("Same filename discovered twice must produce only 1 item", 1, outList.size)
        assertTrue(existingFileNames.contains("duplicate clip.mp4"))
    }

    @Test
    fun syncResult_statusMessagesAreInformative() {
        val restored = SyncResult(restoredCount = 3, totalFoundOnDisk = 3, hasFolderAccess = true)
        assertTrue(restored.statusMessage().contains("Restored 3"))

        val cleaned = SyncResult(removedCount = 2, totalFoundOnDisk = 1, hasFolderAccess = true)
        assertTrue(cleaned.statusMessage().contains("Removed 2"))

        val upToDate = SyncResult(totalFoundOnDisk = 5, hasFolderAccess = true)
        assertTrue(upToDate.statusMessage().contains("All 5"))
    }
}

