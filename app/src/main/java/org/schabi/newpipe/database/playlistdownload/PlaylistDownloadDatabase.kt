package org.schabi.newpipe.database.playlistdownload

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PlaylistDownloadEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(PlaylistDownloadConverters::class)
abstract class PlaylistDownloadDatabase : RoomDatabase() {
    abstract fun playlistDownloadDao(): PlaylistDownloadDao

    companion object {
        private const val DATABASE_NAME = "playlist_downloads.db"

        @Volatile
        private var INSTANCE: PlaylistDownloadDatabase? = null

        fun getInstance(context: Context): PlaylistDownloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlaylistDownloadDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
