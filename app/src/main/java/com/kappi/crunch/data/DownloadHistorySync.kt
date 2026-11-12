package com.kappi.crunch.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.kappi.crunch.util.FileLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "DownloadHistorySync"

data class SyncResult(
    val restoredCount: Int = 0,
    val updatedCount: Int = 0,
    val removedCount: Int = 0,
    val totalFoundOnDisk: Int = 0,
    val hasFolderAccess: Boolean = false,
) {
    fun statusMessage(): String = when {
        restoredCount > 0 && removedCount > 0 ->
            "Restored $restoredCount and removed $removedCount missing entry${if (removedCount == 1) "" else " entries"}."
        restoredCount > 0 ->
            "Restored $restoredCount download${if (restoredCount == 1) "" else "s"} from storage."
        updatedCount > 0 && removedCount > 0 ->
            "Relinked $updatedCount and removed $removedCount missing."
        updatedCount > 0 ->
            "Relinked $updatedCount download${if (updatedCount == 1) "" else "s"}."
        removedCount > 0 ->
            "Removed $removedCount missing file${if (removedCount == 1) "" else "s"} from history."
        totalFoundOnDisk > 0 ->
            "Synced • All $totalFoundOnDisk file${if (totalFoundOnDisk == 1) "" else "s"} up to date."
        !hasFolderAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            "No visible files. Tap Link Folder to grant access to Downloads/Crunch."
        else ->
            "No media files found in Downloads/Crunch."
    }
}

/**
 * Automatically inspects the Crunch downloads directory, persisted SAF folder tree,
 * and MediaStore to recover and synchronize completed downloads with the Room database.
 */
object DownloadHistorySync {

    private val syncMutex = Mutex()

    internal val MEDIA_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "m4v", "mov", "avi", "ts"
    )

    internal val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "opus", "flac", "wav", "aac", "ogg"
    )

    internal fun isMediaExtension(ext: String): Boolean = ext.lowercase() in MEDIA_EXTENSIONS

    internal fun isAudioExtension(ext: String): Boolean = ext.lowercase() in AUDIO_EXTENSIONS

    internal fun generateFileDownloadId(fileName: String, length: Long): String =
        UUID.nameUUIDFromBytes("crunch_recovered_${fileName}_$length".toByteArray()).toString()

    internal fun generateMediaStoreDownloadId(fileName: String, size: Long): String =
        UUID.nameUUIDFromBytes("crunch_mediastore_${fileName}_$size".toByteArray()).toString()

    fun hasPersistedFolderAccess(context: Context): Boolean {
        val saved = UserPreferences(context).downloadsTreeUri
        if (saved.isBlank()) return false
        val uri = runCatching { Uri.parse(saved) }.getOrNull() ?: return false
        val stillValid = context.contentResolver.persistedUriPermissions.any { perm ->
            perm.uri == uri && perm.isReadPermission
        }
        if (!stillValid) {
            UserPreferences(context).downloadsTreeUri = ""
        }
        return stillValid
    }

    fun saveFolderAccess(context: Context, treeUri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: SecurityException) {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            UserPreferences(context).downloadsTreeUri = treeUri.toString()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist folder URI permission: ${e.message}", e)
            false
        }
    }

    fun getInitialFolderUri(): Uri {
        runCatching {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloads, "Crunch").mkdirs()
        }
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/Crunch"
        )
    }

    suspend fun sync(
        context: Context,
        dao: DownloadDao,
        removeMissing: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            try {
                val hasFolderAccess = hasPersistedFolderAccess(context)
                val completedItems = dao.getCompletedItems()
                val activeItems = dao.getAllActiveTitles().map { it.lowercase() }.toSet()

                val seenPaths = mutableSetOf<String>()
                val seenFileNames = mutableSetOf<String>()
                val missingCompletedByFileName = mutableMapOf<String, DownloadItem>()

                for (item in completedItems) {
                    val fileName = resolveItemFileName(context, item)
                    if (item.filePath.isNotBlank() && FileLocation.exists(context, item.filePath)) {
                        seenPaths.add(item.filePath)
                        if (fileName.isNotBlank()) {
                            seenFileNames.add(fileName.lowercase())
                        }
                    } else {
                        if (fileName.isNotBlank()) {
                            missingCompletedByFileName[fileName.lowercase()] = item
                        } else {
                            val fallbackKey = "${item.title.lowercase()}.${item.formatId.lowercase()}"
                            missingCompletedByFileName[fallbackKey] = item
                        }
                    }
                }

                val discoveredUniqueNames = mutableSetOf<String>()
                val itemsToInsert = mutableListOf<DownloadItem>()
                val itemsToUpdate = mutableListOf<Triple<String, String, Long>>()

                fun onFileDiscovered(
                    uriOrPath: String,
                    fileName: String,
                    size: Long,
                    modifiedMs: Long
                ) {
                    if (fileName.isBlank() || fileName.startsWith(".") || size <= 0) return
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (!isMediaExtension(ext)) return

                    val title = fileName.substringBeforeLast('.', fileName)
                    if (title.lowercase() in activeItems) return

                    val normalizedName = fileName.lowercase()
                    discoveredUniqueNames.add(normalizedName)

                    if (uriOrPath in seenPaths || normalizedName in seenFileNames) {
                        return
                    }

                    // If a previously completed DB record lost its path, relink it cleanly
                    val missingMatch = missingCompletedByFileName.remove(normalizedName)
                    if (missingMatch != null) {
                        itemsToUpdate.add(Triple(missingMatch.id, uriOrPath, size))
                        seenPaths.add(uriOrPath)
                        seenFileNames.add(normalizedName)
                        return
                    }

                    val isAudio = isAudioExtension(ext)
                    val ts = if (modifiedMs > 0) modifiedMs else System.currentTimeMillis()
                    val deterministicId = generateFileDownloadId(fileName, size)

                    itemsToInsert.add(
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
                            filePath = uriOrPath,
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
                    seenPaths.add(uriOrPath)
                    seenFileNames.add(normalizedName)
                }

                // 1. Scan persisted SAF tree URI (survives reinstalls & Scoped Storage restrictions)
                if (hasFolderAccess) {
                    val treeUriStr = UserPreferences(context).downloadsTreeUri
                    runCatching { Uri.parse(treeUriStr) }.getOrNull()?.let { treeUri ->
                        querySafTree(context, treeUri, ::onFileDiscovered)
                    }
                }

                // 2. Query MediaStore (Downloads, Video, Audio) on Android 10+ (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryMediaStoreCollection(
                        context,
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        hasPendingColumn = true,
                        onDiscovered = ::onFileDiscovered
                    )
                    queryMediaStoreCollection(
                        context,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        hasPendingColumn = true,
                        onDiscovered = ::onFileDiscovered
                    )
                    queryMediaStoreCollection(
                        context,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        hasPendingColumn = true,
                        onDiscovered = ::onFileDiscovered
                    )
                }

                // 3. Scan Public Downloads/Crunch directory directly (Legacy storage + accessible files)
                @Suppress("DEPRECATION")
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val publicCrunchDir = File(publicDownloads, "Crunch")
                if (publicCrunchDir.exists() && publicCrunchDir.isDirectory) {
                    val files = publicCrunchDir.listFiles()
                    val toMediaScan = mutableListOf<String>()
                    files?.forEach { file ->
                        if (file.isFile && file.length() > 0 && isMediaExtension(file.extension)) {
                            val beforeCount = itemsToInsert.size + itemsToUpdate.size
                            onFileDiscovered(file.absolutePath, file.name, file.length(), file.lastModified())
                            if (itemsToInsert.size + itemsToUpdate.size > beforeCount) {
                                toMediaScan.add(file.absolutePath)
                            }
                        }
                    }
                    if (toMediaScan.isNotEmpty()) {
                        runCatching {
                            MediaScannerConnection.scanFile(context, toMediaScan.toTypedArray(), null, null)
                        }
                    }
                }

                // 4. Scan App Staging Crunch directory (fallback files not yet moved)
                val stagingDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Crunch") }
                if (stagingDir != null && stagingDir.exists() && stagingDir.isDirectory) {
                    stagingDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.length() > 0 && isMediaExtension(file.extension)) {
                            onFileDiscovered(file.absolutePath, file.name, file.length(), file.lastModified())
                        }
                    }
                }

                if (itemsToUpdate.isNotEmpty()) {
                    for ((id, newPath, newSize) in itemsToUpdate) {
                        dao.updateFileLocation(id, newPath, newSize)
                    }
                }

                if (itemsToInsert.isNotEmpty()) {
                    dao.insertAll(itemsToInsert)
                }

                var removedCount = 0
                if (removeMissing && missingCompletedByFileName.isNotEmpty()) {
                    val idsToDelete = missingCompletedByFileName.values
                        .filter { item ->
                            item.filePath.isBlank() ||
                                FileLocation.isContentUri(item.filePath) ||
                                hasFolderAccess ||
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                                item.filePath.contains("/Android/data/")
                        }
                        .map { it.id }

                    if (idsToDelete.isNotEmpty()) {
                        dao.deleteByIds(idsToDelete)
                        removedCount = idsToDelete.size
                    }
                }

                val result = SyncResult(
                    restoredCount = itemsToInsert.size,
                    updatedCount = itemsToUpdate.size,
                    removedCount = removedCount,
                    totalFoundOnDisk = discoveredUniqueNames.size,
                    hasFolderAccess = hasFolderAccess,
                )
                Log.i(TAG, "Sync completed: $result")
                result
            } catch (t: Throwable) {
                Log.w(TAG, "Error synchronizing download history from storage: ${t.message}", t)
                SyncResult(hasFolderAccess = hasPersistedFolderAccess(context))
            }
        }
    }

    private fun resolveItemFileName(context: Context, item: DownloadItem): String {
        val path = item.filePath
        if (path.isNotBlank() && !FileLocation.isContentUri(path)) {
            return File(path).name
        }
        if (path.isNotBlank() && FileLocation.isContentUri(path)) {
            runCatching {
                val uri = Uri.parse(path)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = c.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            }
        }
        val ext = item.formatId.takeIf { it.isNotBlank() && isMediaExtension(it) } ?: ""
        return if (ext.isNotEmpty() && item.title.isNotBlank()) "${item.title}.$ext" else ""
    }

    private fun querySafTree(
        context: Context,
        treeUri: Uri,
        onDiscovered: (uriOrPath: String, fileName: String, size: Long, modifiedMs: Long) -> Unit,
        parentDocId: String = DocumentsContract.getTreeDocumentId(treeUri),
        depth: Int = 0
    ) {
        if (depth > 1) return
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val docId = if (idCol >= 0) cursor.getString(idCol) else continue
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else continue
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else ""

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth == 0 && name.equals("Crunch", ignoreCase = true)) {
                            querySafTree(context, treeUri, onDiscovered, docId, depth + 1)
                        }
                        continue
                    }

                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val modified = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()

                    onDiscovered(docUri, name, size, modified)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SAF tree query failed for $treeUri: ${e.message}")
        }
    }

    private fun queryMediaStoreCollection(
        context: Context,
        collection: Uri,
        hasPendingColumn: Boolean,
        onDiscovered: (uriOrPath: String, fileName: String, size: Long, modifiedMs: Long) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                if (hasPendingColumn) add(MediaStore.MediaColumns.IS_PENDING)
            }.toTypedArray()

            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Crunch%")

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pendingCol = if (hasPendingColumn) cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING) else -1

                while (cursor.moveToNext()) {
                    if (pendingCol >= 0 && cursor.getInt(pendingCol) == 1) continue

                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val dateModifiedMs = cursor.getLong(dateCol) * 1000L

                    val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()
                    onDiscovered(contentUri, name, size, dateModifiedMs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed on $collection: ${e.message}")
        }
    }

    internal fun processDiscoveredFile(
        file: File,
        existingPaths: Set<String>,
        existingTitles: Set<String>,
        outList: MutableList<DownloadItem>,
        existingFileNames: MutableSet<String> = mutableSetOf(),
    ) {
        if (!file.isFile || file.length() <= 0) return
        val ext = file.extension.lowercase()
        if (!isMediaExtension(ext)) return

        val path = file.absolutePath
        val title = file.nameWithoutExtension
        val normalizedFileName = file.name.lowercase()

        if (path in existingPaths || title in existingTitles || normalizedFileName in existingFileNames) return

        val isAudio = isAudioExtension(ext)
        val ts = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        val deterministicId = generateFileDownloadId(file.name, file.length())

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
        existingFileNames.add(normalizedFileName)
    }
}
