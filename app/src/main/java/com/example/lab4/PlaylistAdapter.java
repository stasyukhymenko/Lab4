package com.example.lab4;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lab4.db.Playlist;

public class PlaylistAdapter extends ListAdapter<Playlist, PlaylistAdapter.PlaylistViewHolder> {

    private final OnPlaylistClickListener clickListener;
    private final OnPlaylistDeleteListener deleteListener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
    }
    public interface OnPlaylistDeleteListener {
        void onPlaylistDelete(Playlist playlist);
    }

    public PlaylistAdapter(@NonNull DiffUtil.ItemCallback<Playlist> diffCallback,
                           OnPlaylistClickListener clickListener,
                           OnPlaylistDeleteListener deleteListener) {
        super(diffCallback);
        this.clickListener = clickListener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        Playlist current = getItem(position);
        holder.bind(current, clickListener, deleteListener);
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final TextView playlistNameTextView;
        private final ImageButton deleteButton;

        PlaylistViewHolder(View itemView) {
            super(itemView);
            playlistNameTextView = itemView.findViewById(R.id.tvItemPlaylistName);
            deleteButton = itemView.findViewById(R.id.btnDeletePlaylist);
        }

        public void bind(final Playlist playlist,
                         final OnPlaylistClickListener clickListener,
                         final OnPlaylistDeleteListener deleteListener) {
            playlistNameTextView.setText(playlist.getPlaylistName());
            itemView.setOnClickListener(v -> clickListener.onPlaylistClick(playlist));
            deleteButton.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onPlaylistDelete(playlist);
                }
            });
        }
    }

    public static class PlaylistDiff extends DiffUtil.ItemCallback<Playlist> {
        @Override
        public boolean areItemsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
            return oldItem.getPlaylistId() == newItem.getPlaylistId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Playlist oldItem, @NonNull Playlist newItem) {
            return oldItem.getPlaylistName().equals(newItem.getPlaylistName());
        }
    }
}