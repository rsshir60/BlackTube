package org.schabi.newpipe.download.playlist

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.playlistdownload.BatchProgress

class PlaylistDownloadService : LifecycleService() {

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "playlist_download_channel"
        private const val CHANNEL_NAME = "Playlist Downloads"

        private const val EXTRA_BATCH_ID = "batch_id"
        private const val EXTRA_PLAYLIST_ID = "playlist_id"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_CANCEL = "cancel"

        fun start(context: Context, batchId: String, playlistId: String) {
            val intent = Intent(context, PlaylistDownloadService::class.java).apply {
                putExtra(EXTRA_BATCH_ID, batchId)
                putExtra(EXTRA_PLAYLIST_ID, playlistId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaylistDownloadService::class.java))
        }

        fun updateProgress(context: Context, progress: BatchProgress) {
            try {
                val notification = buildNotification(context, progress)
                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                // Ignore notification errors
            }
        }

        private fun buildNotification(context: Context, progress: BatchProgress): Notification {
            createNotificationChannel(context)

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Downloading Playlist")
                .setContentText("${progress.completedCount}/${progress.totalCount} videos completed")
                .setSmallIcon(R.drawable.ic_file_download)
                .setContentIntent(pendingIntent)
                .setProgress(if (progress.totalCount > 0) progress.totalCount else 100, progress.completedCount, false)
                .setOngoing(true)
                .setSilent(true)
                .build()
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows playlist download progress"
                    setShowBadge(false)
                }

                val notificationManager = context.getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val initialProgress = BatchProgress(0, 0, 0, 0, 0)
        val notification = buildNotification(this, initialProgress)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
