package org.schabi.newpipe.download;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlaylistDownloadAdapter extends RecyclerView.Adapter<PlaylistDownloadAdapter.ViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selectedCount, int totalCount);
    }

    private final List<StreamInfoItem> items;
    private final boolean[] selectedArray;
    private final OnSelectionChangedListener listener;

    public PlaylistDownloadAdapter(final List<StreamInfoItem> items,
                                   final OnSelectionChangedListener listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.selectedArray = new boolean[this.items.size()];
        Arrays.fill(this.selectedArray, true);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_playlist_download_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
        final StreamInfoItem item = items.get(position);
        holder.titleView.setText(item.getName());

        if (item.getDuration() > 0) {
            holder.durationView.setText(Localization.getDurationString(item.getDuration()));
            holder.durationView.setVisibility(View.VISIBLE);
        } else {
            holder.durationView.setVisibility(View.GONE);
        }

        CoilHelper.INSTANCE.loadThumbnail(holder.thumbnailView, item.getThumbnails());

        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selectedArray[position]);

        holder.itemView.setOnClickListener(v -> {
            holder.checkBox.setChecked(!holder.checkBox.isChecked());
        });

        holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            selectedArray[holder.getBindingAdapterPosition()] = isChecked;
            notifySelectionChanged();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setSelectAll(final boolean selectAll) {
        Arrays.fill(selectedArray, selectAll);
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public List<StreamInfoItem> getSelectedItems() {
        final List<StreamInfoItem> selected = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (selectedArray[i]) {
                selected.add(items.get(i));
            }
        }
        return selected;
    }

    public int getSelectedCount() {
        int count = 0;
        for (final boolean b : selectedArray) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public long getSelectedTotalDurationSeconds() {
        long totalSeconds = 0;
        for (int i = 0; i < items.size(); i++) {
            if (selectedArray[i]) {
                final long dur = items.get(i).getDuration();
                if (dur > 0) {
                    totalSeconds += dur;
                } else {
                    totalSeconds += 240; // Assume 4 min average if duration missing
                }
            }
        }
        return totalSeconds;
    }

    private void notifySelectionChanged() {
        if (listener != null) {
            listener.onSelectionChanged(getSelectedCount(), items.size());
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final CheckBox checkBox;
        final ImageView thumbnailView;
        final TextView titleView;
        final TextView durationView;

        ViewHolder(@NonNull final View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.item_checkbox);
            thumbnailView = itemView.findViewById(R.id.item_thumbnail);
            titleView = itemView.findViewById(R.id.item_title);
            durationView = itemView.findViewById(R.id.item_duration);
        }
    }
}
