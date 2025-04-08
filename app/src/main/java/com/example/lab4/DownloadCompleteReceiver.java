package com.example.lab4;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import com.example.lab4.db.AppDatabase;
import com.example.lab4.db.MediaDao;
import com.example.lab4.db.Playlist;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public class DownloadCompleteReceiver extends BroadcastReceiver {

    private static final String TAG = "DownloadReceiver";
    private static final String PLAYLIST_ID_PREFIX = "PlaylistID:";
    private static final String DEFAULT_DOWNLOADS_PLAYLIST_NAME = "Downloads";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
            Log.v(TAG, "Received non-download-complete action: " + action);
            return;
        }

        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (downloadId == -1) {
            Log.e(TAG, "Received download complete action but no valid download ID.");
            return;
        }
        Log.d(TAG, "Processing download complete for ID: " + downloadId);
        handleDownloadCompletion(context.getApplicationContext(), downloadId);
    }

    private void handleDownloadCompletion(Context appContext, long downloadId) {
        DownloadManager downloadManager = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager service not available.");
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(downloadId);
        Cursor cursor = null;

        Uri downloadedFileUri = null;
        String title = null;
        String description = null;
        String mimeType = null;
        int status = -1;
        int reason = -1;

        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int titleIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE);
                int localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                int descriptionIndex = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION);
                int mediaTypeIndex = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE);
                int reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);

                if(statusIndex != -1) status = cursor.getInt(statusIndex);
                if(titleIndex != -1) title = cursor.getString(titleIndex);
                if(localUriIndex != -1) {
                    String uriString = cursor.getString(localUriIndex);
                    if (uriString != null && !uriString.isEmpty()) {
                        try {
                            downloadedFileUri = Uri.parse(uriString);
                        } catch (Exception e){
                            Log.e(TAG, "Failed to parse downloaded file URI string: " + uriString, e);
                        }
                    } else {
                        Log.w(TAG,"Downloaded file URI string is null or empty for ID: " + downloadId);
                    }
                }
                if(descriptionIndex != -1) description = cursor.getString(descriptionIndex);
                if(mediaTypeIndex != -1) mimeType = cursor.getString(mediaTypeIndex);
                if(reasonIndex != -1) reason = cursor.getInt(reasonIndex);
                if (title == null || title.isEmpty()) {
                    title = "Downloaded File " + downloadId;
                }

            } else {
                Log.w(TAG, "DownloadManager query returned no results for ID: " + downloadId);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying DownloadManager for ID: " + downloadId, e);
            return;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            if (downloadedFileUri != null) {
                long targetPlaylistId = extractPlaylistIdFromDescription(description);
                Log.i(TAG, "Download successful: '" + title + "' Temp URI: " + downloadedFileUri + " Target Playlist ID from desc: " + targetPlaylistId);
                copyFileToInternalStorageAndAddToDb(appContext, downloadedFileUri, title, mimeType, targetPlaylistId);
            } else {
                Log.e(TAG, "Download successful status but local URI is null for ID: " + downloadId + " Title: " + title);
                downloadManager.remove(downloadId);
            }
        } else {
            Log.w(TAG, "Download failed for ID: " + downloadId + " ('" + title + "'). Status: " + status + ", Reason: " + getDownloadErrorReason(reason));
            downloadManager.remove(downloadId);
        }
    }

    private void copyFileToInternalStorageAndAddToDb(Context appContext, Uri sourceUri, String originalTitle, String mimeType, long targetPlaylistIdFromDesc) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            String finalFileName = originalTitle;
            if (finalFileName == null || finalFileName.isEmpty()) {
                finalFileName = "downloaded_media_" + System.currentTimeMillis();
            }
            String extension = getExtensionFromMimeType(mimeType);
            String safeBaseName = finalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (safeBaseName.length() > 50) {
                safeBaseName = safeBaseName.substring(0, 50);
            }
            String uniqueInternalFileName = "media_" + System.currentTimeMillis() + "_" + safeBaseName
                    + (extension != null ? "." + extension : ".file");

            File internalDir = appContext.getFilesDir();
            if (!internalDir.exists()) {
                if (!internalDir.mkdirs()) {
                    Log.e(TAG, "Failed to create internal storage directory: " + internalDir.getAbsolutePath());
                    return;
                }
            }
            File destinationFile = new File(internalDir, uniqueInternalFileName);

            boolean copySuccessful = false;
            Uri internalFileUri = null;

            Log.d(TAG, "Attempting to copy from " + sourceUri + " to " + destinationFile.getAbsolutePath());
            try (InputStream inputStream = appContext.getContentResolver().openInputStream(sourceUri);
                 OutputStream outputStream = new FileOutputStream(destinationFile)) {

                Objects.requireNonNull(inputStream, "Unable to open input stream for source URI: " + sourceUri);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesCopied = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesCopied += bytesRead;
                }
                outputStream.flush();
                copySuccessful = true;
                internalFileUri = Uri.fromFile(destinationFile);
                Log.i(TAG, "File copied successfully (" + totalBytesCopied + " bytes). Internal URI: " + internalFileUri);

            } catch (IOException | SecurityException | NullPointerException e) {
                Log.e(TAG, "Error copying downloaded file from " + sourceUri + " to " + destinationFile.getAbsolutePath(), e);
                if (destinationFile.exists()) {
                    if (destinationFile.delete()) {
                        Log.d(TAG, "Deleted partially copied file: " + destinationFile.getName());
                    } else {
                        Log.w(TAG, "Failed to delete partially copied file: " + destinationFile.getName());
                    }
                }
            }

            if (copySuccessful && internalFileUri != null) {
                addDbEntry(appContext, finalFileName, internalFileUri.toString(), targetPlaylistIdFromDesc);
            } else {
                Log.e(TAG, "Skipping DB entry because file copy failed or internal URI is null.");
            }
        });
    }

    private void addDbEntry(Context appContext, String title, String internalFileUriString, long targetPlaylistIdFromDesc) {
        MediaDao dao = AppDatabase.getInstance(appContext).mediaDao();
        long finalPlaylistId = -1;
        boolean useDefaultDownloads = false;

        if (targetPlaylistIdFromDesc > 0) {
            Playlist targetPlaylist = dao.getPlaylistById(targetPlaylistIdFromDesc);
            if (targetPlaylist != null) {
                finalPlaylistId = targetPlaylistIdFromDesc;
                Log.d(TAG,"Target playlist ID " + finalPlaylistId + " from description exists.");
            } else {
                Log.w(TAG,"Target playlist ID " + targetPlaylistIdFromDesc + " from description NOT FOUND. Falling back to 'Downloads'.");
                useDefaultDownloads = true;
            }
        } else {
            Log.w(TAG,"Target playlist ID not found in description (" + targetPlaylistIdFromDesc + "). Using 'Downloads'.");
            useDefaultDownloads = true;
        }

        if (useDefaultDownloads) {
            Playlist downloadsPlaylist = dao.getPlaylistByName(DEFAULT_DOWNLOADS_PLAYLIST_NAME);
            if (downloadsPlaylist != null) {
                finalPlaylistId = downloadsPlaylist.playlistId;
                Log.d(TAG, "Found existing 'Downloads' playlist with ID: " + finalPlaylistId);
            } else {
                Playlist newDownloads = new Playlist(DEFAULT_DOWNLOADS_PLAYLIST_NAME);
                finalPlaylistId = dao.insertPlaylist(newDownloads);
                if (finalPlaylistId > 0) {
                    Log.i(TAG, "Created default '" + DEFAULT_DOWNLOADS_PLAYLIST_NAME + "' playlist with ID: " + finalPlaylistId);
                } else {
                    Log.e(TAG, "Failed to create default 'Downloads' playlist in DB.");
                }
            }
        }

        if (finalPlaylistId <= 0) {
            Log.e(TAG, "Failed to determine a valid final playlist ID (" + finalPlaylistId + "). Cannot add downloaded item.");
            try {
                File internalFile = new File(Uri.parse(internalFileUriString).getPath());
                if (internalFile.exists()) internalFile.delete();
            } catch (Exception e) { Log.e(TAG, "Error deleting orphaned copied file", e); }
            return;
        }
        String finalTitle = title;
        if (finalTitle != null && finalTitle.length() > 200) {
            finalTitle = finalTitle.substring(0, 200);
        }

        com.example.lab4.db.MediaItem newItem =
                new com.example.lab4.db.MediaItem(internalFileUriString, finalTitle, finalPlaylistId);
        try {
            dao.insertMediaItem(newItem);
            Log.i(TAG, "DB entry added for '" + finalTitle + "' (Internal URI: " + internalFileUriString + ") to playlist ID: " + finalPlaylistId + (useDefaultDownloads ? " (Downloads fallback)" : " (From description)"));
        } catch (Exception e) {
            Log.e(TAG, "Error inserting MediaItem into database", e);
            try {
                File internalFile = new File(Uri.parse(internalFileUriString).getPath());
                if (internalFile.exists()) internalFile.delete();
            } catch (Exception inner_e) { Log.e(TAG, "Error deleting copied file after DB insert error", inner_e); }
        }
    }

    private long extractPlaylistIdFromDescription(String description) {
        if (description != null && description.startsWith(PLAYLIST_ID_PREFIX)) {
            try {
                int endOfId = description.indexOf(';');
                String idString = (endOfId > 0)
                        ? description.substring(PLAYLIST_ID_PREFIX.length(), endOfId)
                        : description.substring(PLAYLIST_ID_PREFIX.length());
                return Long.parseLong(idString.trim());
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                Log.e(TAG, "Could not parse Playlist ID from description: '" + description + "'", e);
            }
        }
        Log.w(TAG, "Playlist ID prefix not found in description: '" + description + "'");
        return -1;
    }

    private String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        if (mimeType.equalsIgnoreCase("audio/mpeg")) return "mp3";
        if (mimeType.equalsIgnoreCase("audio/aac")) return "aac";
        if (mimeType.equalsIgnoreCase("audio/ogg")) return "ogg";
        if (mimeType.equalsIgnoreCase("audio/wav")) return "wav";
        if (mimeType.equalsIgnoreCase("audio/mp4")) return "m4a";
        if (mimeType.startsWith("audio/")) return "audio";
        if (mimeType.equalsIgnoreCase("video/mp4")) return "mp4";
        if (mimeType.equalsIgnoreCase("video/3gpp")) return "3gp";
        if (mimeType.equalsIgnoreCase("video/webm")) return "webm";
        if (mimeType.equalsIgnoreCase("video/x-matroska")) return "mkv";
        if (mimeType.startsWith("video/")) return "video";
        if (mimeType.equalsIgnoreCase("image/jpeg")) return "jpg";
        if (mimeType.equalsIgnoreCase("image/png")) return "png";
        if (mimeType.startsWith("image/")) return "img";
        if (mimeType.equalsIgnoreCase("application/octet-stream")) return "bin";

        Log.w(TAG, "Unknown MIME type, cannot determine extension: " + mimeType);
        return null;
    }

    private String getDownloadErrorReason(int reason) {
        switch (reason) {
            case DownloadManager.ERROR_CANNOT_RESUME: return "ERROR_CANNOT_RESUME";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return "ERROR_DEVICE_NOT_FOUND";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return "ERROR_FILE_ALREADY_EXISTS";
            case DownloadManager.ERROR_FILE_ERROR: return "ERROR_FILE_ERROR";
            case DownloadManager.ERROR_HTTP_DATA_ERROR: return "ERROR_HTTP_DATA_ERROR";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return "ERROR_INSUFFICIENT_SPACE";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return "ERROR_TOO_MANY_REDIRECTS";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return "ERROR_UNHANDLED_HTTP_CODE";
            case DownloadManager.ERROR_UNKNOWN: return "ERROR_UNKNOWN";
            default: return "Unknown Error Code (" + reason + ")";
        }
    }
}