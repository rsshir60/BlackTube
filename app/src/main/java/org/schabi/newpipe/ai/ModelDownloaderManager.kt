package org.schabi.newpipe.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

data class ModelDownloadProgress(
    val downloadId: Long = 0L,
    val status: Int = 0,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val error: String? = null,
    val isCompleted: Boolean = false,
    val isDownloading: Boolean = false
)

object ModelDownloaderManager {
    private const val TAG = "ModelDownloaderManager"
    private const val BUFFER_SIZE = 16384 // 16KB high-throughput buffer
    private const val MAX_RETRIES = 5
    private const val RETRY_DELAY_MS = 2000L

    private const val NOTIFICATION_ID = 9002
    private const val CHANNEL_ID = "ai_model_download_channel"
    private const val CHANNEL_NAME = "AI Model Downloads"

    const val STATUS_PENDING = 1
    const val STATUS_RUNNING = 2
    const val STATUS_PAUSED = 4
    const val STATUS_SUCCESSFUL = 8
    const val STATUS_FAILED = 16

    private val _downloadProgress = MutableStateFlow(ModelDownloadProgress())
    val downloadProgress: StateFlow<ModelDownloadProgress> = _downloadProgress.asStateFlow()

    private var downloadJob: Job? = null
    private var isCancelled = false

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 BlackTube/1.3.3")
                .header("Accept", "*/*")
                .build()
            chain.proceed(request)
        }
        .build()

    @JvmStatic
    fun startModelDownload(context: Context, modelInfo: LocalModelInfo): Long {
        cancelDownload(context)
        val downloadId = System.currentTimeMillis()
        val appContext = context.applicationContext

        createNotificationChannel(appContext)
        showNotification(
            context = appContext,
            title = "Downloading ${modelInfo.name}",
            content = "Connecting to high-speed AI CDN...",
            progress = 0,
            ongoing = true
        )

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                isCancelled = false
                _downloadProgress.value = ModelDownloadProgress(
                    downloadId = downloadId,
                    status = STATUS_RUNNING,
                    isDownloading = true,
                    progressPercent = 0
                )

                val rawUrl = modelInfo.downloadUrl
                val finalUrl = ensureHuggingFaceDownloadParam(rawUrl)
                Log.d(TAG, "Starting streaming model download: $finalUrl")

                val targetFile = UniversalModelRegistry.getModelFile(appContext, modelInfo)
                targetFile.parentFile?.mkdirs()

                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    if (isCancelled) {
                        Log.d(TAG, "Download cancelled by user")
                        updateError(appContext, downloadId, "Download cancelled")
                        return@launch
                    }

                    try {
                        Log.d(TAG, "Download attempt $attempt of $MAX_RETRIES")
                        executeStreamingDownload(appContext, finalUrl, targetFile, downloadId, modelInfo.name)

                        _downloadProgress.value = _downloadProgress.value.copy(
                            status = STATUS_SUCCESSFUL,
                            isDownloading = false,
                            isCompleted = true,
                            progressPercent = 100,
                            error = null
                        )

                        Log.d(TAG, "Download completed: ${targetFile.absolutePath} (${targetFile.length() / (1024 * 1024)} MB)")
                        showNotification(
                            context = appContext,
                            title = "✅ ${modelInfo.name} Downloaded",
                            content = "Offline AI model is ready for instant private video summaries.",
                            progress = 100,
                            ongoing = false
                        )
                        return@launch
                    } catch (e: Exception) {
                        if (e is CancellationException || isCancelled) {
                            Log.d(TAG, "Download cancelled during execution")
                            updateError(appContext, downloadId, "Download cancelled")
                            return@launch
                        }

                        lastException = e
                        Log.e(TAG, "Download attempt $attempt failed: ${e.message}", e)
                        if (attempt < MAX_RETRIES) {
                            val retryMsg = "Retrying (${attempt}/$MAX_RETRIES): ${extractUserFriendlyError(e)}"
                            showNotification(
                                context = appContext,
                                title = "Reconnecting download...",
                                content = retryMsg,
                                progress = _downloadProgress.value.progressPercent,
                                ongoing = true
                            )
                            delay(RETRY_DELAY_MS * attempt)
                        }
                    }
                }

                val errorMsg = lastException?.let { extractUserFriendlyError(it) } ?: "Download failed"
                Log.e(TAG, "All download attempts failed: $errorMsg")
                updateError(appContext, downloadId, errorMsg)

            } catch (e: CancellationException) {
                Log.d(TAG, "Download job cancelled")
                updateError(appContext, downloadId, "Download cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal download error: ${e.message}", e)
                updateError(appContext, downloadId, "Fatal error: ${e.message}")
            }
        }

        return downloadId
    }

    private suspend fun executeStreamingDownload(
        context: Context,
        url: String,
        targetFile: File,
        downloadId: Long,
        modelName: String
    ) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        var existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            Log.d(TAG, "Resuming download from $existingBytes bytes")
        }

        val response: Response = downloadClient.newCall(requestBuilder.build()).execute()
        val isResume = (response.code == 206)
        if (!response.isSuccessful && !isResume) {
            if (response.code == 416) {
                // Requested range not satisfiable -> start fresh
                tempFile.delete()
                existingBytes = 0L
                val freshResponse = downloadClient.newCall(Request.Builder().url(url).build()).execute()
                if (!freshResponse.isSuccessful) {
                    throw IOException("HTTP ${freshResponse.code}: ${freshResponse.message}")
                }
                processDownloadStream(context, freshResponse, tempFile, targetFile, downloadId, modelName, 0L)
                return
            }
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val offset = if (isResume) existingBytes else {
            tempFile.delete()
            0L
        }

        processDownloadStream(context, response, tempFile, targetFile, downloadId, modelName, offset)
    }

    private fun processDownloadStream(
        context: Context,
        response: Response,
        tempFile: File,
        targetFile: File,
        downloadId: Long,
        modelName: String,
        initialOffset: Long
    ) {
        val responseBody = response.body ?: throw IOException("Empty response body from server")
        val streamLength = responseBody.contentLength()
        val totalLength = if (streamLength > 0) streamLength + initialOffset else -1L

        val availableSpace = targetFile.parentFile?.freeSpace ?: 0L
        if (totalLength > 0 && availableSpace > 0 && availableSpace < (totalLength - initialOffset) * 1.05) {
            throw IOException("Not enough storage space. Need ${(totalLength - initialOffset) / (1024 * 1024)} MB, have ${availableSpace / (1024 * 1024)} MB")
        }

        var downloadedBytes = initialOffset
        var lastUpdateTime = System.currentTimeMillis()
        var lastUpdateBytes = downloadedBytes
        var speedBytesPerSec = 0L

        try {
            responseBody.byteStream().use { input: InputStream ->
                FileOutputStream(tempFile, initialOffset > 0).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            throw CancellationException("User cancelled download")
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= 500) {
                            val progress = if (totalLength > 0) ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 100) else 0
                            val elapsed = (currentTime - lastUpdateTime) / 1000.0
                            if (elapsed > 0) {
                                speedBytesPerSec = ((downloadedBytes - lastUpdateBytes) / elapsed).toLong()
                            }

                            _downloadProgress.value = ModelDownloadProgress(
                                downloadId = downloadId,
                                status = STATUS_RUNNING,
                                isDownloading = true,
                                progressPercent = progress,
                                bytesDownloaded = downloadedBytes,
                                totalBytes = totalLength,
                                speedBytesPerSec = speedBytesPerSec
                            )

                            val speedMb = speedBytesPerSec / (1024.0 * 1024.0)
                            val downloadedMb = downloadedBytes / (1024 * 1024)
                            val totalMb = totalLength / (1024 * 1024)
                            val statusText = if (totalMb > 0) {
                                "$downloadedMb MB / $totalMb MB (%.1f MB/s)".format(speedMb)
                            } else {
                                "$downloadedMb MB (%.1f MB/s)".format(speedMb)
                            }

                            showNotification(
                                context = context,
                                title = "Downloading $modelName ($progress%)",
                                content = statusText,
                                progress = progress,
                                ongoing = true
                            )

                            lastUpdateTime = currentTime
                            lastUpdateBytes = downloadedBytes
                        }
                    }

                    output.flush()
                }
            }

            if (totalLength > 0 && tempFile.length() < totalLength) {
                throw IOException("Incomplete download (${tempFile.length() / (1024 * 1024)} MB of ${totalLength / (1024 * 1024)} MB)")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                // Fallback copy if atomic rename fails across partitions
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

        } catch (e: Exception) {
            throw e
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows AI model download progress and status"
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        context: Context,
        title: String,
        content: String,
        progress: Int,
        ongoing: Boolean
    ) {
        try {
            val notificationIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_file_download)
                .setContentIntent(pendingIntent)
                .setOngoing(ongoing)
                .setSilent(true)

            if (ongoing) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(0, 0, false)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Could not post notification: ${e.message}")
        }
    }

    private fun cancelNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Could not cancel notification: ${e.message}")
        }
    }

    private fun ensureHuggingFaceDownloadParam(url: String): String {
        return when {
            url.contains("huggingface.co") && !url.contains("?download=true") && !url.contains("&download=true") -> {
                if (url.contains("?")) "$url&download=true" else "$url?download=true"
            }
            else -> url
        }
    }

    @JvmStatic
    fun cancelDownload(context: Context? = null) {
        isCancelled = true
        downloadJob?.cancel()
        downloadJob = null
        if (context != null) {
            cancelNotification(context)
        }
    }

    @JvmStatic
    fun getDownloadProgress(context: Context, downloadId: Long): ModelDownloadProgress {
        return _downloadProgress.value
    }

    @JvmStatic
    fun getDownloadStatus(context: Context, downloadId: Long): Int {
        return _downloadProgress.value.status
    }

    private fun extractUserFriendlyError(e: Exception): String {
        return when {
            e is CancellationException -> "Download cancelled"
            e is UnknownHostException -> "No internet / DNS error. Cannot reach HuggingFace."
            e is SocketTimeoutException -> "Connection timed out. Retrying..."
            e is SSLException -> "Secure connection (SSL) error: ${e.localizedMessage}"
            e.message?.contains("HTTP 401") == true -> "Server authorization error (401). Please verify model link."
            e.message?.contains("HTTP 403") == true -> "Access forbidden (403). Host denied access."
            e.message?.contains("HTTP 404") == true -> "Model file not found on server (404)."
            e.message?.contains("HTTP 429") == true -> "Server rate limit reached. Please wait a few minutes."
            e.message?.contains("Not enough storage") == true -> e.message ?: "Not enough storage space"
            e.message?.contains("Permission denied", ignoreCase = true) == true -> "Storage permission error. App directory used."
            e is IOException -> "Network error: ${e.localizedMessage ?: e.message}"
            else -> "Download error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun updateError(context: Context, downloadId: Long, message: String) {
        _downloadProgress.value = _downloadProgress.value.copy(
            downloadId = downloadId,
            status = STATUS_FAILED,
            isDownloading = false,
            isCompleted = false,
            error = message
        )
        showNotification(
            context = context,
            title = "❌ AI Model Download Failed",
            content = message,
            progress = 0,
            ongoing = false
        )
    }
}
