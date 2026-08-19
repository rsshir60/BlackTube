package org.schabi.newpipe.download;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.io.Serializable;

public class PlaylistDownloadEntry implements Serializable {
    public enum State {
        PENDING,
        RESOLVING,
        DOWNLOADING,
        COMPLETED,
        ERROR,
        SKIPPED
    }

    private final StreamInfoItem streamItem;
    private State state;
    private String errorMessage;
    private int progress;

    public PlaylistDownloadEntry(final StreamInfoItem streamItem) {
        this.streamItem = streamItem;
        this.state = State.PENDING;
        this.errorMessage = null;
        this.progress = 0;
    }

    public StreamInfoItem getStreamItem() {
        return streamItem;
    }

    public String getTitle() {
        return streamItem != null ? streamItem.getName() : "";
    }

    public String getUrl() {
        return streamItem != null ? streamItem.getUrl() : "";
    }

    public State getState() {
        return state;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(final int progress) {
        this.progress = progress;
    }
}
