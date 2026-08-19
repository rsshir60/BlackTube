package org.schabi.newpipe.download.playlist

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.database.playlistdownload.*
import java.util.concurrent.TimeUnit

class PlaylistDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "PlaylistDownloadWorker"
        const val WORK_NAME_PREFIX = "playlist_download_"
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val MAX_RETRY_COUNT = 3

        fun enqueue(
            context: Context,
            batchId: String,
            playlistId: String
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val inputData = workDataOf(
                KEY_BATCH_ID to batchId,
                KEY_PLAYLIST_ID to playlistId
            )

            val request = OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + batchId,
                ExistingWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Enqueued playlist download: batchId=$batchId")
        }

        fun cancel(context: Context, batchId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_PREFIX + batchId)
            Log.d(TAG, "Cancelled playlist download: batchId=$batchId")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return@withContext Result.failure()
        val playlistId = inputData.getString(KEY_PLAYLIST_ID) ?: return@withContext Result.failure()

        Log.d(TAG, "Starting playlist download: batchId=$batchId")

        try {
            val repository = getRepository()

            // Start foreground service for progress
            try {
                PlaylistDownloadService.start(applicationContext, batchId, playlistId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service", e)
            }

            var processedCount = 0
            var failedCount = 0

            while (true) {
                if (isStopped) {
                    Log.d(TAG, "Worker stopped, exiting loop")
                    break
                }

                val nextItem = repository.getNextPendingItem() ?: break

                if (nextItem.status == DownloadStatus.COMPLETED) {
                    continue
                }

                if (nextItem.retryCount >= MAX_RETRY_COUNT) {
                    Log.w(TAG, "Max retries reached for: ${nextItem.videoTitle}")
                    failedCount++
                    continue
                }

                try {
                    repository.updateStatus(nextItem.id, DownloadStatus.DOWNLOADING)

                    // Mark completed in database
                    repository.markCompleted(nextItem.id, nextItem.videoUrl)
                    processedCount++

                    val progress = repository.getBatchProgress(batchId)
                    PlaylistDownloadService.updateProgress(applicationContext, progress)

                    Log.d(TAG, "Completed: ${nextItem.videoTitle} ($processedCount total)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to download: ${nextItem.videoTitle}", e)
                    repository.markFailed(nextItem.id, e.message ?: "Unknown error")
                    failedCount++

                    if (failedCount > 5) {
                        Log.e(TAG, "Too many failures, stopping batch")
                        break
                    }
                }
            }

            try {
                PlaylistDownloadService.stop(applicationContext)
            } catch (ignored: Exception) { }

            Log.d(TAG, "Batch completed: $processedCount succeeded, $failedCount failed")
            return@withContext if (failedCount == 0) Result.success() else Result.failure()

        } catch (e: Exception) {
            Log.e(TAG, "Worker failed with exception", e)
            try {
                PlaylistDownloadService.stop(applicationContext)
            } catch (ignored: Exception) { }
            return@withContext Result.retry()
        }
    }

    private fun getRepository(): PlaylistDownloadRepository {
        val db = PlaylistDownloadDatabase.getInstance(applicationContext)
        return PlaylistDownloadRepository(db.playlistDownloadDao())
    }
}
