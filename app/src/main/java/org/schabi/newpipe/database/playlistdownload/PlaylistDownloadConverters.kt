package org.schabi.newpipe.database.playlistdownload

import androidx.room.TypeConverter

class PlaylistDownloadConverters {
    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus?): String? {
        return status?.name
    }

    @TypeConverter
    fun toDownloadStatus(statusString: String?): DownloadStatus? {
        return statusString?.let {
            try {
                DownloadStatus.valueOf(it)
            } catch (e: Exception) {
                DownloadStatus.PENDING
            }
        }
    }
}
