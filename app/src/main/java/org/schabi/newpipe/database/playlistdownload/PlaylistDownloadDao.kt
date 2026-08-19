package org.schabi.newpipe.database.playlistdownload

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistDownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistDownloadEntity>)

    @Update
    suspend fun update(item: PlaylistDownloadEntity)

    @Delete
    suspend fun delete(item: PlaylistDownloadEntity)

    @Query("DELETE FROM playlist_downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM playlist_downloads WHERE batch_id = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("SELECT * FROM playlist_downloads WHERE playlist_id = :playlistId ORDER BY position_in_playlist ASC")
    fun observeByPlaylist(playlistId: String): Flow<List<PlaylistDownloadEntity>>

    @Query("SELECT * FROM playlist_downloads WHERE batch_id = :batchId ORDER BY position_in_playlist ASC")
    fun observeByBatch(batchId: String): Flow<List<PlaylistDownloadEntity>>

    @Query("SELECT * FROM playlist_downloads WHERE status = :status ORDER BY position_in_playlist ASC")
    fun observeByStatus(status: DownloadStatus): Flow<List<PlaylistDownloadEntity>>

    @Query("SELECT * FROM playlist_downloads WHERE status IN (:statuses) ORDER BY position_in_playlist ASC LIMIT 1")
    suspend fun getNextItem(statuses: List<DownloadStatus>): PlaylistDownloadEntity?

    @Query("UPDATE playlist_downloads SET status = :status, updated_at = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_downloads SET progress_percent = :progress, downloaded_bytes = :downloadedBytes, total_bytes = :totalBytes, updated_at = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, downloadedBytes: Long, totalBytes: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_downloads SET status = :status, error_message = :errorMessage, retry_count = retry_count + 1, updated_at = :timestamp WHERE id = :id")
    suspend fun markFailed(id: String, status: DownloadStatus, errorMessage: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_downloads SET status = :status, completed_at = :timestamp, file_path = :filePath WHERE id = :id")
    suspend fun markCompleted(id: String, status: DownloadStatus, filePath: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE playlist_downloads SET status = 'PAUSED' WHERE batch_id = :batchId AND status IN ('PENDING', 'QUEUED', 'DOWNLOADING')")
    suspend fun pauseBatch(batchId: String)

    @Query("UPDATE playlist_downloads SET status = 'QUEUED' WHERE batch_id = :batchId AND status = 'PAUSED'")
    suspend fun resumeBatch(batchId: String)

    @Query("UPDATE playlist_downloads SET status = 'CANCELLED' WHERE batch_id = :batchId AND status NOT IN ('COMPLETED')")
    suspend fun cancelBatch(batchId: String)

    @Query("SELECT COUNT(*) FROM playlist_downloads WHERE batch_id = :batchId AND status = 'COMPLETED'")
    suspend fun getCompletedCount(batchId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_downloads WHERE batch_id = :batchId")
    suspend fun getTotalCount(batchId: String): Int

    @Query("SELECT SUM(downloaded_bytes) FROM playlist_downloads WHERE batch_id = :batchId")
    suspend fun getDownloadedBytes(batchId: String): Long?

    @Query("SELECT SUM(total_bytes) FROM playlist_downloads WHERE batch_id = :batchId")
    suspend fun getTotalBytes(batchId: String): Long?

    @Query("SELECT DISTINCT playlist_id FROM playlist_downloads WHERE status IN ('PENDING', 'QUEUED', 'DOWNLOADING')")
    suspend fun getActivePlaylists(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_downloads WHERE video_id = :videoId AND status = 'COMPLETED')")
    suspend fun isVideoDownloaded(videoId: String): Boolean
}
