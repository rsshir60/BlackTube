package org.schabi.newpipe.util;

import android.graphics.Color;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

public final class SnackbarHelper {

    public interface OnUndoListener {
        void onUndo();
    }

    public interface OnConfirmedListener {
        void onConfirmed();
    }

    private SnackbarHelper() { }

    public static void showUndoSnackbar(@NonNull final View rootView,
                                        @NonNull final String message,
                                        @Nullable final OnUndoListener undoListener,
                                        @Nullable final OnConfirmedListener confirmedListener) {
        final Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(Color.parseColor("#1E1E24"));
        snackbar.setTextColor(Color.parseColor("#FFFFFF"));
        snackbar.setActionTextColor(Color.parseColor("#E50914"));

        if (undoListener != null) {
            snackbar.setAction("UNDO", v -> {
                HapticHelper.lightTick(v);
                undoListener.onUndo();
            });
        }

        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(final Snackbar transientBottomBar, final int event) {
                if (event != DISMISS_EVENT_ACTION && confirmedListener != null) {
                    confirmedListener.onConfirmed();
                }
            }
        });

        snackbar.show();
    }
}
