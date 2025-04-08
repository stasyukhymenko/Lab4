package com.example.lab4;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.lab4.db.AppDatabase;
import com.example.lab4.db.MediaDao;
import com.example.lab4.db.Playlist;

public class PlaylistActivity extends AppCompatActivity {

    private static final String TAG = "PlaylistActivity";
    private RecyclerView rvPlaylists;
    private PlaylistAdapter adapter;
    private MediaDao mediaDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist);
        Toolbar toolbar = findViewById(R.id.toolbar_playlist);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.playlist_activity_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            try {
                getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
            } catch (Exception e) {
                Log.e(TAG, "Error setting custom navigation icon", e);
            }
        }
        mediaDao = AppDatabase.getInstance(this).mediaDao();
        rvPlaylists = findViewById(R.id.rvPlaylists);
        Button btnAddPlaylist = findViewById(R.id.btnAddPlaylist);

        setupRecyclerView();

        mediaDao.getAllPlaylists().observe(this, playlists -> {
            Log.d(TAG, "LiveData observer received update. Playlist count: " + (playlists != null ? playlists.size() : "null"));
            if (adapter != null) {
                adapter.submitList(playlists);
            }
        });

        btnAddPlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
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
        PlaylistAdapter.OnPlaylistClickListener clickListener = playlist -> {
            Intent intent = new Intent(PlaylistActivity.this, PlaylistDetailActivity.class);
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.getPlaylistId());
            intent.putExtra(PlaylistDetailActivity.EXTRA_PLAYLIST_NAME, playlist.getPlaylistName());
            startActivity(intent);
        };

        PlaylistAdapter.OnPlaylistDeleteListener deleteListener = playlist -> {
            showDeleteConfirmationDialog(playlist);
        };

        adapter = new PlaylistAdapter(new PlaylistAdapter.PlaylistDiff(), clickListener, deleteListener);
        rvPlaylists.setAdapter(adapter);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
    }

    private void showDeleteConfirmationDialog(Playlist playlist) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete '" + playlist.getPlaylistName() + "' and all its media?")
                .setPositiveButton(R.string.delete_button, (dialog, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        try {
                            mediaDao.deletePlaylist(playlist);
                            Log.i(TAG, "Deleted playlist ID: " + playlist.playlistId);
                        } catch (Exception e) {
                            Log.e(TAG, "Error deleting playlist ID: " + playlist.playlistId, e);
                            runOnUiThread(()-> Toast.makeText(PlaylistActivity.this, "Error deleting playlist", Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel_button, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showCreatePlaylistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_playlist_dialog_title);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.playlist_name_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.create_button, (dialog, which) -> {
            String playlistName = input.getText().toString().trim();
            if (!playlistName.isEmpty()) {
                Playlist newPlaylist = new Playlist(playlistName);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    Playlist existing = mediaDao.getPlaylistByName(playlistName);
                    if (existing == null) {
                        mediaDao.insertPlaylist(newPlaylist);
                        runOnUiThread(()-> Toast.makeText(PlaylistActivity.this, R.string.playlist_created_toast, Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(()-> Toast.makeText(PlaylistActivity.this, R.string.playlist_name_exists_toast, Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                Toast.makeText(this, R.string.playlist_name_empty_toast, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(R.string.cancel_button, (dialog, which) -> dialog.cancel());

        builder.show();
    }
}