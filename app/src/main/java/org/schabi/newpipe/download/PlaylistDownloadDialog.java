package org.schabi.newpipe.download;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.Serializable;
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
    private TextView storageEstimateText;
    private TextView storageAvailableText;
    private Spinner qualitySpinner;
    private MaterialButton downloadButton;

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

        final TextView dialogTitleView = view.findViewById(R.id.dialog_title);
        if (dialogTitleView != null && playlistTitle != null && !playlistTitle.isEmpty()) {
            dialogTitleView.setText(playlistTitle);
        }

        selectAllCheckBox = view.findViewById(R.id.select_all_checkbox);
        selectedCountText = view.findViewById(R.id.selected_count_text);
        storageEstimateText = view.findViewById(R.id.storage_estimate_text);
        storageAvailableText = view.findViewById(R.id.storage_available_text);
        qualitySpinner = view.findViewById(R.id.quality_spinner);
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

        setupQualitySpinner();
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

    private void setupQualitySpinner() {
        final String[] qualityOptions = new String[]{
                getString(R.string.playlist_download_quality_best),
                getString(R.string.playlist_download_quality_720p),
                getString(R.string.playlist_download_quality_480p),
                getString(R.string.playlist_download_quality_360p),
                getString(R.string.playlist_download_quality_audio)
        };

        final ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                qualityOptions
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(spinnerAdapter);

        qualitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
                switch (position) {
                    case 0:
                        selectedQualityMode = PlaylistDownloadManager.QualityMode.BEST_VIDEO;
                        break;
                    case 1:
                        selectedQualityMode = PlaylistDownloadManager.QualityMode.VIDEO_720P;
                        break;
                    case 2:
                        selectedQualityMode = PlaylistDownloadManager.QualityMode.VIDEO_480P;
                        break;
                    case 3:
                        selectedQualityMode = PlaylistDownloadManager.QualityMode.VIDEO_360P;
                        break;
                    case 4:
                        selectedQualityMode = PlaylistDownloadManager.QualityMode.AUDIO_ONLY;
                        break;
                }
                if (adapter != null) {
                    updateSelectionUI(adapter.getSelectedCount(), streamItems.size());
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) { }
        });
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

        if (storageEstimateText != null && getContext() != null) {
            final String formattedEst = Formatter.formatFileSize(getContext(), estimatedBytes);
            storageEstimateText.setText(getString(R.string.playlist_download_est_size, formattedEst));
        }

        if (storageAvailableText != null && getContext() != null) {
            final String formattedAvail = Formatter.formatFileSize(getContext(), availableBytes);
            storageAvailableText.setText(getString(R.string.playlist_download_available_space, formattedAvail));
        }

        if (downloadButton != null) {
            if (estimatedBytes > 0 && availableBytes > 0 && estimatedBytes > availableBytes) {
                downloadButton.setText(R.string.playlist_download_insufficient_storage);
                downloadButton.setEnabled(false);
            } else {
                downloadButton.setText(getString(R.string.playlist_download_btn, selectedCount));
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
            final File path = Environment.getExternalStorageDirectory();
            final StatFs stat = new StatFs(path.getPath());
            return stat.getAvailableBytes();
        } catch (final Exception e) {
            return 0L;
        }
    }
}
