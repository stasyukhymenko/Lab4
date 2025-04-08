package com.example.lab4;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private EditText etMediaUrl;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private ActivityResultLauncher<Intent> playlistActivityLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSelectAudio = findViewById(R.id.btnSelectAudio);
        Button btnSelectVideo = findViewById(R.id.btnSelectVideo);
        etMediaUrl = findViewById(R.id.etMediaUrl);
        Button btnPlayUrl = findViewById(R.id.btnPlayUrl);
        Button btnViewPlaylists = findViewById(R.id.btnViewPlaylists);

        initializeOpenDocumentLauncher();

        playlistActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri selectedMediaUri = result.getData().getParcelableExtra(PlayerActivity.EXTRA_MEDIA_URI);
                        if (selectedMediaUri != null) {
                            Log.d(TAG, "Returned from PlaylistActivity with media URI: " + selectedMediaUri);
                        }
                    }
                });

        btnSelectAudio.setOnClickListener(v -> selectFile(new String[]{"audio/*"}));
        btnSelectVideo.setOnClickListener(v -> selectFile(new String[]{"video/*"}));

        btnPlayUrl.setOnClickListener(v -> playFromUrl());

        btnViewPlaylists.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PlaylistActivity.class);
            playlistActivityLauncher.launch(intent);
        });
    }
    private void initializeOpenDocumentLauncher() {
        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        Log.d(TAG, "SAF file selected in MainActivity: " + uri.toString());
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            Log.i(TAG, "Persistable read permission granted for URI: " + uri);
                            launchPlayer(uri);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Failed to take persistable URI permission for: " + uri, e);
                            Toast.makeText(this, "Failed to get permanent access permission. Playback might fail later.", Toast.LENGTH_LONG).show();
                            launchPlayer(uri);
                        }
                    } else {
                        Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void selectFile(String[] mimeTypes) {
        try {
            openDocumentLauncher.launch(mimeTypes);
        } catch (Exception e) {
            Log.e(TAG, "Error launching document picker", e);
            Toast.makeText(this, "Cannot open file picker", Toast.LENGTH_SHORT).show();
        }
    }
    private void playFromUrl() {
        String url = etMediaUrl.getText().toString().trim();
        if (!TextUtils.isEmpty(url)) {
            try {
                Uri parsedUri = Uri.parse(url);
                String scheme = parsedUri.getScheme();
                if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("rtsp"))) {
                    launchPlayer(parsedUri);
                } else {
                    Toast.makeText(this, "Unsupported or invalid URL scheme", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Invalid URL format", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error parsing URL: " + url, e);
            }
        } else {
            Toast.makeText(this, R.string.enter_url_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchPlayer(Uri mediaUri) {
        if (mediaUri == null) {
            Log.e(TAG, "Attempted to launch player with null URI.");
            Toast.makeText(this, R.string.error_media_uri_not_found, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_URI, mediaUri);
        String title = getFileName(mediaUri);
        intent.putExtra(PlayerActivity.EXTRA_MEDIA_TITLE, title);
        Log.d(TAG, "Launching PlayerActivity with URI: " + mediaUri + ", Title: " + title);
        startActivity(intent);
    }

    private String getFileName(Uri uri) {
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
                        Log.d(TAG, "Filename from ContentResolver: " + result + " for URI: " + uri);
                    } else {
                        Log.w(TAG, "DISPLAY_NAME column not found for content URI: " + uri);
                    }
                } else {
                    Log.w(TAG, "ContentResolver query returned null or empty cursor for URI: " + uri);
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
                Log.d(TAG, "Filename from path: " + result + " for URI: " + uri);
            }
        }
        return (result != null && !result.isEmpty()) ? result : getString(R.string.unknown_media_title);
    }
}