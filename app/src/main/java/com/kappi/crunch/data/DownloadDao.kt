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
}
