package org.schabi.newpipe.player.mediasession;

import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import androidx.media3.common.Player.RepeatMode;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.notification.NotificationActionData;
import org.schabi.newpipe.player.notification.NotificationConstants;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MediaSessionPlayerUi extends PlayerUi
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "MediaSessUi";

    @NonNull
    private final MediaSessionCompat mediaSession;
    private final Object sessionConnector;

    private final String ignoreHardwareMediaButtonsKey;
    private boolean shouldIgnoreHardwareMediaButtons = false;

    // used to check whether any notification action changed, before sending costly updates
    private List<NotificationActionData> prevNotificationActions = List.of();


    private final MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            if (player != null) {
                player.play();
            }
        }

        @Override
        public void onPause() {
            if (player != null) {
                player.pause();
            }
        }

        @Override
        public void onSkipToNext() {
            if (player != null) {
                player.playNext();
            }
        }

        @Override
        public void onSkipToPrevious() {
            if (player != null) {
                player.playPrevious();
            }
        }

        @Override
        public void onSeekTo(final long pos) {
            if (player != null) {
                player.seekTo(pos);
            }
        }

        @Override
        public boolean onMediaButtonEvent(final Intent mediaButtonEvent) {
            if (shouldIgnoreHardwareMediaButtons) {
                return true;
            }
            return super.onMediaButtonEvent(mediaButtonEvent);
        }
    };

    public MediaSessionPlayerUi(@NonNull final Player player,
                                @NonNull final MediaSessionCompat mediaSession) {
        super(player);
        this.mediaSession = mediaSession;
        this.sessionConnector = null;
        this.ignoreHardwareMediaButtonsKey =
                context.getString(R.string.ignore_hardware_media_buttons_key);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        destroyPlayer(); // release previously used resources

        mediaSession.setCallback(sessionCallback);
        mediaSession.setActive(true);

        // listen to changes to ignore_hardware_media_buttons_key
        updateShouldIgnoreHardwareMediaButtons(player.getPrefs());
        player.getPrefs().registerOnSharedPreferenceChangeListener(this);

        // force updating media session actions by resetting the previous ones
        prevNotificationActions = List.of();
        updateMediaSessionActions();
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        player.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        try {
            final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());
            mediaSession.setCallback(null);
            mediaSession.setActive(false);
        } catch (final Exception ignored) {
        }
        prevNotificationActions = List.of();
    }

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        updateMediaSessionState();
    }


    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key == null || key.equals(ignoreHardwareMediaButtonsKey)) {
            updateShouldIgnoreHardwareMediaButtons(sharedPreferences);
        }
    }

    public void updateShouldIgnoreHardwareMediaButtons(final SharedPreferences sharedPreferences) {
        shouldIgnoreHardwareMediaButtons =
                sharedPreferences.getBoolean(ignoreHardwareMediaButtonsKey, false);
    }


    public void handleMediaButtonIntent(final Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public Optional<MediaSessionCompat.Token> getSessionToken() {
        return Optional.ofNullable(mediaSession).map(MediaSessionCompat::getSessionToken);
    }


    private Object getForwardingPlayer() { return null; }

    private MediaMetadataCompat buildMediaMetadata() {
        if (DEBUG) {
            Log.d(TAG, "buildMediaMetadata called");
        }

        // set title and artist
        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName());

        // set duration (-1 for livestreams or if unknown, see the METADATA_KEY_DURATION docs)
        final long duration = player.getCurrentStreamInfo()
                .filter(info -> !StreamTypeUtil.isLiveStream(info.getStreamType()))
                .map(info -> info.getDuration() * 1000L)
                .orElse(-1L);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        // set album art, unless the user asked not to, or there is no thumbnail available
        final boolean showThumbnail = player.getPrefs().getBoolean(
                context.getString(R.string.show_thumbnail_key), true);
        Optional.ofNullable(player.getThumbnail())
                .filter(bitmap -> showThumbnail)
                .ifPresent(bitmap -> {
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap);
                });

        return builder.build();
    }

    private void updateMediaSessionState() {
        if (!mediaSession.isActive()) {
            return;
        }

        final long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SEEK_TO;

        final int state;
        if (player.isPlaying()) {
            state = PlaybackStateCompat.STATE_PLAYING;
        } else if (player.getCurrentState() == Player.STATE_BUFFERING) {
            state = PlaybackStateCompat.STATE_BUFFERING;
        } else {
            state = PlaybackStateCompat.STATE_PAUSED;
        }

        final float speed = player.getPlaybackSpeed();
        final long position = player.getExoPlayer() != null
                ? player.getExoPlayer().getCurrentPosition()
                : 0L;

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, speed);

        mediaSession.setPlaybackState(stateBuilder.build());
        mediaSession.setMetadata(buildMediaMetadata());
    }

    private void updateMediaSessionActions() {
        updateMediaSessionState();
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        updateMediaSessionActions();
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        updateMediaSessionActions();
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        updateMediaSessionActions();
    }

    @Override
    public void onPaused() {
        super.onPaused();
        updateMediaSessionActions();
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        updateMediaSessionActions();
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        updateMediaSessionActions();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);
        updateMediaSessionActions();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        updateMediaSessionActions();
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (ACTION_RECREATE_NOTIFICATION.equals(intent.getAction())) {
            // the notification actions changed
            updateMediaSessionActions();
        }
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        updateMediaSessionActions();
    }

    @Override
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        updateMediaSessionActions();
    }
}




