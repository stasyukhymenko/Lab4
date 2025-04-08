package com.example.lab4;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import com.example.lab4.db.AppDatabase;
import com.example.lab4.db.MediaDao;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PLAYLIST_ID = "com.example.lab4.EXTRA_PLAYLIST_ID";
    public static final String EXTRA_PLAYLIST_NAME = "com.example.lab4.EXTRA_PLAYLIST_NAME";
    private static final String TAG = "PlaylistDetailActivity";
    private RecyclerView rvMediaItems;
    private MediaItemAdapter adapter;
    private MediaDao mediaDao;
    private long currentPlaylistId = -1;
    private String currentPlaylistName = "Playlist";
    private LiveData<List<com.example.lab4.db.MediaItem>> mediaItemsLiveData;
    private List<com.example.lab4.db.MediaItem> currentMediaItemList;
    private ActivityResultLauncher<String[]> openDocumentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            try {
                getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
            } catch (Exception e) { Log.e(TAG, "Error setting custom navigation icon", e); }
        }

        mediaDao = AppDatabase.getInstance(this).mediaDao();
        rvMediaItems = findViewById(R.id.rvMediaItems);
        Button btnAddLocalFile = findViewById(R.id.btnAddLocalFile);
        currentPlaylistId = getIntent().getLongExtra(EXTRA_PLAYLIST_ID, -1);
        currentPlaylistName = getIntent().getStringExtra(EXTRA_PLAYLIST_NAME);

        if (currentPlaylistId == -1) {
            Toast.makeText(this, R.string.invalid_playlist_id_toast, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid Playlist ID received (-1). Finishing activity.");
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentPlaylistName != null ? currentPlaylistName : getString(R.string.playlist_detail_activity_title));
        }
        setupRecyclerView();
        initializeOpenDocumentLauncher();

        mediaItemsLiveData = mediaDao.getMediaItemsForPlaylist(currentPlaylistId);
        mediaItemsLiveData.observe(this, mediaItems -> {
            Log.d(TAG, "LiveData observer received update for playlist " + currentPlaylistId + ". Item count: " + (mediaItems != null ? mediaItems.size() : "null"));
            currentMediaItemList = mediaItems;
            if (adapter != null) {
                adapter.submitList(mediaItems);
            }
        });
        btnAddLocalFile.setOnClickListener(v -> selectLocalFile());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    private void setupRecyclerView() {
        MediaItemAdapter.OnMediaItemClickListener clickListener = clickedDbMediaItem -> {
            if (currentMediaItemList == null || currentMediaItemList.isEmpty()) {
                Toast.makeText(this, "Playlist is empty or not loaded yet", Toast.LENGTH_SHORT).show();
                return;
            }

            int startIndex = -1;
            ArrayList<String> uriList = new ArrayList<>();
            ArrayList<String> titleList = new ArrayList<>();
            for (int i = 0; i < currentMediaItemList.size(); i++) {
                com.example.lab4.db.MediaItem item = currentMediaItemList.get(i);
                Uri parsedUri = null;
                boolean isValid = false;
                if (item.mediaUri != null && !item.mediaUri.isEmpty()) {
                    try {
                        parsedUri = Uri.parse(item.mediaUri);
                        isValid = true;
                    } catch (Exception e) {
                        Log.w(TAG, "Invalid URI format in DB, skipping item ID " + item.mediaId + ": " + item.mediaUri);
                    }
                }

                if (isValid) {
                    uriList.add(item.mediaUri);
                    titleList.add((item.mediaTitle != null && !item.mediaTitle.isEmpty()) ? item.mediaTitle : getFileNameFromUri(parsedUri));
                    if (item.mediaId == clickedDbMediaItem.mediaId) {
                        startIndex = uriList.size() - 1;
                    }
                } else if (item.mediaId == clickedDbMediaItem.mediaId) {
                    startIndex = -2;
                }
            }
            if (uriList.isEmpty()) {
                Toast.makeText(this, "No valid items found to play in this playlist", Toast.LENGTH_SHORT).show(); return;
            }
            if (startIndex < 0) {
                Log.w(TAG, "Clicked item was invalid or not found in the valid list, starting playlist from beginning.");
                startIndex = 0;
            }

            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putStringArrayListExtra(PlayerActivity.EXTRA_MEDIA_URI_LIST, uriList);
            intent.putStringArrayListExtra(PlayerActivity.EXTRA_MEDIA_TITLE_LIST, titleList);
            intent.putExtra(PlayerActivity.EXTRA_START_INDEX, startIndex);
            startActivity(intent);
        };

        MediaItemAdapter.OnMediaItemDeleteListener deleteListener = mediaItemToDelete -> {
            if (mediaItemToDelete == null) return;
            Log.d(TAG, "Delete listener triggered for item ID: " + mediaItemToDelete.mediaId);
            new AlertDialog.Builder(this)
                    .setTitle("Delete Item")
                    .setMessage("Remove '" + (mediaItemToDelete.mediaTitle != null && !mediaItemToDelete.mediaTitle.isEmpty() ? mediaItemToDelete.mediaTitle : mediaItemToDelete.mediaUri) + "'?")
                    .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            try {
                                mediaDao.deleteMediaItem(mediaItemToDelete);
                                Log.i(TAG, "Successfully deleted media item ID: " + mediaItemToDelete.mediaId + " from playlist ID: " + currentPlaylistId);
                            } catch (Exception e) {
                                Log.e(TAG, "Error deleting media item from DB. ID: " + mediaItemToDelete.mediaId, e);
                                runOnUiThread(()-> Toast.makeText(PlaylistDetailActivity.this, "Error deleting item", Toast.LENGTH_SHORT).show());
                            }
                        });
                    })
                    .setNegativeButton(R.string.cancel_button, null)
                    .show();
        };
        adapter = new MediaItemAdapter(new MediaItemAdapter.MediaItemDiff(), clickListener, deleteListener);
        rvMediaItems.setAdapter(adapter);
        rvMediaItems.setLayoutManager(new LinearLayoutManager(this));
    }
    private void initializeOpenDocumentLauncher() {
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        Log.d(TAG, "SAF file selected: " + uri);
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            Log.i(TAG, "Persistable read permission granted for URI: " + uri);
                            addUrlToPlaylistDb(uri, getFileNameFromUri(uri));

                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take persistable URI permission for: " + uri, e);
                            Toast.makeText(this, "Failed to get permanent access permission", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
                    }
                });
    }
    private void selectLocalFile() {
        String[] mimeTypes = new String[]{
                "audio/*",
                "video/*"
        };
        try {
            openDocumentLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "Error launching document picker", e);
            Toast.makeText(this, "Cannot open file picker", Toast.LENGTH_SHORT).show();
        }
    }
    private void addUrlToPlaylistDb(Uri mediaUri, String title) {
        if (mediaUri == null || currentPlaylistId == -1) {
            Log.e(TAG,"Cannot add item to DB: invalid URI or Playlist ID.");
            return;
        }
        String uriString = mediaUri.toString();
        String finalTitle = (title != null && !title.isEmpty()) ? title : getString(R.string.unknown_media_title);
        com.example.lab4.db.MediaItem newItem = new com.example.lab4.db.MediaItem(uriString, finalTitle, currentPlaylistId);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                mediaDao.insertMediaItem(newItem);
                Log.i(TAG,"Added URI to DB: " + uriString + " (Title: " + finalTitle + ") for playlist ID: " + currentPlaylistId);
            } catch (Exception e) {
                Log.e(TAG, "Error inserting media item into DB", e);
                runOnUiThread(()-> Toast.makeText(PlaylistDetailActivity.this, "Error adding item to database", Toast.LENGTH_SHORT).show());
            }
        });
    }
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return getString(R.string.unknown_media_title);
        String result = null;
        String scheme = uri.getScheme();

        if ("content".equalsIgnoreCase(scheme)) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    } else {
                        Log.w(TAG, "DISPLAY_NAME column not found for content URI: " + uri);
                    }
                } else {
                    if(cursor == null) Log.w(TAG, "ContentResolver query returned null cursor for URI: " + uri);
                    else Log.w(TAG, "ContentResolver query returned empty cursor for URI: " + uri);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error getting filename from ContentResolver for URI: "+ uri, e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
                try {
                    result = java.net.URLDecoder.decode(result, "UTF-8");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to URL decode path segment: " + result);
                }
                int queryParamIndex = result.indexOf('?');
                if (queryParamIndex > 0) {
                    result = result.substring(0, queryParamIndex);
                }
            }
        }

        return (result != null && !result.isEmpty()) ? result : getString(R.string.unknown_media_title);
    }
}