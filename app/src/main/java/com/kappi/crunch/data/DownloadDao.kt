package com.kappi.crunch.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','FETCHING','DOWNLOADING','PROCESSING') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELLED') ORDER BY completedAt DESC")
    fun observeCompleted(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem)

    @Update
    suspend fun update(item: DownloadItem)

    @Delete
    suspend fun delete(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE downloads SET status = :status, errorMessage = :msg WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, msg: String = "")

    @Query("UPDATE downloads SET progress = :progress, speed = :speed, eta = :eta WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, speed: String, eta: String)

    @Query("UPDATE downloads SET status = 'COMPLETED', filePath = :path, fileSize = :size, completedAt = :ts WHERE id = :id")
    suspend fun markCompleted(id: String, path: String, size: Long, ts: Long = System.currentTimeMillis())

    @Query("SELECT * FROM downloads WHERE playlistId = :playlistId ORDER BY playlistIndex ASC")
    fun observePlaylist(playlistId: String): Flow<List<DownloadItem>>

    @Query("SELECT filePath FROM downloads WHERE filePath IS NOT NULL AND filePath != ''")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT title FROM downloads")
    suspend fun getAllTitles(): List<String>

    @Query("SELECT title FROM downloads WHERE status IN ('QUEUED','FETCHING','DOWNLOADING','PROCESSING')")
    suspend fun getAllActiveTitles(): List<String>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedItems(): List<DownloadItem>

    @Query("UPDATE downloads SET filePath = :path, fileSize = :size WHERE id = :id")
    suspend fun updateFileLocation(id: String, path: String, size: Long)

    @Query("DELETE FROM downloads WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<DownloadItem>)
}
