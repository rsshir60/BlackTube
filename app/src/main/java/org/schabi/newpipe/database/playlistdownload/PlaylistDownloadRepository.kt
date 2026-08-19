package org.schabi.newpipe.database.playlistdownload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class PlaylistDownloadRepository(
    private val dao: PlaylistDownloadDao
) {

    fun observePlaylist(playlistId: String): Flow<List<PlaylistDownloadEntity>> {
        return dao.observeByPlaylist(playlistId)
    }

    fun observeBatch(batchId: String): Flow<List<PlaylistDownloadEntity>> {
        return dao.observeByBatch(batchId)
    }

    fun observeActiveDownloads(): Flow<List<PlaylistDownloadEntity>> {
        return dao.observeByStatus(DownloadStatus.DOWNLOADING)
    }

    suspend fun enqueuePlaylist(
        playlistId: String,
        playlistName: String,
        videos: List<PlaylistVideoInfo>,
        quality: String,
        format: String
    ): String = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()

        val entities = videos.mapIndexed { index, video ->
            PlaylistDownloadEntity(
                playlistId = playlistId,
                playlistName = playlistName,
                videoId = video.id,
                videoTitle = video.title,
                videoUrl = video.url,
                videoDuration = video.duration,
                thumbnailUrl = video.thumbnailUrl,
                quality = quality,
                format = format,
                positionInPlaylist = index,
                status = DownloadStatus.QUEUED,
                batchId = batchId
            )
        }

        dao.insertAll(entities)
        batchId
    }

    suspend fun getNextPendingItem(): PlaylistDownloadEntity? = withContext(Dispatchers.IO) {
        dao.getNextItem(listOf(DownloadStatus.QUEUED, DownloadStatus.PENDING))
    }

    suspend fun updateProgress(id: String, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        withContext(Dispatchers.IO) {
            dao.updateProgress(id, progress, downloadedBytes, totalBytes)
        }
    }

    suspend fun updateStatus(id: String, status: DownloadStatus) {
        withContext(Dispatchers.IO) {
            dao.updateStatus(id, status)
        }
    }

    suspend fun markCompleted(id: String, filePath: String) {
        withContext(Dispatchers.IO) {
            dao.markCompleted(id, DownloadStatus.COMPLETED, filePath)
        }
    }

    suspend fun markFailed(id: String, errorMessage: String) {
        withContext(Dispatchers.IO) {
            dao.markFailed(id, DownloadStatus.FAILED, errorMessage)
        }
    }

    suspend fun pauseBatch(batchId: String) = withContext(Dispatchers.IO) {
        dao.pauseBatch(batchId)
    }

    suspend fun resumeBatch(batchId: String) = withContext(Dispatchers.IO) {
        dao.resumeBatch(batchId)
    }

    suspend fun cancelBatch(batchId: String) = withContext(Dispatchers.IO) {
        dao.cancelBatch(batchId)
    }

    suspend fun getBatchProgress(batchId: String): BatchProgress = withContext(Dispatchers.IO) {
        val completed = dao.getCompletedCount(batchId)
        val total = dao.getTotalCount(batchId)
        val downloadedBytes = dao.getDownloadedBytes(batchId) ?: 0L
        val totalBytes = dao.getTotalBytes(batchId) ?: 0L

        BatchProgress(
            completedCount = completed,
            totalCount = total,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            overallPercent = if (total > 0) (completed * 100) / total else 0
        )
    }

    suspend fun isVideoAlreadyDownloaded(videoId: String): Boolean = withContext(Dispatchers.IO) {
        dao.isVideoDownloaded(videoId)
    }

    suspend fun removeItem(id: String) = withContext(Dispatchers.IO) {
        val item = dao.getNextItem(listOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED))
        if (item != null) {
            dao.delete(item)
        }
    }
}

data class BatchProgress(
    val completedCount: Int,
    val totalCount: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val overallPercent: Int
)

data class PlaylistVideoInfo(
    val id: String,
    val title: String,
    val url: String,
    val duration: Long,
    val thumbnailUrl: String
)
