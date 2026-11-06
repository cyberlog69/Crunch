package com.kappi.crunch.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "DownloadHistorySync"

/**
 * Automatically inspects the Crunch downloads directory and MediaStore
 * to recover and preserve completed downloads into the Room database.
 *
 * This ensures that when the user updates the app, clears cache, or reinstalls,
 * their existing downloaded media files in Downloads/Crunch are seamlessly
 * restored to the Download History.
 */
object DownloadHistorySync {

    private val MEDIA_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "m4v", "mov", "avi", "ts"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg"
    )

    suspend fun sync(context: Context, dao: DownloadDao) = withContext(Dispatchers.IO) {
        try {
            val existingPaths = dao.getAllFilePaths().toSet()
            val existingTitles = dao.getAllTitles().toSet()
            val itemsToInsert = mutableListOf<DownloadItem>()

            // 1. Scan Public Downloads/Crunch directory
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicCrunchDir = File(publicDownloads, "Crunch")
            if (publicCrunchDir.exists() && publicCrunchDir.isDirectory) {
                publicCrunchDir.listFiles()?.forEach { file ->
                    processDiscoveredFile(file, existingPaths, existingTitles, itemsToInsert)
                }
            }

            // 2. Scan App Staging Crunch directory
            val stagingDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Crunch") }
            if (stagingDir != null && stagingDir.exists() && stagingDir.isDirectory) {
                stagingDir.listFiles()?.forEach { file ->
                    processDiscoveredFile(file, existingPaths, existingTitles, itemsToInsert)
                }
            }

            // 3. Query MediaStore on Android 10+ (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                queryMediaStore(context, existingPaths, existingTitles, itemsToInsert)
            }

            if (itemsToInsert.isNotEmpty()) {
                dao.insertAll(itemsToInsert)
                Log.i(TAG, "Successfully restored ${itemsToInsert.size} historical downloads into database.")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error synchronizing download history from storage: ${t.message}", t)
        }
    }

    private fun processDiscoveredFile(
        file: File,
        existingPaths: Set<String>,
        existingTitles: Set<String>,
        outList: MutableList<DownloadItem>
    ) {
        if (!file.isFile || file.length() <= 0) return
        val ext = file.extension.lowercase()
        if (ext !in MEDIA_EXTENSIONS) return

        val path = file.absolutePath
        val title = file.nameWithoutExtension

        if (path in existingPaths || title in existingTitles) return

        val isAudio = ext in AUDIO_EXTENSIONS
        val ts = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        val deterministicId = UUID.nameUUIDFromBytes("crunch_recovered_${file.name}_${file.length()}".toByteArray()).toString()

        outList.add(
            DownloadItem(
                id = deterministicId,
                url = "",
                title = title,
                uploader = "",
                thumbnailUrl = "",
                duration = 0L,
                downloadType = if (isAudio) DownloadType.AUDIO else DownloadType.VIDEO,
                formatId = ext,
                formatLabel = ext.uppercase(),
                audioFormat = if (isAudio) ext else "mp3",
                subtitleLang = "",
                embedSubtitles = false,
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                speed = "",
                eta = "",
                filePath = path,
                fileSize = file.length(),
                errorMessage = "",
                createdAt = ts,
                completedAt = ts,
                playlistId = "",
                playlistIndex = 0,
                workerId = "",
                useExternalDownloader = false,
                customTemplate = "",
            )
        )
    }

    private fun queryMediaStore(
        context: Context,
        existingPaths: Set<String>,
        existingTitles: Set<String>,
        outList: MutableList<DownloadItem>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.SIZE,
                MediaStore.Downloads.DATE_MODIFIED,
                MediaStore.Downloads.MIME_TYPE,
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Crunch%")

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000L

                    if (size <= 0) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in MEDIA_EXTENSIONS) continue

                    val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()
                    val title = name.substringBeforeLast('.')

                    if (contentUri in existingPaths || title in existingTitles) continue

                    val isAudio = ext in AUDIO_EXTENSIONS
                    val ts = if (dateModified > 0) dateModified else System.currentTimeMillis()
                    val deterministicId = UUID.nameUUIDFromBytes("crunch_mediastore_${name}_$size".toByteArray()).toString()

                    outList.add(
                        DownloadItem(
                            id = deterministicId,
                            url = "",
                            title = title,
                            uploader = "",
                            thumbnailUrl = "",
                            duration = 0L,
                            downloadType = if (isAudio) DownloadType.AUDIO else DownloadType.VIDEO,
                            formatId = ext,
                            formatLabel = ext.uppercase(),
                            audioFormat = if (isAudio) ext else "mp3",
                            subtitleLang = "",
                            embedSubtitles = false,
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            speed = "",
                            eta = "",
                            filePath = contentUri,
                            fileSize = size,
                            errorMessage = "",
                            createdAt = ts,
                            completedAt = ts,
                            playlistId = "",
                            playlistIndex = 0,
                            workerId = "",
                            useExternalDownloader = false,
                            customTemplate = "",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query for Crunch downloads failed: ${e.message}")
        }
    }
}

