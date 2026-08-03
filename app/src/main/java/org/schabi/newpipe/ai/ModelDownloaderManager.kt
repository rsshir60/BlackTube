package org.schabi.newpipe.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log

object ModelDownloaderManager {
    private const val TAG = "ModelDownloaderManager"

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
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        Log.i(TAG, "Enqueued model download ID: $downloadId for ${modelInfo.name}")
        return downloadId
    }

    fun getDownloadStatus(context: Context, downloadId: Long): Int {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex != -1) {
                val status = cursor.getInt(statusIndex)
                cursor.close()
                return status
            }
            cursor.close()
        }
        return DownloadManager.STATUS_FAILED
    }
}
