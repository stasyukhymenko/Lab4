package com.example.lab4.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "media_items",
        foreignKeys = @ForeignKey(entity = Playlist.class,
                parentColumns = "playlist_id",
                childColumns = "playlist_creator_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("playlist_creator_id")})
public class MediaItem {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "media_id")
    public long mediaId;

    @NonNull
    @ColumnInfo(name = "media_uri")
    public String mediaUri;

    @ColumnInfo(name = "media_title")
    public String mediaTitle;

    @ColumnInfo(name = "playlist_creator_id")
    public long playlistCreatorId;

    public MediaItem(@NonNull String mediaUri, String mediaTitle, long playlistCreatorId) {
        this.mediaUri = mediaUri;
        this.mediaTitle = mediaTitle;
        this.playlistCreatorId = playlistCreatorId;
    }
}