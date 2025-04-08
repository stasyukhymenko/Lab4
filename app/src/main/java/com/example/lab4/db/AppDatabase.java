package com.example.lab4.db;

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Database(entities = {Playlist.class, MediaItem.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract MediaDao mediaDao();
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    private static final AtomicBoolean mIsDatabaseInitialized = new AtomicBoolean(false);
    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    Log.d("AppDatabase", "Creating new database instance.");
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class,
                                    "lab4_media_database")
                            .build();
                    initializeDefaultPlaylistsIfNeeded(context);
                }
            }
        }
        return INSTANCE;
    }
    private static void initializeDefaultPlaylistsIfNeeded(final Context context) {
        if (mIsDatabaseInitialized.compareAndSet(false, true)) {
            Log.d("AppDatabase", "Initializing default playlists check...");
            databaseWriteExecutor.execute(() -> {
                AppDatabase db = getInstance(context.getApplicationContext());
                MediaDao dao = db.mediaDao();
                try {
                    Playlist downloads = dao.getPlaylistByName("Downloads");
                    if (downloads == null) {
                        dao.insertPlaylist(new Playlist("Downloads"));
                        Log.i("AppDatabase", "Created default 'Downloads' playlist.");
                    } else {
                        Log.d("AppDatabase", "'Downloads' playlist already exists.");
                    }
                    Playlist first = dao.getPlaylistByName("My First Playlist");
                    if (first == null) {
                        dao.insertPlaylist(new Playlist("My First Playlist"));
                        Log.i("AppDatabase", "Created default 'My First Playlist' playlist.");
                    } else {
                        Log.d("AppDatabase", "'My First Playlist' playlist already exists.");
                    }
                    Log.d("AppDatabase", "Default playlists initialization complete.");
                } catch (Exception e) {
                    Log.e("AppDatabase", "Error during default playlist initialization", e);
                    mIsDatabaseInitialized.set(false);
                }
            });
        } else {
            Log.v("AppDatabase", "Default playlists already initialized or initialization in progress.");
        }
    }
}