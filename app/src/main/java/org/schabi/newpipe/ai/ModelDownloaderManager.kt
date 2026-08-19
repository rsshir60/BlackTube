package org.schabi.newpipe.ai

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private const val DOWNLOAD_DIR = "BlackTube_AI"
    private const val BUFFER_SIZE = 8192 // 8KB buffer
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 3000L

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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "BlackTube/1.3.0 (Android)")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity")
                .build()
            chain.proceed(request)
        }
        .build()

    @JvmStatic
    fun startModelDownload(context: Context, modelInfo: LocalModelInfo): Long {
        cancelDownload()
        val downloadId = System.currentTimeMillis()

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

                val targetFile = UniversalModelRegistry.getModelFile(context, modelInfo)
                targetFile.parentFile?.mkdirs()

                var lastException: Exception? = null
                for (attempt in 1..MAX_RETRIES) {
                    if (isCancelled) {
                        Log.d(TAG, "Download cancelled by user")
                        updateError(downloadId, "Download cancelled")
                        return@launch
                    }

                    try {
                        Log.d(TAG, "Download attempt $attempt of $MAX_RETRIES")
                        executeStreamingDownload(finalUrl, targetFile, downloadId)

                        _downloadProgress.value = _downloadProgress.value.copy(
                            status = STATUS_SUCCESSFUL,
                            isDownloading = false,
                            isCompleted = true,
                            progressPercent = 100,
                            error = null
                        )

                        Log.d(TAG, "Download completed: ${targetFile.absolutePath} (${targetFile.length() / (1024 * 1024)} MB)")
                        return@launch
                    } catch (e: IOException) {
                        lastException = e
                        Log.e(TAG, "Download attempt $attempt failed: ${e.message}", e)
                        if (attempt < MAX_RETRIES) {
                            delay(RETRY_DELAY_MS * attempt)
                        }
                    } catch (e: Exception) {
                        lastException = e
                        Log.e(TAG, "Unexpected error on attempt $attempt: ${e.message}", e)
                        break
                    }
                }

                val errorMsg = lastException?.let { extractUserFriendlyError(it) } ?: "Download failed"
                Log.e(TAG, "All download attempts failed: $errorMsg")
                updateError(downloadId, errorMsg)

            } catch (e: CancellationException) {
                Log.d(TAG, "Download job cancelled")
                updateError(downloadId, "Download cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Fatal download error: ${e.message}", e)
                updateError(downloadId, "Fatal error: ${e.message}")
            }
        }

        return downloadId
    }

    private suspend fun executeStreamingDownload(
        url: String,
        targetFile: File,
        downloadId: Long
    ) {
        val request = Request.Builder()
            .url(url)
            .build()

        val response: Response = downloadClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }

        val responseBody = response.body ?: throw IOException("Empty response body")
        val contentLength = responseBody.contentLength()

        val availableSpace = targetFile.parentFile?.freeSpace ?: 0L
        if (contentLength > 0 && availableSpace > 0 && availableSpace < contentLength * 1.1) {
            throw IOException("Not enough storage space. Need ${contentLength / (1024 * 1024)} MB, have ${availableSpace / (1024 * 1024)} MB")
        }

        var downloadedBytes = 0L
        var lastUpdateTime = System.currentTimeMillis()
        var lastUpdateBytes = 0L
        var speedBytesPerSec = 0L

        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

        try {
            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
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
                            val progress = if (contentLength > 0) ((downloadedBytes * 100) / contentLength).toInt() else 0
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
                                totalBytes = contentLength,
                                speedBytesPerSec = speedBytesPerSec
                            )

                            lastUpdateTime = currentTime
                            lastUpdateBytes = downloadedBytes
                        }
                    }

                    output.flush()
                }
            }

            if (contentLength > 0 && tempFile.length() != contentLength) {
                tempFile.delete()
                throw IOException("File size mismatch. Expected ${contentLength / (1024 * 1024)} MB, got ${tempFile.length() / (1024 * 1024)} MB")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                throw IOException("Failed to rename temporary file to final target")
            }

        } catch (e: Exception) {
            tempFile.delete()
            throw e
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
    fun cancelDownload() {
        isCancelled = true
        downloadJob?.cancel()
        downloadJob = null
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
            e.message?.contains("HTTP 404") == true -> "Model file not found on server. Please try again later."
            e.message?.contains("HTTP 429") == true -> "Server rate limit reached. Please wait a few minutes and try again."
            e.message?.contains("Not enough storage") == true -> e.message ?: "Not enough storage space"
            e.message?.contains("timeout", ignoreCase = true) == true -> "Download timed out. Check your internet connection and try again."
            e.message?.contains("connect", ignoreCase = true) == true -> "Cannot connect to model host. Check your internet connection."
            e.message?.contains("File size mismatch") == true -> "Download corrupted. Please try again."
            e is IOException -> "Network error: ${e.message}"
            else -> "Download failed: ${e.message}"
        }
    }

    private fun updateError(downloadId: Long, message: String) {
        _downloadProgress.value = _downloadProgress.value.copy(
            downloadId = downloadId,
            status = STATUS_FAILED,
            isDownloading = false,
            isCompleted = false,
            error = message
        )
    }
}
