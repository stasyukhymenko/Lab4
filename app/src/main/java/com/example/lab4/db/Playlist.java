package com.example.lab4.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "playlists")
public class Playlist {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "playlist_id")
    public long playlistId;

    @NonNull
    @ColumnInfo(name = "playlist_name")
    public String playlistName;

    public Playlist(@NonNull String playlistName) {
        this.playlistName = playlistName;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    @NonNull
    public String getPlaylistName() {
        return playlistName;
    }
}