package org.schabi.newpipe.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log

data class ModelDownloadProgress(
    val downloadId: Long,
    val status: Int,
    val progressPercent: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long
)

object ModelDownloaderManager {
    private const val TAG = "ModelDownloaderManager"
    const val PREF_LAST_DOWNLOAD_ID = "pref_key_last_model_download_id"

    fun startModelDownload(context: Context, modelInfo: LocalModelInfo): Long {
        val modelFile = UniversalModelRegistry.getModelFile(context, modelInfo)
        if (modelFile.exists()) {
            modelFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(modelInfo.downloadUrl)).apply {
            setTitle("BlackTube AI: ${modelInfo.name}")
            setDescription("Downloading ${modelInfo.name} (${modelInfo.fileSizeMB} MB) for offline summaries")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(modelFile))
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            
            // ⚡ MAXIMUM SPEED OPTIMIZATIONS:
            addRequestHeader("User-Agent", "BlackTube-FastDownloader/1.1 (Android; HighSpeed-CDN)")
            addRequestHeader("Accept-Encoding", "identity")
            addRequestHeader("Connection", "keep-alive")
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putLong(PREF_LAST_DOWNLOAD_ID, downloadId).apply()
        
        Log.i(TAG, "Enqueued model download ID: $downloadId for ${modelInfo.name}")
        return downloadId
    }

    fun getDownloadProgress(context: Context, downloadId: Long): ModelDownloadProgress {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            val status = if (statusIdx != -1) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
            val downloaded = if (downloadedIdx != -1) cursor.getLong(downloadedIdx) else 0L
            val total = if (totalIdx != -1) cursor.getLong(totalIdx) else 0L

            val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
            cursor.close()
            return ModelDownloadProgress(downloadId, status, percent, downloaded, total)
        }
        cursor?.close()
        return ModelDownloadProgress(downloadId, DownloadManager.STATUS_FAILED, 0, 0, 0)
    }

    fun getDownloadStatus(context: Context, downloadId: Long): Int {
        return getDownloadProgress(context, downloadId).status
    }
}
