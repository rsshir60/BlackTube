package org.schabi.newpipe.download;

import static org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP;
import static org.schabi.newpipe.util.ListHelper.getStreamsOfSpecifiedDelivery;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.streams.io.SharpStream;
import org.schabi.newpipe.streams.io.StoredDirectoryHelper;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class PlaylistDownloadManager {
    private static final String TAG = "PlaylistDownloadManager";
    private static final String CHANNEL_ID = "playlist_downloads";
    private static final int NOTIFICATION_ID = 9981;

    public enum QualityMode {
        BEST_VIDEO,
        VIDEO_720P,
        VIDEO_480P,
        VIDEO_360P,
        AUDIO_ONLY
    }

    private static PlaylistDownloadManager instance;

    public static synchronized PlaylistDownloadManager getInstance() {
        if (instance == null) {
            instance = new PlaylistDownloadManager();
        }
        return instance;
    }

    private final CompositeDisposable disposables = new CompositeDisposable();
    private boolean isProcessing = false;
    private final List<PlaylistDownloadEntry> queue = Collections.synchronizedList(new ArrayList<>());
    private final List<PlaylistDownloadEntry> completedBatch = Collections.synchronizedList(new ArrayList<>());
    private QualityMode currentQualityMode = QualityMode.BEST_VIDEO;
    private String currentPlaylistTitle = "Playlist";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PlaylistDownloadManager() { }

    public synchronized void startBatchDownload(final Context context,
                                               final List<StreamInfoItem> items,
                                               final QualityMode qualityMode,
                                               final String playlistTitle) {
        if (items == null || items.isEmpty()) {
            return;
        }

        final Context appContext = context.getApplicationContext();
        this.currentQualityMode = qualityMode;
        if (playlistTitle != null && !playlistTitle.trim().isEmpty()) {
            this.currentPlaylistTitle = playlistTitle.trim();
        } else {
            this.currentPlaylistTitle = "Playlist";
        }

        synchronized (queue) {
            completedBatch.clear();
            for (final StreamInfoItem item : items) {
                queue.add(new PlaylistDownloadEntry(item));
            }
        }

        createNotificationChannel(appContext);

        mainHandler.post(() -> Toast.makeText(appContext,
                appContext.getString(R.string.playlist_download_starting),
                Toast.LENGTH_SHORT).show());

        if (!isProcessing) {
            processNextInQueue(appContext);
        }
    }

    private void processNextInQueue(final Context context) {
        PlaylistDownloadEntry nextEntry = null;

        synchronized (queue) {
            for (final PlaylistDownloadEntry entry : queue) {
                if (entry.getState() == PlaylistDownloadEntry.State.PENDING) {
                    nextEntry = entry;
                    break;
                }
            }

            if (nextEntry == null) {
                isProcessing = false;
                final int completedCount = completedBatch.size();
                generateM3u8Playlist(context, new ArrayList<>(completedBatch), currentPlaylistTitle);
                showCompletionNotification(context, completedCount);
                queue.clear();
                completedBatch.clear();
                disposables.clear();
                return;
            }

            isProcessing = true;
            nextEntry.setState(PlaylistDownloadEntry.State.RESOLVING);
        }

        final PlaylistDownloadEntry currentEntry = nextEntry;
        final int currentIdx;
        final int totalCount;
        synchronized (queue) {
            currentIdx = queue.indexOf(currentEntry) + 1;
            totalCount = queue.size();
        }

        updateNotification(context,
                context.getString(R.string.playlist_download_resolving, currentIdx, totalCount),
                currentEntry.getTitle(), currentIdx, totalCount);

        final StreamInfoItem item = currentEntry.getStreamItem();
        if (item == null || item.getUrl() == null) {
            currentEntry.setState(PlaylistDownloadEntry.State.ERROR);
            currentEntry.setErrorMessage("Invalid stream item URL");
            processNextInQueue(context);
            return;
        }

        disposables.add(ExtractorHelper.getStreamInfo(context, item.getServiceId(), item.getUrl(), false)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        info -> {
                            try {
                                enqueueStreamDownload(context, currentEntry, info, currentIdx);
                                currentEntry.setState(PlaylistDownloadEntry.State.COMPLETED);
                                completedBatch.add(currentEntry);
                            } catch (final Exception e) {
                                Log.e(TAG, "Failed to enqueue stream for " + item.getName(), e);
                                currentEntry.setState(PlaylistDownloadEntry.State.ERROR);
                                currentEntry.setErrorMessage(e.getMessage());
                            }
                            processNextInQueue(context);
                        },
                        throwable -> {
                            Log.e(TAG, "Failed to resolve stream info for " + item.getName(), throwable);
                            currentEntry.setState(PlaylistDownloadEntry.State.ERROR);
                            currentEntry.setErrorMessage(throwable.getMessage());
                            processNextInQueue(context);
                        }
                ));
    }

    private void enqueueStreamDownload(final Context context,
                                      final PlaylistDownloadEntry entry,
                                      final StreamInfo info,
                                      final int trackIndex) throws Exception {
        final boolean isAudioOnly = currentQualityMode == QualityMode.AUDIO_ONLY;
        final char kind = isAudioOnly ? 'a' : 'v';
        final int threads = isAudioOnly ? 2 : 4;
        final String tag = isAudioOnly ? DownloadManager.TAG_AUDIO : DownloadManager.TAG_VIDEO;

        String[] urls;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;
        final List<MissionRecoveryInfo> recoveryInfo = new ArrayList<>();
        String filename;
        String mime;

        final List<AudioStream> audioStreams =
                getStreamsOfSpecifiedDelivery(info.getAudioStreams(), PROGRESSIVE_HTTP);

        final String cleanTitle = FilenameUtils.createFilename(context, info.getName());
        final String indexPrefix = String.format(Locale.US, "%02d - ", trackIndex);

        if (isAudioOnly) {
            if (audioStreams.isEmpty()) {
                throw new IllegalStateException("No progressive audio streams found");
            }
            final AudioStream audioStream = audioStreams.get(0);
            urls = new String[]{ audioStream.getContent() };
            recoveryInfo.add(new MissionRecoveryInfo(audioStream));
            mime = audioStream.getFormat().getMimeType();
            filename = indexPrefix + cleanTitle + "." + audioStream.getFormat().getSuffix();
            nearLength = audioStream.getAverageBitrate() > 0 ? (info.getDuration() * audioStream.getAverageBitrate() / 8) : 0;

            if (audioStream.getFormat() == MediaFormat.M4A) {
                psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
            } else if (audioStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
            }
        } else {
            final List<VideoStream> videoStreams = ListHelper.getSortedStreamVideosList(
                    context,
                    getStreamsOfSpecifiedDelivery(info.getVideoStreams(), PROGRESSIVE_HTTP),
                    getStreamsOfSpecifiedDelivery(info.getVideoOnlyStreams(), PROGRESSIVE_HTTP),
                    false,
                    false
            );

            if (videoStreams.isEmpty()) {
                throw new IllegalStateException("No compatible video streams found");
            }

            VideoStream selectedVideo = videoStreams.get(0);
            if (currentQualityMode == QualityMode.VIDEO_720P) {
                selectedVideo = findClosestResolution(videoStreams, "720p");
            } else if (currentQualityMode == QualityMode.VIDEO_480P) {
                selectedVideo = findClosestResolution(videoStreams, "480p");
            } else if (currentQualityMode == QualityMode.VIDEO_360P) {
                selectedVideo = findClosestResolution(videoStreams, "360p");
            }

            mime = selectedVideo.getFormat().getMimeType();
            filename = indexPrefix + cleanTitle + "." + selectedVideo.getFormat().getSuffix();

            if (selectedVideo.isVideoOnly()) {
                final AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(context, audioStreams, selectedVideo);
                if (audioStream != null) {
                    urls = new String[]{ selectedVideo.getContent(), audioStream.getContent() };
                    recoveryInfo.add(new MissionRecoveryInfo(selectedVideo));
                    recoveryInfo.add(new MissionRecoveryInfo(audioStream));

                    if (selectedVideo.getFormat() == MediaFormat.MPEG_4) {
                        psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                    } else {
                        psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                    }
                } else {
                    urls = new String[]{ selectedVideo.getContent() };
                    recoveryInfo.add(new MissionRecoveryInfo(selectedVideo));
                }
            } else {
                urls = new String[]{ selectedVideo.getContent() };
                recoveryInfo.add(new MissionRecoveryInfo(selectedVideo));
            }
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final int prefKey = isAudioOnly ? R.string.download_path_audio_key : R.string.download_path_video_key;
        final String savedPath = prefs.getString(context.getString(prefKey), null);

        StoredDirectoryHelper mainStorage = null;
        if (savedPath != null && !savedPath.isEmpty()) {
            try {
                mainStorage = new StoredDirectoryHelper(context, Uri.parse(savedPath), tag);
            } catch (final Exception e) {
                Log.w(TAG, "Failed to open configured storage for " + tag + ": " + savedPath, e);
            }
        }

        if (mainStorage == null) {
            final String defaultDirectory = isAudioOnly
                    ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES;
            mainStorage = new StoredDirectoryHelper(
                    context,
                    Uri.fromFile(NewPipeSettings.getDir(defaultDirectory)),
                    tag);
            if (!mainStorage.mkdirs()) {
                throw new IllegalStateException("Unable to create default download directory");
            }
        }

        final String folderName = (currentPlaylistTitle != null && !currentPlaylistTitle.isEmpty())
                ? currentPlaylistTitle : "Playlist";
        final StoredDirectoryHelper playlistStorage = mainStorage.createOrGetSubDirectory(folderName);
        final StoredFileHelper storage = playlistStorage.createFile(filename, mime);

        if (storage == null || !storage.canWrite()) {
            throw new IllegalStateException("Failed to create target storage file: " + filename);
        }

        DownloadManagerService.startMission(context, urls, storage, kind, threads,
                info, psName, psArgs, nearLength, new ArrayList<>(recoveryInfo));
    }

    private void generateM3u8Playlist(final Context context, final List<PlaylistDownloadEntry> entries, final String playlistTitle) {
        if (entries == null || entries.isEmpty()) return;

        try {
            final StringBuilder sb = new StringBuilder();
            sb.append("#EXTM3U\n");
            sb.append("#PLAYLIST:").append(playlistTitle).append("\n\n");

            final boolean isAudioOnly = currentQualityMode == QualityMode.AUDIO_ONLY;
            final String ext = isAudioOnly ? "m4a" : "mp4";

            for (int i = 0; i < entries.size(); i++) {
                final PlaylistDownloadEntry entry = entries.get(i);
                final StreamInfoItem item = entry.getStreamItem();
                final int trackIdx = i + 1;
                final String indexPrefix = String.format(Locale.US, "%02d - ", trackIdx);
                final String cleanTitle = FilenameUtils.createFilename(context, item.getName());
                final String filename = indexPrefix + cleanTitle + "." + ext;
                final long duration = item.getDuration() > 0 ? item.getDuration() : -1;

                sb.append("#EXTINF:").append(duration).append(",").append(item.getName()).append("\n");
                sb.append(filename).append("\n");
            }

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final String tag = isAudioOnly ? DownloadManager.TAG_AUDIO : DownloadManager.TAG_VIDEO;
            final int prefKey = isAudioOnly ? R.string.download_path_audio_key : R.string.download_path_video_key;
            final String savedPath = prefs.getString(context.getString(prefKey), null);

            StoredDirectoryHelper mainStorage = null;
            if (savedPath != null && !savedPath.isEmpty()) {
                try {
                    mainStorage = new StoredDirectoryHelper(context, Uri.parse(savedPath), tag);
                } catch (final Exception ignored) { }
            }

            if (mainStorage == null) {
                final String defaultDirectory = isAudioOnly
                        ? Environment.DIRECTORY_MUSIC : Environment.DIRECTORY_MOVIES;
                mainStorage = new StoredDirectoryHelper(
                        context,
                        Uri.fromFile(NewPipeSettings.getDir(defaultDirectory)),
                        tag);
                if (!mainStorage.mkdirs()) {
                    throw new IllegalStateException("Unable to create default download directory");
                }
            }

            final String folderName = (playlistTitle != null && !playlistTitle.isEmpty())
                    ? playlistTitle : "Playlist";
            final StoredDirectoryHelper playlistStorage = mainStorage.createOrGetSubDirectory(folderName);
            final String m3u8Filename = FilenameUtils.createFilename(context, playlistTitle) + ".m3u8";
            final StoredFileHelper storage = playlistStorage.createFile(
                    m3u8Filename, "audio/x-mpegurl");

            if (storage != null && storage.canWrite()) {
                try (SharpStream stream = storage.openAndTruncateStream()) {
                    stream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
                Log.i(TAG, "Successfully generated .m3u8 playlist file: " + m3u8Filename);
                mainHandler.post(() -> Toast.makeText(context,
                        context.getString(R.string.playlist_download_m3u8_created),
                        Toast.LENGTH_SHORT).show());
            }
        } catch (final Exception e) {
            Log.w(TAG, "Failed to generate .m3u8 playlist file", e);
        }
    }

    private VideoStream findClosestResolution(final List<VideoStream> streams, final String targetRes) {
        for (final VideoStream stream : streams) {
            if (stream.getResolution() != null && stream.getResolution().contains(targetRes)) {
                return stream;
            }
        }
        return streams.get(0);
    }

    private void createNotificationChannel(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Playlist Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows batch playlist download progress");
            final NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(final Context context, final String title, final String contentText,
                                     final int current, final int total) {
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        try {
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_file_download)
                    .setContentTitle(title)
                    .setContentText(contentText)
                    .setProgress(total, current, false)
                    .setOngoing(true);

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (final SecurityException e) {
            Log.w(TAG, "Missing notification permission on Android 13+", e);
        }
    }

    private void showCompletionNotification(final Context context, final int count) {
        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        try {
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_file_download)
                    .setContentTitle(context.getString(R.string.playlist_download_completed))
                    .setContentText(context.getString(R.string.playlist_download_progress, count, count))
                    .setOngoing(false)
                    .setAutoCancel(true);

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (final SecurityException e) {
            Log.w(TAG, "Missing notification permission on Android 13+", e);
        }
    }
}
