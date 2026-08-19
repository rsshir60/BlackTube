package org.schabi.newpipe.download.playlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.database.playlistdownload.*

class PlaylistDownloadViewModel(
    private val repository: PlaylistDownloadRepository
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val isDownloading: Boolean = false,
        val items: List<PlaylistDownloadEntity> = emptyList(),
        val batchProgress: BatchProgress? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun observePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.observePlaylist(playlistId)
                .catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(
                            items = items,
                            isDownloading = items.any { item ->
                                item.status == DownloadStatus.DOWNLOADING
                            }
                        )
                    }
                }
        }
    }

    fun downloadPlaylist(
        context: Context,
        playlistId: String,
        playlistName: String,
        videos: List<PlaylistVideoInfo>,
        quality: String,
        format: String
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val videosToDownload = videos.filter { video ->
                    !repository.isVideoAlreadyDownloaded(video.id)
                }

                if (videosToDownload.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "All videos in this playlist are already downloaded"
                        )
                    }
                    return@launch
                }

                val batchId = repository.enqueuePlaylist(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    videos = videosToDownload,
                    quality = quality,
                    format = format
                )

                PlaylistDownloadWorker.enqueue(context, batchId, playlistId)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isDownloading = true,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start download: ${e.message}"
                    )
                }
            }
        }
    }

    fun pauseBatch(batchId: String) {
        viewModelScope.launch {
            repository.pauseBatch(batchId)
        }
    }

    fun resumeBatch(batchId: String) {
        viewModelScope.launch {
            repository.resumeBatch(batchId)
        }
    }

    fun cancelBatch(context: Context, batchId: String) {
        viewModelScope.launch {
            repository.cancelBatch(batchId)
            PlaylistDownloadWorker.cancel(context, batchId)
            PlaylistDownloadService.stop(context)
        }
    }

    fun removeItem(item: PlaylistDownloadEntity) {
        viewModelScope.launch {
            repository.removeItem(item.id)
        }
    }
}
