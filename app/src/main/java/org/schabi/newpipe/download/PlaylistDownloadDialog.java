package org.schabi.newpipe.download;

import android.app.Dialog;
import android.os.Bundle;
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
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ThemeHelper;

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

        final Toolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        if (playlistTitle != null && !playlistTitle.isEmpty()) {
            toolbar.setTitle(playlistTitle);
        }

        selectAllCheckBox = view.findViewById(R.id.select_all_checkbox);
        selectedCountText = view.findViewById(R.id.selected_count_text);
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
        if (downloadButton != null) {
            downloadButton.setText(getString(R.string.playlist_download_btn, selectedCount));
            downloadButton.setEnabled(selectedCount > 0);
        }
    }
}
