package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

/**
 * A {@link MediaCodecVideoRenderer} which gracefully catches and recovers from Qualcomm Snapdragon
 * and vendor hardware decoder buffer ownership exceptions ("client does not own the buffer #0").
 */
public final class CustomMediaCodecVideoRenderer extends MediaCodecVideoRenderer {

    @SuppressWarnings({"checkstyle:ParameterNumber", "squid:S107"})
    public CustomMediaCodecVideoRenderer(final Context context,
                                         final MediaCodecAdapter.Factory codecAdapterFactory,
                                         final MediaCodecSelector mediaCodecSelector,
                                         final long allowedJoiningTimeMs,
                                         final boolean enableDecoderFallback,
                                         @Nullable final Handler eventHandler,
                                         @Nullable final VideoRendererEventListener eventListener,
                                         final int maxDroppedFramesToNotify) {
        super(context, codecAdapterFactory, mediaCodecSelector, allowedJoiningTimeMs,
                enableDecoderFallback, eventHandler, eventListener, maxDroppedFramesToNotify);
    }

    @Override
    protected boolean codecNeedsSetOutputSurfaceWorkaround(final String name) {
        return super.codecNeedsSetOutputSurfaceWorkaround(name);
    }

    @Override
    protected boolean processOutputBuffer(final long positionUs,
                                          final long elapsedRealtimeUs,
                                          @Nullable final MediaCodecAdapter codec,
                                          @Nullable final java.nio.ByteBuffer buffer,
                                          final int bufferIndex,
                                          final int flags,
                                          final int sampleCount,
                                          final long sampleTimeUs,
                                          final boolean isDecodeOnlyBuffer,
                                          final boolean isLastBuffer,
                                          final androidx.media3.common.Format format)
            throws androidx.media3.exoplayer.ExoPlaybackException {
        try {
            return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer,
                    bufferIndex, flags, sampleCount, sampleTimeUs, isDecodeOnlyBuffer,
                    isLastBuffer, format);
        } catch (final Exception e) {
            final Throwable cause = e.getCause();
            if (cause instanceof android.media.MediaCodec.CodecException
                    || (e.getMessage() != null && e.getMessage().contains("buffer"))) {
                // Background/Foreground playback surface detached buffer error - skip gracefully without crashing
                return true;
            }
            throw e;
        }
    }
}
