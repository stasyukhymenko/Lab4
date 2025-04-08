package com.example.lab4;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.ui.PlayerNotificationManager;

import com.example.lab4.db.AppDatabase;
import com.example.lab4.db.MediaDao;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class PlaybackService extends MediaSessionService {

    private static final String TAG = "PlaybackService";
    private static final String NOTIFICATION_CHANNEL_ID = "lab4_playback_channel";
    private static final int NOTIFICATION_ID = 1234;

    private MediaSession mediaSession;
    private ExoPlayer player;
    private MediaDao mediaDao;
    private PlayerNotificationManager playerNotificationManager;

    public static final String ACTION_PLAY_PLAYLIST = "com.example.lab4.ACTION_PLAY_PLAYLIST";
    public static final String EXTRA_PLAYLIST_ID = "com.example.lab4.EXTRA_PLAYLIST_ID";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mediaDao = AppDatabase.getInstance(this).mediaDao();
        initializePlayerAndSession();
        createNotificationChannel();
        initializeNotificationManager();
    }

    private void initializePlayerAndSession() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build();
            player.setAudioAttributes(audioAttributes, true);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "Player state changed: " + playbackState);
                }
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    Log.d(TAG, "Media item transition, reason: " + reason);
                }
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "IsPlaying changed: " + isPlaying);
                }
                @Override
                public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                    Log.e(TAG, "Player Error: " + error.getMessage(), error);
                }
            });
        }

        if (mediaSession == null) {
            Intent sessionActivityIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, sessionActivityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            mediaSession = new MediaSession.Builder(this, player)
                    .setSessionActivity(pendingIntent)
                    .build();
            Log.d(TAG, "MediaSession created.");
        }
    }

    private void initializeNotificationManager() {
        PlayerNotificationManager.Builder builder =
                new PlayerNotificationManager.Builder(this, NOTIFICATION_ID, NOTIFICATION_CHANNEL_ID)
                        .setChannelNameResourceId(R.string.playback_channel_name)
                        .setChannelDescriptionResourceId(R.string.playback_channel_description);

        builder.setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
            @Override
            public CharSequence getCurrentContentTitle(@NonNull Player player) {
                MediaItem currentItem = player.getCurrentMediaItem();
                if (currentItem != null && currentItem.mediaMetadata.title != null) {
                    return currentItem.mediaMetadata.title;
                }
                return getString(R.string.unknown_media_title);
            }

            @Nullable
            @Override
            public PendingIntent createCurrentContentIntent(@NonNull Player player) {
                Intent intent = new Intent(PlaybackService.this, MainActivity.class);
                return PendingIntent.getActivity(PlaybackService.this, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }

            @Nullable
            @Override
            public CharSequence getCurrentContentText(@NonNull Player player) {
                return null;
            }

            @Nullable
            @Override
            public Bitmap getCurrentLargeIcon(@NonNull Player player, @NonNull PlayerNotificationManager.BitmapCallback callback) {
                return null;
            }
        });

        builder.setNotificationListener(new PlayerNotificationManager.NotificationListener() {
            @Override
            public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
                if (ongoing) {
                    ContextCompat.startForegroundService(PlaybackService.this, new Intent(PlaybackService.this, PlaybackService.class));
                    startForeground(notificationId, notification);
                    Log.d(TAG, "Notification posted, starting foreground.");
                } else {
                    stopForeground(false);
                    Log.d(TAG, "Notification posted, but not ongoing. Stopping foreground.");
                }
            }

            @Override
            public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                Log.d(TAG, "Notification cancelled. Dismissed by user: " + dismissedByUser);
                stopSelf();
            }
        });

        playerNotificationManager = builder.build();
        playerNotificationManager.setPlayer(player);
        playerNotificationManager.setUseNextActionInCompactView(true);
        playerNotificationManager.setUsePreviousActionInCompactView(true);

        Log.d(TAG, "PlayerNotificationManager initialized.");
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand received: " + (intent != null ? intent.getAction() : "null intent"));

        if (intent != null && ACTION_PLAY_PLAYLIST.equals(intent.getAction())) {
            long playlistId = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1);
            if (playlistId != -1) {
                Log.d(TAG, "Request to load playlist ID: " + playlistId);
                loadPlaylistAndPlay(playlistId);
            } else {
                Log.w(TAG, "Playlist ID not provided in intent.");
            }
        }
        return START_STICKY;
    }

    private void loadPlaylistAndPlay(long playlistId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            final List<com.example.lab4.db.MediaItem>[] dbMediaItemsHolder = new List[1];
            mainHandler.post(() -> {
                mediaDao.getMediaItemsForPlaylist(playlistId).observe((LifecycleOwner) this, items -> {
                    dbMediaItemsHolder[0] = items;
                    AppDatabase.databaseWriteExecutor.execute(() -> processLoadedItems(dbMediaItemsHolder[0], playlistId));
                });
            });
        });
    }

    private void processLoadedItems(List<com.example.lab4.db.MediaItem> dbMediaItems, long playlistId) {
        if (dbMediaItems == null || dbMediaItems.isEmpty()) {
            Log.w(TAG, "No media items found for playlist ID: " + playlistId + " after observing.");
            mainHandler.post(this::stopSelf);
            return;
        }

        Log.d(TAG, "Processing " + dbMediaItems.size() + " items for playlist ID: " + playlistId);

        List<MediaItem> exoMediaItems = new ArrayList<>();
        for (com.example.lab4.db.MediaItem dbItem : dbMediaItems) {
            try {
                if (dbItem.mediaUri == null || dbItem.mediaUri.isEmpty()) {
                    Log.w(TAG, "Skipping item with null or empty URI for mediaId: " + dbItem.mediaId);
                    continue;
                }
                Uri uri = Uri.parse(dbItem.mediaUri);
                String title = (dbItem.mediaTitle != null && !dbItem.mediaTitle.isEmpty())
                        ? dbItem.mediaTitle
                        : extractFileNameFromUri(uri);

                exoMediaItems.add(
                        new MediaItem.Builder()
                                .setUri(uri)
                                .setMediaId(String.valueOf(dbItem.mediaId))
                                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(title)
                                        .build())
                                .build()
                );
            } catch (Exception e) {
                Log.e(TAG, "Error creating ExoPlayer MediaItem from DB URI: " + dbItem.mediaUri, e);
            }
        }

        if (!exoMediaItems.isEmpty()) {
            mainHandler.post(() -> {
                if (player == null) initializePlayerAndSession();
                if (player != null) {
                    Log.d(TAG, "Setting " + exoMediaItems.size() + " items to player.");
                    player.setMediaItems(exoMediaItems, true);
                    player.prepare();
                    player.play();
                } else {
                    Log.e(TAG, "Player is null, cannot start playback");
                }
            });
        } else {
            Log.w(TAG, "No valid ExoPlayer MediaItems created for playlist ID: " + playlistId);
            mainHandler.post(this::stopSelf);
        }
    }

    private String extractFileNameFromUri(Uri uri) {
        if (uri == null) return getString(R.string.unknown_media_title);
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not get display name from content URI: " + uri, e);
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
            if (result != null && result.contains("/")) {
                result = result.substring(result.lastIndexOf('/') + 1);
            }
        }
        return result != null ? result : getString(R.string.unknown_media_title);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.playback_channel_name),
                    importance);
            channel.setDescription(getString(R.string.playback_channel_description));
            channel.setSound(null, null);
            channel.enableVibration(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created or already exists.");
            } else {
                Log.e(TAG, "Failed to get NotificationManager.");
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved - Task removed by user.");
        if (player != null && !player.getPlayWhenReady()) {
            Log.d(TAG, "Playback paused, stopping service.");
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy - Releasing resources.");
        releaseResources();
        super.onDestroy();
    }

    private void releaseResources() {
        mainHandler.removeCallbacksAndMessages(null);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
            Log.d(TAG, "MediaSession released.");
        }
        if (playerNotificationManager != null) {
            playerNotificationManager.setPlayer(null);
            playerNotificationManager = null;
            Log.d(TAG, "PlayerNotificationManager released.");
        }
        if (player != null) {
            player.release();
            player = null;
            Log.d(TAG, "ExoPlayer released.");
        }
        Log.d(TAG, "Service resources released.");
    }
}