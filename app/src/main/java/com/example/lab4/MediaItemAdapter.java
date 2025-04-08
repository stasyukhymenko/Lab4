package com.example.lab4;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lab4.db.MediaItem;
import java.util.Objects;

public class MediaItemAdapter extends ListAdapter<MediaItem, MediaItemAdapter.MediaItemViewHolder> {

    private final OnMediaItemClickListener clickListener;
    private final OnMediaItemDeleteListener deleteListener;
    public interface OnMediaItemClickListener {
        void onMediaItemClick(MediaItem mediaItem);
    }
    public interface OnMediaItemDeleteListener {
        void onMediaItemDelete(MediaItem mediaItem);
    }
    public MediaItemAdapter(@NonNull DiffUtil.ItemCallback<MediaItem> diffCallback,
                            OnMediaItemClickListener clickListener,
                            OnMediaItemDeleteListener deleteListener) {
        super(diffCallback);
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new MediaItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position) {
        MediaItem current = getItem(position);
        holder.bind(current, clickListener, deleteListener);
    }

    static class MediaItemViewHolder extends RecyclerView.ViewHolder {
        private final TextView mediaTitleTextView;
        private final ImageButton deleteButton;

        MediaItemViewHolder(View itemView) {
            super(itemView);
            mediaTitleTextView = itemView.findViewById(R.id.tvItemMediaTitle);
            deleteButton = itemView.findViewById(R.id.btnDeleteMediaItem);
        }
        public void bind(final MediaItem mediaItem,
                         final OnMediaItemClickListener clickListener,
                         final OnMediaItemDeleteListener deleteListener) {

            String displayTitle = mediaItem.mediaTitle;
            if (displayTitle == null || displayTitle.trim().isEmpty()) {
                if (mediaItem.mediaUri != null && !mediaItem.mediaUri.trim().isEmpty()) {
                    try {
                        Uri uri = Uri.parse(mediaItem.mediaUri);
                        String path = uri.getPath();
                        if (path != null) {
                            int cut = path.lastIndexOf('/');
                            if (cut != -1) {
                                displayTitle = path.substring(cut + 1);
                                try { displayTitle = java.net.URLDecoder.decode(displayTitle, "UTF-8"); } catch (Exception e) {}
                            } else {
                                displayTitle = path;
                            }
                        }
                        if(displayTitle == null || displayTitle.trim().isEmpty()) {
                            displayTitle = mediaItem.mediaUri;
                        }
                    } catch (Exception e) {
                        Log.w("MediaItemViewHolder", "Error parsing URI path for title fallback: " + mediaItem.mediaUri, e);
                        displayTitle = mediaItem.mediaUri;
                    }
                }
            }
            if (displayTitle == null || displayTitle.trim().isEmpty()) {
                displayTitle = itemView.getContext().getString(R.string.unknown_media_title);
            }
            mediaTitleTextView.setText(displayTitle);
            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onMediaItemClick(mediaItem);
                } else {
                    Log.w("MediaItemViewHolder", "ClickListener is null for item: " + mediaItem.mediaId);
                }
            });

            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onMediaItemDelete(mediaItem);
                    Log.d("MediaItemViewHolder", "Delete button clicked for item ID: " + mediaItem.mediaId);
                } else {
                    Log.w("MediaItemViewHolder", "DeleteListener is null for item: " + mediaItem.mediaId);
                }
            });
        }
    }
    public static class MediaItemDiff extends DiffUtil.ItemCallback<MediaItem> {
        @Override
        public boolean areItemsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
            return oldItem.mediaId == newItem.mediaId;
        }
        @Override
        public boolean areContentsTheSame(@NonNull MediaItem oldItem, @NonNull MediaItem newItem) {
            return Objects.equals(oldItem.mediaUri, newItem.mediaUri) &&
                    Objects.equals(oldItem.mediaTitle, newItem.mediaTitle) &&
                    oldItem.playlistCreatorId == newItem.playlistCreatorId;
        }
    }
}