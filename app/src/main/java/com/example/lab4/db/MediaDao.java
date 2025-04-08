package com.example.lab4.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MediaDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertPlaylist(Playlist playlist);

    @Query("SELECT * FROM playlists ORDER BY playlist_name ASC")
    LiveData<List<Playlist>> getAllPlaylists();

    @Query("SELECT * FROM playlists ORDER BY playlist_name ASC")
    List<Playlist> getAllPlaylistsSync();

    @Query("SELECT * FROM playlists WHERE playlist_id = :playlistId")
    Playlist getPlaylistById(long playlistId);

    @Query("SELECT * FROM playlists WHERE playlist_name = :name LIMIT 1")
    Playlist getPlaylistByName(String name);

    @Delete
    void deletePlaylist(Playlist playlist);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMediaItem(MediaItem mediaItem);

    @Query("SELECT * FROM media_items WHERE playlist_creator_id = :playlistId ORDER BY media_id ASC")
    LiveData<List<MediaItem>> getMediaItemsForPlaylist(long playlistId);

    @Delete
    void deleteMediaItem(MediaItem mediaItem);
}