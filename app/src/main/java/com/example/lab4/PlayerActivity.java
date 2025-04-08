package com.example.lab4;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.lab4.db.AppDatabase;
import com.example.lab4.db.MediaDao;
import com.example.lab4.db.Playlist;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    private static final String TAG = "PlayerActivity";
    private PlayerView playerView;
    private ExoPlayer player;
    private ArrayList<Uri> mediaUriList;
    private ArrayList<String> mediaTitleList;
    private int currentWindowIndex = 0;
    private Uri currentMediaUri;
    private String currentMediaTitle;

    private long playbackPosition = 0;
    private boolean playWhenReady = true;
    private boolean shuffleModeEnabled = false;
    private boolean isHttpUri = false;
    private boolean canBeAdded = false;

    public static final String EXTRA_MEDIA_URI_LIST = "com.example.lab4.EXTRA_MEDIA_URI_LIST";
    public static final String EXTRA_MEDIA_TITLE_LIST = "com.example.lab4.EXTRA_MEDIA_TITLE_LIST";
    public static final String EXTRA_START_INDEX = "com.example.lab4.EXTRA_START_INDEX";
    public static final String EXTRA_MEDIA_URI = "com.example.lab4.EXTRA_MEDIA_URI";
    public static final String EXTRA_MEDIA_TITLE = "com.example.lab4.EXTRA_MEDIA_TITLE";

    private MediaDao mediaDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private LinearLayout actionButtonsLayout;
    private ImageButton btnDownloadMedia;
    private ImageButton btnAddMediaToPlaylist;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.player_view);
        actionButtonsLayout = findViewById(R.id.llActionButtons);
        btnDownloadMedia = findViewById(R.id.btnDownloadMedia);
        btnAddMediaToPlaylist = findViewById(R.id.btnAddMediaToPlaylist);

        Toolbar toolbar = findViewById(R.id.toolbar_player);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }

        mediaDao = AppDatabase.getInstance(this).mediaDao();

        handleIntentAndSavedState(getIntent(), savedInstanceState);

        if (currentMediaUri == null) {
            Log.e(TAG, "Current media URI is null after processing intent/saved state.");
            finishWithError();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentMediaTitle);
        }

        setupActionButtons();
        setupPlayerViewListeners();
    }

    private void handleIntentAndSavedState(Intent intent, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            playbackPosition = savedInstanceState.getLong("playbackPosition", 0);
            playWhenReady = savedInstanceState.getBoolean("playWhenReady", true);
            currentWindowIndex = savedInstanceState.getInt("currentWindowIndex", 0);
            shuffleModeEnabled = savedInstanceState.getBoolean("shuffleModeEnabled", false);
            ArrayList<String> uriStrings = savedInstanceState.getStringArrayList("mediaUriList");
            mediaTitleList = savedInstanceState.getStringArrayList("mediaTitleList");
            if (uriStrings != null) {
                mediaUriList = new ArrayList<>();
                for(String s : uriStrings) mediaUriList.add(Uri.parse(s));
            }
            Log.d(TAG, "Restoring state: Position=" + playbackPosition + ", PlayWhenReady=" + playWhenReady + ", Index=" + currentWindowIndex + ", Shuffle=" + shuffleModeEnabled);
        } else if (intent != null) {
            if (intent.hasExtra(EXTRA_MEDIA_URI_LIST)) {
                List<String> uriStrings = intent.getStringArrayListExtra(EXTRA_MEDIA_URI_LIST);
                mediaTitleList = intent.getStringArrayListExtra(EXTRA_MEDIA_TITLE_LIST);
                currentWindowIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);
                if (uriStrings != null && !uriStrings.isEmpty()) {
                    mediaUriList = new ArrayList<>();
                    for (String s : uriStrings) mediaUriList.add(Uri.parse(s));
                }
            } else if (intent.hasExtra(EXTRA_MEDIA_URI)) {
                Uri singleUri = intent.getParcelableExtra(EXTRA_MEDIA_URI);
                String singleTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
                if (singleUri != null) {
                    mediaUriList = new ArrayList<>();
                    mediaUriList.add(singleUri);
                    mediaTitleList = new ArrayList<>();
                    mediaTitleList.add(singleTitle != null ? singleTitle : getFileName(singleUri));
                    currentWindowIndex = 0;
                }
            }
        }

        if (mediaUriList != null && !mediaUriList.isEmpty() && currentWindowIndex < mediaUriList.size()) {
            currentMediaUri = mediaUriList.get(currentWindowIndex);
            currentMediaTitle = (mediaTitleList != null && currentWindowIndex < mediaTitleList.size())
                    ? mediaTitleList.get(currentWindowIndex)
                    : getFileName(currentMediaUri);
        } else {
            if (intent != null && intent.hasExtra(EXTRA_MEDIA_URI)) {
                currentMediaUri = intent.getParcelableExtra(EXTRA_MEDIA_URI);
                currentMediaTitle = intent.getStringExtra(EXTRA_MEDIA_TITLE);
                if (currentMediaTitle == null || currentMediaTitle.isEmpty()) {
                    currentMediaTitle = getFileName(currentMediaUri);
                }
                if (currentMediaUri != null) {
                    mediaUriList = new ArrayList<>();
                    mediaUriList.add(currentMediaUri);
                    mediaTitleList = new ArrayList<>();
                    mediaTitleList.add(currentMediaTitle);
                    currentWindowIndex = 0;
                }
            }
        }

        if (currentMediaUri != null) {
            String scheme = currentMediaUri.getScheme();
            isHttpUri = scheme != null && (scheme.equals("http") || scheme.equals("https"));
            canBeAdded = scheme != null && (scheme.equals("content") || scheme.equals("file") || isHttpUri);
        } else {
            isHttpUri = false;
            canBeAdded = false;
        }
    }
    private void finishWithError() {
        Toast.makeText(this, R.string.error_media_uri_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void initializePlayer() {
        if (player == null && mediaUriList != null && !mediaUriList.isEmpty()) {
            try {
                Log.d(TAG, "Initializing player with " + mediaUriList.size() + " items.");
                player = new ExoPlayer.Builder(this).build();
                player.addListener(playerListener);
                playerView.setPlayer(player);

                List<MediaItem> exoMediaItems = new ArrayList<>();
                for (int i = 0; i < mediaUriList.size(); i++) {
                    Uri uri = mediaUriList.get(i);
                    String title = (mediaTitleList != null && i < mediaTitleList.size()) ? mediaTitleList.get(i) : getFileName(uri);
                    Bundle extras = new Bundle();
                    extras.putString("title", title);

                    exoMediaItems.add(
                            new MediaItem.Builder()
                                    .setUri(uri)
                                    .setMediaId(uri.toString())
                                    .setTag(extras)
                                    .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                                            .setTitle(title)
                                            .build())
                                    .build()
                    );
                }

                player.setMediaItems(exoMediaItems, currentWindowIndex, playbackPosition);
                player.setPlayWhenReady(playWhenReady);
                player.setShuffleModeEnabled(shuffleModeEnabled);
                player.prepare();
                Log.d(TAG, "Player prepared, starting at index " + currentWindowIndex + ", position " + playbackPosition);

            } catch (Exception e) {
                Log.e(TAG, "Error initializing player", e);
                Toast.makeText(this, "Error initializing player: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else if (mediaUriList == null || mediaUriList.isEmpty()) {
            Log.e(TAG, "Cannot initialize player, media list is null or empty.");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            currentWindowIndex = player.getCurrentMediaItemIndex();
            shuffleModeEnabled = player.getShuffleModeEnabled();
            player.removeListener(playerListener);
            player.release();
            player = null;
            playerView.setPlayer(null);
            Log.d(TAG, "Player released. Saved state: Position=" + playbackPosition + ", PlayWhenReady=" + playWhenReady + ", Index=" + currentWindowIndex + ", Shuffle=" + shuffleModeEnabled);
        }
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            Log.e(TAG, "Player Error: code=" + error.errorCode + " message=" + error.getMessage(), error);
            Toast.makeText(PlayerActivity.this, "Playback Error: " + error.errorCode, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            String stateString;
            switch (playbackState) { /* ... */ }
        }

        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (player == null) return;
            currentWindowIndex = player.getCurrentMediaItemIndex();

            if (mediaItem != null) {
                currentMediaUri = mediaItem.localConfiguration != null ? mediaItem.localConfiguration.uri : null;
                if (mediaItem.localConfiguration != null && mediaItem.localConfiguration.tag instanceof Bundle) {
                    currentMediaTitle = ((Bundle) mediaItem.localConfiguration.tag).getString("title", getFileName(currentMediaUri));
                } else if (mediaItem.mediaMetadata.title != null) {
                    currentMediaTitle = mediaItem.mediaMetadata.title.toString();
                } else {
                    currentMediaTitle = getFileName(currentMediaUri);
                }

                Log.d(TAG, "Transition to index: " + currentWindowIndex + ", Title: " + currentMediaTitle);

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(currentMediaTitle);
                }
                if (currentMediaUri != null) {
                    String scheme = currentMediaUri.getScheme();
                    isHttpUri = scheme != null && (scheme.equals("http") || scheme.equals("https"));
                    canBeAdded = scheme != null && (scheme.equals("content") || scheme.equals("file") || isHttpUri);
                    setupActionButtons();
                }
            } else {
                currentMediaUri = null;
                currentMediaTitle = getString(R.string.unknown_media_title);
                if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentMediaTitle);
                isHttpUri = false;
                canBeAdded = false;
                setupActionButtons();
            }
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            PlayerActivity.this.shuffleModeEnabled = shuffleModeEnabled;
            Log.d(TAG, "Shuffle mode changed to: " + shuffleModeEnabled);
            Toast.makeText(PlayerActivity.this, "Shuffle " + (shuffleModeEnabled ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
        }
    };
    private void setupActionButtons() {
        if (btnDownloadMedia != null) {
            btnDownloadMedia.setVisibility(isHttpUri ? View.VISIBLE : View.GONE);
            btnDownloadMedia.setOnClickListener(v -> {
                if (currentMediaUri != null && isHttpUri) {
                    Log.d(TAG, "Download button clicked for: " + currentMediaUri);
                    startDownloadAndAddToDownloads(currentMediaUri);
                }
            });
        }
        if (btnAddMediaToPlaylist != null) {
            btnAddMediaToPlaylist.setVisibility(canBeAdded ? View.VISIBLE : View.GONE);
            btnAddMediaToPlaylist.setOnClickListener(v -> {
                if (currentMediaUri != null && canBeAdded) {
                    Log.d(TAG, "Add to Playlist button clicked for: " + currentMediaUri);
                    showAddToPlaylistDialog();
                }
            });
        }
    }

    private void setupPlayerViewListeners() {
        playerView.setControllerVisibilityListener(
                (PlayerView.ControllerVisibilityListener) visibility -> {
                    Log.d(TAG, "Controller visibility changed: " + visibility);
                    if (actionButtonsLayout != null) {
                        actionButtonsLayout.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE);
                    }
                    if (getSupportActionBar() != null) {
                        if (visibility == View.VISIBLE) getSupportActionBar().show();
                        else getSupportActionBar().hide();
                    }
                }
        );

        View shuffleButton = playerView.findViewById(androidx.media3.ui.R.id.exo_shuffle);
        if (shuffleButton != null) {
            shuffleButton.setOnClickListener(v -> {
                if (player != null) {
                    boolean currentShuffle = player.getShuffleModeEnabled();
                    player.setShuffleModeEnabled(!currentShuffle);
                }
            });
        } else {
            Log.w(TAG,"Shuffle button not found in PlayerView controls");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAddToPlaylistDialog() {
        if (currentMediaUri == null || !canBeAdded) {
            Toast.makeText(this, "Nothing to add to playlist", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            final List<Playlist> playlists = mediaDao.getAllPlaylistsSync();
            mainHandler.post(() -> {
                if (playlists == null || playlists.isEmpty()) {
                    Toast.makeText(this, R.string.no_playlists_available, Toast.LENGTH_SHORT).show();
                    return;
                }
                CharSequence[] playlistNames = new CharSequence[playlists.size()];
                for (int i = 0; i < playlists.size(); i++) {
                    playlistNames[i] = playlists.get(i).getPlaylistName();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.add_to_playlist_dialog_title, currentMediaTitle))
                        .setItems(playlistNames, (dialog, which) -> {
                            Playlist selectedPlaylist = playlists.get(which);
                            addMediaToSelectedPlaylist(selectedPlaylist.getPlaylistId());
                        })
                        .setNegativeButton(R.string.cancel_button, null);
                builder.show();
            });
        });
    }

    private void addMediaToSelectedPlaylist(long playlistId) {
        if (currentMediaUri == null) return;
        String finalTitle = (this.currentMediaTitle != null && !this.currentMediaTitle.isEmpty() && !this.currentMediaTitle.equals(getString(R.string.unknown_media_title)))
                ? this.currentMediaTitle
                : getFileName(currentMediaUri);

        com.example.lab4.db.MediaItem newItem =
                new com.example.lab4.db.MediaItem(currentMediaUri.toString(), finalTitle, playlistId);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            mediaDao.insertMediaItem(newItem);
            Log.d(TAG, "Added URI '" + finalTitle + "' to playlist ID: " + playlistId);
            mainHandler.post(() -> Toast.makeText(PlayerActivity.this, getString(R.string.added_to_playlist_toast, finalTitle), Toast.LENGTH_SHORT).show());
        });
    }

    private void startDownloadAndAddToDownloads(Uri uriToDownload) {
        if (uriToDownload == null) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Playlist downloadsPl = mediaDao.getPlaylistByName("Downloads");
            long downloadsId;
            if (downloadsPl == null) {
                downloadsId = mediaDao.insertPlaylist(new Playlist("Downloads"));
            } else {
                downloadsId = downloadsPl.playlistId;
            }
            if (downloadsId > 0) {
                startDownloadInternal(uriToDownload, downloadsId);
            } else {
                mainHandler.post(() -> Toast.makeText(PlayerActivity.this, "Failed to create/find Downloads playlist", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startDownloadInternal(Uri uriToDownload, long targetPlaylistId) {
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            mainHandler.post(() -> Toast.makeText(PlayerActivity.this, R.string.download_manager_unavailable_toast, Toast.LENGTH_SHORT).show());
            return;
        }
        String fileName = getFileName(uriToDownload);
        String description = "PlaylistID:" + targetPlaylistId + ";" + getString(R.string.download_notification_description);

        DownloadManager.Request request = new DownloadManager.Request(uriToDownload);
        request.setTitle(fileName);
        request.setDescription(description);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Lab4Media/" + fileName);
        request.allowScanningByMediaScanner();
        try {
            long downloadId = downloadManager.enqueue(request);
            mainHandler.post(() -> Toast.makeText(PlayerActivity.this, getString(R.string.download_started_toast, fileName), Toast.LENGTH_SHORT).show());
            Log.d(TAG, "Enqueued download ID: " + downloadId + " targeted for playlist ID: " + targetPlaylistId);
        } catch (Exception e) {
            Log.e(TAG, "Error starting download", e);
            mainHandler.post(() -> Toast.makeText(PlayerActivity.this, R.string.error_starting_download_toast, Toast.LENGTH_SHORT).show());
        }
    }
    @Override
    protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            initializePlayer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && player == null) {
            initializePlayer();
        }
        if (playerView != null) {
            hideSystemUi();
        }
        if (player != null && playWhenReady) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            releasePlayer();
        } else {
            if (player != null) {
                playbackPosition = player.getCurrentPosition();
                playWhenReady = player.getPlayWhenReady();
                currentWindowIndex = player.getCurrentMediaItemIndex();
                shuffleModeEnabled = player.getShuffleModeEnabled();
                player.pause();
                Log.d(TAG, "onPause: Saving state before potential stop. Pos=" + playbackPosition);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            releasePlayer();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("playbackPosition", playbackPosition);
        outState.putBoolean("playWhenReady", playWhenReady);
        outState.putInt("currentWindowIndex", currentWindowIndex);
        outState.putBoolean("shuffleModeEnabled", shuffleModeEnabled);
        if (mediaUriList != null) {
            ArrayList<String> uriStrings = new ArrayList<>();
            for (Uri u : mediaUriList) uriStrings.add(u.toString());
            outState.putStringArrayList("mediaUriList", uriStrings);
            outState.putStringArrayList("mediaTitleList", mediaTitleList);
        }
        Log.d(TAG, "Saving state: Pos=" + playbackPosition + ", PlayWhenReady=" + playWhenReady + ", Index=" + currentWindowIndex + ", Shuffle=" + shuffleModeEnabled);
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            playerView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private String getFileName(Uri uri) {
        if (uri == null) return getString(R.string.unknown_media_title);
        String result = null;
        String scheme = uri.getScheme();

        if ("content".equals(scheme)) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (SecurityException se) {
                Log.w(TAG, "Permission Denial getting filename for content URI (likely DownloadManager URI after restart): " + uri);
            } catch (Exception e) {
                Log.e(TAG, "Error getting filename from ContentResolver for URI: "+ uri, e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
                try { result = java.net.URLDecoder.decode(result, "UTF-8"); } catch (Exception e) { /* ignore */ }
                int queryParamIndex = result.indexOf('?'); if (queryParamIndex > 0) result = result.substring(0, queryParamIndex);
            }
        }
        return (result != null && !result.isEmpty()) ? result : getString(R.string.unknown_media_title);
    }
}