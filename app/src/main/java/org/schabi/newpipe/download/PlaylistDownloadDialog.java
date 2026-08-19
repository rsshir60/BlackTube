package org.schabi.newpipe.download;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlaylistDownloadDialog extends DialogFragment {
    private static final String KEY_STREAM_ITEMS = "key_stream_items";
    private static final String KEY_PLAYLIST_TITLE = "key_playlist_title";

    public static PlaylistDownloadDialog newInstance(final List<StreamInfoItem> items,
                                                     final String playlistTitle) {
        final PlaylistDownloadDialog dialog = new PlaylistDownloadDialog();
        final Bundle args = new Bundle();
        args.putSerializable(KEY_STREAM_ITEMS, new ArrayList<>(items));
        args.putString(KEY_PLAYLIST_TITLE, playlistTitle);
        dialog.setArguments(args);
        return dialog;
    }

    private List<StreamInfoItem> streamItems;
    private String playlistTitle;

    private PlaylistDownloadAdapter adapter;
    private CheckBox selectAllCheckBox;
    private TextView selectedCountText;
    private TextView playlistCardTitle;
    private TextView playlistCardStats;
    private TextView storageAvailableText;
    private RadioGroup qualityRadioGroup;
    private Button downloadButton;

    private PlaylistDownloadManager.QualityMode selectedQualityMode = PlaylistDownloadManager.QualityMode.BEST_VIDEO;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(requireContext()));

        if (getArguments() != null) {
            streamItems = (List<StreamInfoItem>) getArguments().getSerializable(KEY_STREAM_ITEMS);
            playlistTitle = getArguments().getString(KEY_PLAYLIST_TITLE);
        }

        if (streamItems == null) {
            streamItems = new ArrayList<>();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlist_download, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final View closeButton = view.findViewById(R.id.close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(v -> dismiss());
        }

        final View cancelButton = view.findViewById(R.id.btn_cancel_download);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dismiss());
        }

        playlistCardTitle = view.findViewById(R.id.playlist_card_title);
        playlistCardStats = view.findViewById(R.id.playlist_card_stats);
        if (playlistCardTitle != null) {
            final String title = (playlistTitle != null && !playlistTitle.isEmpty()) ? playlistTitle : "Playlist";
            playlistCardTitle.setText("🎬 " + title);
        }

        selectAllCheckBox = view.findViewById(R.id.select_all_checkbox);
        selectedCountText = view.findViewById(R.id.selected_count_text);
        storageAvailableText = view.findViewById(R.id.storage_available_text);
        qualityRadioGroup = view.findViewById(R.id.quality_radio_group);
        downloadButton = view.findViewById(R.id.download_button);

        final RecyclerView recyclerView = view.findViewById(R.id.items_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PlaylistDownloadAdapter(streamItems, (selectedCount, totalCount) -> {
            updateSelectionUI(selectedCount, totalCount);
        });
        recyclerView.setAdapter(adapter);

        selectAllCheckBox.setOnClickListener(v -> {
            adapter.setSelectAll(selectAllCheckBox.isChecked());
        });

        setupQualityRadios(view);

        final androidx.appcompat.widget.SwitchCompat switchCutSponsors = view.findViewById(R.id.switch_cut_sponsors_playlist);
        if (switchCutSponsors != null) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            final boolean defaultCut = prefs.getBoolean("pref_key_sponsor_block_cut_downloads", false);
            switchCutSponsors.setChecked(defaultCut);
            switchCutSponsors.setOnCheckedChangeListener((btn, isChecked) -> {
                prefs.edit().putBoolean("pref_key_sponsor_block_cut_downloads", isChecked).apply();
            });
        }

        updateSelectionUI(adapter.getSelectedCount(), streamItems.size());

        downloadButton.setOnClickListener(v -> {
            final List<StreamInfoItem> selectedItems = adapter.getSelectedItems();
            if (selectedItems.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_videos_selected, Toast.LENGTH_SHORT).show();
                return;
            }

            PlaylistDownloadManager.getInstance().startBatchDownload(
                    requireContext(),
                    selectedItems,
                    selectedQualityMode,
                    playlistTitle
            );

            dismiss();
        });
    }

    private void setupQualityRadios(final View view) {
        final RadioButton r1080 = view.findViewById(R.id.radio_quality_1080p);
        final RadioButton r720 = view.findViewById(R.id.radio_quality_720p);
        final RadioButton r480 = view.findViewById(R.id.radio_quality_480p);
        final RadioButton rAudio = view.findViewById(R.id.radio_quality_audio);

        final View.OnClickListener radioListener = v -> {
            r1080.setChecked(v == r1080);
            r720.setChecked(v == r720);
            r480.setChecked(v == r480);
            rAudio.setChecked(v == rAudio);

            if (v == r1080) {
                selectedQualityMode = PlaylistDownloadManager.QualityMode.BEST_VIDEO;
            } else if (v == r720) {
                selectedQualityMode = PlaylistDownloadManager.QualityMode.VIDEO_720P;
            } else if (v == r480) {
                selectedQualityMode = PlaylistDownloadManager.QualityMode.VIDEO_480P;
            } else if (v == rAudio) {
                selectedQualityMode = PlaylistDownloadManager.QualityMode.AUDIO_ONLY;
            }

            if (adapter != null) {
                updateSelectionUI(adapter.getSelectedCount(), streamItems.size());
            }
        };

        r1080.setOnClickListener(radioListener);
        r720.setOnClickListener(radioListener);
        r480.setOnClickListener(radioListener);
        rAudio.setOnClickListener(radioListener);
    }

    private void updateSelectionUI(final int selectedCount, final int totalCount) {
        if (selectedCountText != null) {
            selectedCountText.setText(getString(R.string.playlist_download_progress, selectedCount, totalCount));
        }
        if (selectAllCheckBox != null) {
            selectAllCheckBox.setChecked(selectedCount == totalCount && totalCount > 0);
        }

        // Storage estimation
        final long totalSeconds = adapter != null ? adapter.getSelectedTotalDurationSeconds() : 0;
        final long estimatedBytes = calculateEstimatedBytes(totalSeconds, selectedQualityMode);
        final long availableBytes = getAvailableStorageBytes();

        if (playlistCardStats != null && getContext() != null) {
            final String formattedEst = Formatter.formatFileSize(getContext(), estimatedBytes);
            final String durationStr = Localization.getDurationString(totalSeconds);
            playlistCardStats.setText(String.format(java.util.Locale.getDefault(),
                    "%d videos • %s • ~%s", selectedCount, durationStr, formattedEst));
        }

        if (storageAvailableText != null && getContext() != null) {
            final String formattedAvail = Formatter.formatFileSize(getContext(), availableBytes);
            if (estimatedBytes > 0 && availableBytes > 0 && estimatedBytes > availableBytes) {
                final String formattedEst = Formatter.formatFileSize(getContext(), estimatedBytes);
                storageAvailableText.setText(String.format(java.util.Locale.getDefault(),
                        "Not enough space. Need %s, have %s", formattedEst, formattedAvail));
                storageAvailableText.setTextColor(0xFFFFC107); // Warning Amber
            } else {
                storageAvailableText.setText(String.format(java.util.Locale.getDefault(),
                        "Free space: %s", formattedAvail));
                storageAvailableText.setTextColor(0xFF4CAF50); // Success Green
            }
        }

        if (downloadButton != null) {
            if (estimatedBytes > 0 && availableBytes > 0 && estimatedBytes > availableBytes) {
                downloadButton.setText(R.string.playlist_download_insufficient_storage);
                downloadButton.setEnabled(false);
            } else {
                if (selectedCount == 1) {
                    downloadButton.setText("Download 1 video");
                } else {
                    downloadButton.setText(String.format(java.util.Locale.getDefault(),
                            "Download %d videos", selectedCount));
                }
                downloadButton.setEnabled(selectedCount > 0);
            }
        }
    }

    private long calculateEstimatedBytes(final long totalSeconds, final PlaylistDownloadManager.QualityMode mode) {
        final double minutes = totalSeconds / 60.0;
        final double mbPerMinute;
        switch (mode) {
            case BEST_VIDEO:
                mbPerMinute = 15.0; // ~1080p
                break;
            case VIDEO_720P:
                mbPerMinute = 8.0;
                break;
            case VIDEO_480P:
                mbPerMinute = 4.0;
                break;
            case VIDEO_360P:
                mbPerMinute = 2.5;
                break;
            case AUDIO_ONLY:
            default:
                mbPerMinute = 1.0;
                break;
        }
        return (long) (minutes * mbPerMinute * 1024 * 1024);
    }

    private long getAvailableStorageBytes() {
        try {
            File path = null;
            if (getContext() != null) {
                path = getContext().getExternalFilesDir(null);
            }
            if (path == null) {
                path = Environment.getExternalStorageDirectory();
            }
            if (path != null) {
                final StatFs stat = new StatFs(path.getPath());
                return stat.getAvailableBytes();
            }
            return 0L;
        } catch (final Exception e) {
            return 0L;
        }
    }
}
