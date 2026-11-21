package com.yedidyabc1000.nagan44;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * A small, keyboard-first music player designed for Android 4.4 devices.
 * It intentionally uses platform widgets only (no AndroidX/AppCompat).
 */
public class MainActivity extends Activity implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private static final int REQUEST_STORAGE = 410;
    private static final int TAB_SONGS = 0;
    private static final int TAB_ARTISTS = 1;
    private static final int TAB_ALBUMS = 2;
    private static final int TAB_PLAYLISTS = 3;
    private static final int TAB_NOW_PLAYING = 4;
    private static final int TAB_MIXES = 5;
    private static final int MIX_ACTION_BASE = 100;
    private static final int MIX_HAPPY = 101;
    private static final int MIX_SAD = 102;
    private static final int MIX_THOUGHTFUL = 103;
    private static final int MIX_RELAXED = 104;
    private static final int MIX_ENERGETIC = 105;
    private static final int MIX_DRAMATIC = 106;
    private static final int REQUEST_IMPORT_PLAYLIST = 411;
    private static final int CUSTOM_PLAYLIST_BASE = 1000;
    private static final int COLOR_DARK = 0xff252525;
    private static final int COLOR_BAR = 0xff383838;
    private static final int COLOR_ACCENT = 0xff00b9cc;
    private static final int COLOR_BG = 0xffcfd3d7;
    private static final int COLOR_ROW = 0xffe1e4e7;
    private static final int COLOR_TEXT = 0xff202020;
    private static final int COLOR_SUB = 0xff777777;
    private static final String PREFS = "nagan_player_stats";
    private static final String LIBRARY_CACHE_KEY = "library_cache_v1";
    private static final int MOOD_TAG_READER_VERSION = 1;
    private static final int MAX_ARTWORK_SIZE = 2400;
    private static final String TAG = "NaganMusic44";
    private static final int COLOR_FOCUS = 0xffb7d2d6;
    private static final int COLOR_FOCUS_PRESSED = 0xff9dbfc4;

    private final ArrayList<Song> allSongs = new ArrayList<Song>();
    private final ArrayList<Song> queue = new ArrayList<Song>();
    private final ArrayList<UserPlaylist> customPlaylists = new ArrayList<UserPlaylist>();
    private HashMap<Integer, ArrayList<Song>> moodMixIndex = new HashMap<Integer, ArrayList<Song>>();
    private boolean moodTagScanRunning = false;
    private int moodTagScanToken = 0;
    private final Handler handler = new Handler();
    private final Random random = new Random();
    private final HashMap<Integer, ArrayList<Entry>> browserEntriesCache = new HashMap<Integer, ArrayList<Entry>>();
    private final android.util.LruCache<Long, Bitmap> artworkCache = new android.util.LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(Long key, Bitmap bitmap) { return bitmap == null ? 0 : bitmap.getByteCount(); }
    };
    private int libraryRevision = 0;
    private int tabLoadToken = 0;

    private MediaPlayer player;
    private Song currentSong;
    private int queueIndex = -1;
    private long forcedNextSongId = -1L;
    private int activeTab = TAB_SONGS;
    private int lastContentTab = TAB_SONGS;
    private int repeatMode = 0; // 0=off, 1=all, 2=one
    private boolean shuffle = false;
    private boolean loading = false;
    private boolean showingNowPlaying = false;
    private int detailLevel = 0;
    private String detailTitle = "";
    private ArrayList<Entry> parentEntries = null;
    private String parentTitle = "";
    private int selectedPosition = 0;

    private LinearLayout root;
    private LinearLayout toolbar;
    private TextView toolbarTitle;
    private TextView toolbarBack;
    private HorizontalScrollView tabsScroll;
    private LinearLayout tabsRow;
    private ListView listView;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private TextView miniSubtitle;
    private ImageButton miniPlay;
    private BrowserAdapter adapter;
    private ArrayList<Entry> visibleEntries = new ArrayList<Entry>();

    private LinearLayout nowPlayingRoot;
    private ImageView nowArt;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowAlbum;
    private TextView elapsedText;
    private TextView durationText;
    private SeekBar seekBar;
    private TransportButton nowPlayButton;
    private TextView shuffleButton;
    private TextView repeatButton;
    private boolean userSeeking = false;
    private int seekDirection = 0;
    private int heldSeekPosition = 0;
    private int heldSeekDuration = 0;
    private boolean playerPrepared = false;
    private final Runnable heldSeekRunnable = new Runnable() {
        @Override public void run() {
            if (seekDirection == 0 || player == null || !playerPrepared) return;
            try {
                heldSeekPosition = Math.max(0, Math.min(heldSeekDuration,
                        heldSeekPosition + seekDirection * 2500));
                player.seekTo(heldSeekPosition);
                if (seekBar != null) seekBar.setProgress(heldSeekPosition);
                if (elapsedText != null) elapsedText.setText(formatTime(heldSeekPosition));
            } catch (IllegalStateException ignored) { }
            if (seekDirection != 0) handler.postDelayed(this, 450L);
        }
    };
    private ContentObserver mediaObserver;
    private final Runnable scanChangedMedia = new Runnable() {
        @Override public void run() {
            if (isFinishing() || !hasStoragePermission()) return;
            detailLevel = 0; parentEntries = null; parentTitle = "";
            loadLibraryAsync(false);
        }
    };

    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                int position = 0;
                int duration = 0;
                try {
                    position = player.getCurrentPosition();
                    duration = player.getDuration();
                } catch (IllegalStateException ignored) { }
                if (seekBar != null && !userSeeking) {
                    seekBar.setMax(Math.max(1, duration));
                    seekBar.setProgress(position);
                }
                if (elapsedText != null) elapsedText.setText(formatTime(position));
                if (durationText != null) durationText.setText(formatTime(duration));
            }
            handler.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        handler.post(progressRunnable);
        loadUserPlaylists();
        if (hasStoragePermission()) {
            ArrayList<Song> cachedSongs = readLibraryCache();
            if (cachedSongs.size() > 0) {
                reconcileLibrary(cachedSongs);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(LIBRARY_CACHE_KEY, serializeLibraryCache(allSongs)).apply();
                renderBrowser();
                prepareBrowserEntriesCacheAsync(new ArrayList<Song>(cachedSongs));
                scanMissingMoodTagsAsync();
            } else {
                loadLibraryAsync();
            }
        } else {
            renderPermissionScreen();
        }
    }

    private boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    private void renderPermissionScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(COLOR_BG);
        TextView title = text("נגן מוזיקה", 21, COLOR_TEXT, true);
        TextView help = text("כדי להציג ולהשמיע את השירים במכשיר, יש לאפשר גישה לקובצי המוזיקה.", 15, COLOR_SUB, false);
        help.setGravity(Gravity.CENTER);
        help.setPadding(0, dp(12), 0, dp(16));
        TextView button = text("אישור גישה", 16, 0xffffffff, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(COLOR_ACCENT, 6));
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestStoragePermission(); }
        });
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(help, new LinearLayout.LayoutParams(-1, -2));
        root.addView(button, new LinearLayout.LayoutParams(-2, -2));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasStoragePermission()) registerMediaObserver();
    }

    @Override protected void onPause() {
        // Keep the MediaStore observer alive while the activity remains in the background.
        seekDirection = 0; handler.removeCallbacks(heldSeekRunnable); super.onPause();
    }

    private void registerMediaObserver() {
        if (mediaObserver != null || !hasStoragePermission()) return;
        mediaObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { onChange(selfChange, null); }
            @Override public void onChange(boolean selfChange, Uri uri) {
                handler.removeCallbacks(scanChangedMedia); handler.postDelayed(scanChangedMedia, 1400L);
            }
        };
        try {
            getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
            getContentResolver().registerContentObserver(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, true, mediaObserver);
        } catch (RuntimeException error) { Log.w(TAG, "Cannot observe media library", error); }
    }

    private void unregisterMediaObserver() {
        if (mediaObserver == null) return;
        try { getContentResolver().unregisterContentObserver(mediaObserver); } catch (RuntimeException ignored) { }
        mediaObserver = null;
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMPORT_PLAYLIST && resultCode == RESULT_OK && data != null && data.getData() != null) importPlaylistFile(data.getData());
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                registerMediaObserver();
                loadLibraryAsync();
            } else {
                Toast.makeText(this, "נדרשת הרשאת אחסון כדי לסרוק שירים", Toast.LENGTH_LONG).show();
                renderPermissionScreen();
            }
        }
    }

    /**
     * Scan MediaStore away from the UI thread. Older Android devices can take
     * several seconds to answer MediaStore queries, especially on large SD cards.
     * Keeping this work off the main thread prevents the blank-screen/ANR issue.
     */
    private int libraryLoadToken = 0;

    private void loadLibraryAsync() { loadLibraryAsync(true); }

    private void loadLibraryAsync(final boolean showLoading) {
        final int token = ++libraryLoadToken;
        loading = true;
        if (showLoading && allSongs.size() == 0) renderLoadingScreen();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Song> loadedSongs = scanLibrary();
                final String cacheJson = serializeLibraryCache(loadedSongs);
                final HashMap<Integer, ArrayList<Entry>> preparedEntries = prepareBaseBrowserEntries(loadedSongs);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || token != libraryLoadToken) return;
                        reconcileLibrary(loadedSongs);
                        browserEntriesCache.putAll(preparedEntries);
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString(LIBRARY_CACHE_KEY, cacheJson).apply();
                        loading = false;
                        if (showingNowPlaying && currentSong != null) renderNowPlaying(); else renderBrowser();
                        if (showLoading && loadedSongs.size() == 0) {
                            Toast.makeText(MainActivity.this,
                                    "לא נמצאו שירים במכשיר", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }, "nagan-library-scan");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /** Read the last successful MediaStore snapshot so startup does not rescan every time. */
    private ArrayList<Song> readLibraryCache() {
        ArrayList<Song> songs = new ArrayList<Song>();
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(LIBRARY_CACHE_KEY, "");
        if (saved == null || saved.length() == 0) return songs;
        HashMap<String, Boolean> visibleDirectoryCache = new HashMap<String, Boolean>();
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                long id = item.optLong("id", -1L);
                if (id < 0) continue;
                Song song = new Song();
                song.id = id;
                song.title = item.optString("title", "ללא שם");
                song.artist = item.optString("artist", "אמן לא ידוע");
                song.album = item.optString("album", "אלבום לא ידוע");
                song.albumId = item.optLong("albumId", -1L);
                song.duration = item.optLong("duration", 0L);
                song.dateAdded = item.optLong("dateAdded", 0L);
                song.path = item.optString("path", "");
                if (song.path.length() == 0) song.path = null;
                // Cached libraries can predate the hidden-folder filter. Apply the
                // exact same visibility rule before a cached track reaches the UI.
                if (!isVisibleAudioPath(song.path, visibleDirectoryCache)) continue;
                song.albumArtPath = item.optString("albumArtPath", "");
                if (song.albumArtPath.length() == 0) song.albumArtPath = null;
                song.albumArtist = item.optString("albumArtist", "");
                if (song.albumArtist.length() == 0) song.albumArtist = null;
                song.key = item.optString("musicKey", "");
                if (song.key.length() == 0) song.key = null;
                song.bpm = item.optInt("bpm", 0);
                song.moodTagsScanned = item.optBoolean("moodTagsScanned", false);
                song.moodTagReaderVersion = item.optInt("moodTagReaderVersion", 0);
                song.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                songs.add(song);
            }
        } catch (Exception error) {
            Log.w(TAG, "Ignoring invalid music-library cache", error);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(LIBRARY_CACHE_KEY).apply();
            songs.clear();
        }
        return songs;
    }

    /** Convert scan results to a compact cache string on the worker thread. */
    private String serializeLibraryCache(ArrayList<Song> songs) {
        JSONArray array = new JSONArray();
        for (Song song : songs) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", song.id);
                item.put("title", song.title);
                item.put("artist", song.artist);
                item.put("album", song.album);
                item.put("albumId", song.albumId);
                item.put("duration", song.duration);
                item.put("dateAdded", song.dateAdded);
                item.put("path", song.path == null ? "" : song.path);
                item.put("albumArtPath", song.albumArtPath == null ? "" : song.albumArtPath);
                item.put("albumArtist", song.albumArtist == null ? "" : song.albumArtist);
                item.put("musicKey", song.key == null ? "" : song.key);
                item.put("bpm", song.bpm);
                item.put("moodTagsScanned", song.moodTagsScanned);
                item.put("moodTagReaderVersion", song.moodTagReaderVersion);
                array.put(item);
            } catch (JSONException error) {
                Log.w(TAG, "Could not cache one music item", error);
            }
        }
        return array.toString();
    }

    private void renderLoadingScreen() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(COLOR_BG);
        TextView title = text("נגן מוזיקה", 21, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        TextView message = text("סורק את ספריית המוזיקה...", 15, COLOR_SUB, false);
        message.setGravity(Gravity.CENTER);
        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-2, -2);
        progressParams.topMargin = dp(20);
        progressParams.bottomMargin = dp(12);
        root.addView(progress, progressParams);
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private ArrayList<Song> scanLibrary() {
        ArrayList<Song> songs = new ArrayList<Song>();
        Cursor cursor = null;
        // Fetch artwork paths once per album instead of querying MediaStore once
        // for every track. This is substantially faster on older devices.
        HashMap<Long, String> artworkByAlbum = new HashMap<Long, String>();
        HashMap<Long, String> albumArtistByAlbum = new HashMap<Long, String>();
        HashMap<String, Boolean> visibleDirectoryCache = new HashMap<String, Boolean>();
        Cursor albumsCursor = null;
        try {
            albumsCursor = getContentResolver().query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART, MediaStore.Audio.Albums.ARTIST},
                    null, null, null);
            if (albumsCursor != null) {
                int albumIdCol = albumsCursor.getColumnIndex(MediaStore.Audio.Albums._ID);
                int artCol = albumsCursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
                int albumArtistCol = albumsCursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
                if (albumIdCol >= 0) {
                    while (albumsCursor.moveToNext()) {
                        long albumId = albumsCursor.getLong(albumIdCol);
                        String artPath = albumsCursor.getString(artCol);
                        if (artPath != null && artPath.length() > 0) artworkByAlbum.put(albumId, artPath);
                        if (albumArtistCol >= 0) {
                            String albumArtist = albumsCursor.getString(albumArtistCol);
                            if (albumArtist != null && albumArtist.trim().length() > 0 && !"<unknown>".equalsIgnoreCase(albumArtist.trim())) albumArtistByAlbum.put(albumId, albumArtist.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Album artwork is optional; continue loading the music library.
        } finally {
            if (albumsCursor != null) albumsCursor.close();
        }

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.IS_MUSIC
        };
        try {
            String selection = MediaStore.Audio.Media.IS_MUSIC + "=1 AND "
                    + MediaStore.Audio.Media.DATA + " NOT LIKE ? AND "
                    + MediaStore.Audio.Media.DATA + " NOT LIKE ?";
            String[] selectionArgs = new String[]{"%/.%/%", "%/.%"};
            try {
                cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection, selection, selectionArgs, null);
            } catch (Exception unsupportedSelection) {
                // A few old OEM MediaStore providers reject compound DATA clauses.
                // Fall back to the broad query; isVisibleAudioPath below remains the final filter.
                Log.w(TAG, "MediaStore path selection unavailable; applying Java path filter", unsupportedSelection);
                cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection, MediaStore.Audio.Media.IS_MUSIC + "=1", null, null);
            }
            if (cursor != null) {
                int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);
                if (idCol >= 0) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idCol);
                        Song song = new Song();
                        song.id = id;
                        song.title = titleCol >= 0 ? clean(cursor.getString(titleCol), "ללא שם") : "ללא שם";
                        song.artist = artistCol >= 0 ? clean(cursor.getString(artistCol), "אמן לא ידוע") : "אמן לא ידוע";
                        song.album = albumCol >= 0 ? clean(cursor.getString(albumCol), "אלבום לא ידוע") : "אלבום לא ידוע";
                        song.albumId = albumIdCol >= 0 ? cursor.getLong(albumIdCol) : -1;
                        song.albumArtist = albumArtistByAlbum.get(song.albumId);
                        song.duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                        song.path = pathCol >= 0 ? cursor.getString(pathCol) : null;
                        if (!isVisibleAudioPath(song.path, visibleDirectoryCache)) continue;
                        song.dateAdded = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                        song.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        song.albumArtPath = artworkByAlbum.get(song.albumId);
                        readMoodTags(song);
                        songs.add(song);
                    }
                }
            }
        } catch (Exception ignored) {
            // Return any songs found so far; the UI thread remains responsive.
        } finally {
            if (cursor != null) cursor.close();
        }
        Collections.sort(songs, new Comparator<Song>() {
            @Override public int compare(Song a, Song b) { return STRING_SORT.compare(a.title, b.title); }
        });
        return songs;
    }

    /** Reject files whose own name or any ancestor directory begins with a dot. */
    private boolean isVisibleAudioPath(String path, Map<String, Boolean> directoryCache) {
        if (path == null || path.trim().length() == 0) return false;
        String normalized = path.trim().replace('\\', '/');
        if (normalized.regionMatches(true, 0, "file://", 0, 7)) {
            try { normalized = Uri.decode(Uri.parse(normalized).getPath()); }
            catch (Exception ignored) { }
        } else {
            try { normalized = Uri.decode(normalized); } catch (Exception ignored) { }
        }
        normalized = normalized.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.US);
        if (lower.contains("/android/data/") || lower.contains("/android/obb/") || lower.contains("/lost.dir/")) return false;
        if (hasDotPrefixedPathComponent(normalized)) return false;
        File file = new File(normalized);
        try {
            String canonical = file.getCanonicalPath();
            if (hasDotPrefixedPathComponent(canonical)) return false;
            file = new File(canonical);
        } catch (IOException ignored) {
            file = file.getAbsoluteFile();
        }
        File current = file;
        boolean isFile = true;
        while (current != null) {
            String name = current.getName();
            if (name != null && name.length() > 1 && name.charAt(0) == '.') return false;
            if (!isFile) {
                String key = current.getAbsolutePath();
                Boolean visible = directoryCache == null ? null : directoryCache.get(key);
                if (visible == null) {
                    visible = !new File(current, ".nomedia").exists();
                    if (directoryCache != null) directoryCache.put(key, visible);
                }
                if (!visible.booleanValue()) return false;
            }
            isFile = false;
            current = current.getParentFile();
        }
        return true;
    }

    private boolean hasDotPrefixedPathComponent(String path) {
        if (path == null) return false;
        String[] parts = path.replace('\\', '/').split("/");
        for (String part : parts) if (part.length() > 1 && part.charAt(0) == '.') return true;
        return false;
    }

    private void reconcileLibrary(ArrayList<Song> loadedSongs) {
        HashMap<Long, Song> byId = new HashMap<Long, Song>();
        for (Song song : loadedSongs) byId.put(song.id, song);
        Song oldCurrent = currentSong;
        moodTagScanToken++;
        moodTagScanRunning = false;
        allSongs.clear(); allSongs.addAll(loadedSongs);
        moodMixIndex = buildMoodMixIndex(allSongs);
        libraryRevision++;
        tabLoadToken++;
        browserEntriesCache.clear();
        artworkCache.evictAll();
        if (oldCurrent != null) {
            Song updated = byId.get(oldCurrent.id);
            if (updated == null) { releasePlayer(); currentSong = null; queue.clear(); queueIndex = -1; }
            else currentSong = updated;
        }
        ArrayList<Song> updatedQueue = new ArrayList<Song>(); int newIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            Song updated = byId.get(queue.get(i).id);
            if (updated != null) { if (i == queueIndex) newIndex = updatedQueue.size(); updatedQueue.add(updated); }
        }
        queue.clear(); queue.addAll(updatedQueue); queueIndex = currentSong == null ? -1 : newIndex;
        if (forcedNextSongId >= 0 && !byId.containsKey(forcedNextSongId)) forcedNextSongId = -1L;
        for (UserPlaylist playlist : customPlaylists) {
            for (int j = playlist.songIds.size() - 1; j >= 0; j--) if (!byId.containsKey(playlist.songIds.get(j))) playlist.songIds.remove(j);
        }
        saveUserPlaylists();
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().length() == 0 || "<unknown>".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }

    private StateListDrawable createRowSelector() {
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(COLOR_FOCUS_PRESSED));
        selector.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(COLOR_FOCUS));
        selector.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(COLOR_FOCUS));
        selector.addState(new int[]{}, new ColorDrawable(COLOR_ROW));
        return selector;
    }

    private StateListDrawable createTabSelector() {
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(COLOR_FOCUS_PRESSED));
        selector.addState(new int[]{android.R.attr.state_focused}, new ColorDrawable(COLOR_FOCUS));
        selector.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(0xfff8f8f8));
        selector.addState(new int[]{}, new ColorDrawable(0xffdddddd));
        return selector;
    }

    private void renderHomeScreen() {
        showingNowPlaying = false;
        tabsScroll = null;
        tabsRow = null;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        float screenHeightDp = metrics.heightPixels / metrics.density;
        boolean compact = screenHeightDp < 600f;
        int compactRowHeight = Math.max(48, Math.min(60, (int) ((screenHeightDp - 130f) / 6f)));
        int pagePadding = compact ? 6 : 12;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(pagePadding), dp(14), dp(pagePadding));
        page.setBackgroundColor(COLOR_BG);

        TextView heading = text("הספרייה שלי", compact ? 20 : 23, COLOR_TEXT, true);
        heading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(4), 0, dp(4), dp(compact ? 4 : 8));
        page.addView(heading, new LinearLayout.LayoutParams(-1, dp(compact ? 32 : 42)));

        String currentTitle = currentSong == null ? "לא מתנגן שיר כרגע" : currentSong.title;
        String currentArtist = currentSong == null ? "מתנגן עכשיו" : "מתנגן עכשיו · " + currentSong.artist;
        addHomeRow(page, currentTitle, currentArtist, "", "♫", compact, compactRowHeight,
                new Runnable() {
                    @Override public void run() {
                        activeTab = TAB_NOW_PLAYING;
                        renderNowPlaying();
                    }
                });
        addHomeRow(page, "שירים", "", allSongs.size() + " שירים", "≡", compact, compactRowHeight,
                new Runnable() { @Override public void run() { openHomeCategory(TAB_SONGS, "שירים"); } });
        addHomeRow(page, "אמנים", "", artistIndex().size() + " אמנים", "♬", compact, compactRowHeight,
                new Runnable() { @Override public void run() { openHomeCategory(TAB_ARTISTS, "אמנים"); } });
        addHomeRow(page, "אלבומים", "", albumGroups(allSongs).size() + " אלבומים", "▤", compact, compactRowHeight,
                new Runnable() { @Override public void run() { openHomeCategory(TAB_ALBUMS, "אלבומים"); } });
        addHomeRow(page, "פלייליסטים", "", customPlaylists.size() + " רשימות אישיות", "☰", compact, compactRowHeight,
                new Runnable() { @Override public void run() { openHomeCategory(TAB_PLAYLISTS, "פלייליסטים"); } });
        addHomeRow(page, "מיקסים", "", "רשימות לפי מצב רוח", "♫", compact, compactRowHeight,
                new Runnable() { @Override public void run() { openHomeCategory(TAB_MIXES, "מיקסים"); } });

        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void addHomeRow(LinearLayout page, String title, String subtitle, String detail,
                            String symbol, boolean compact, int compactRowHeight, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(compact ? 10 : 13), dp(compact ? 5 : 8),
                dp(compact ? 10 : 13), dp(compact ? 5 : 8));
        card.setBackgroundDrawable(roundRect(COLOR_ROW, 11));
        card.setFocusable(true);
        card.setClickable(true);

        TextView icon = text(symbol, compact ? 22 : 25, 0xff52616a, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackgroundDrawable(roundRect(0xffd2d7db, 10));
        int iconSize = compact ? Math.max(34, compactRowHeight - 16) : 46;
        card.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        labels.setPadding(dp(compact ? 8 : 10), 0, dp(6), 0);
        TextView name = text(title, compact ? 15 : 17, COLOR_TEXT, true);
        name.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView description = text(subtitle, compact ? 11 : 12, COLOR_SUB, false);
        description.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        description.setSingleLine(true);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView count = text(detail, compact ? 10 : 11, COLOR_SUB, false);
        count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        count.setSingleLine(true);
        count.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && subtitle.length() > 0) {
            labels.addView(description, new LinearLayout.LayoutParams(-1, dp(compact ? 15 : 19)));
        }
        if (detail != null && detail.length() > 0) {
            labels.addView(count, new LinearLayout.LayoutParams(-1, dp(compact ? 13 : 16)));
        }
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = text("‹", compact ? 22 : 24, 0xff89949a, true);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(compact ? 18 : 20), -1));

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(roundRect(hasFocus ? COLOR_FOCUS : COLOR_ROW, 13));
                v.setSelected(hasFocus);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(compact ? compactRowHeight : 73));
        params.bottomMargin = dp(compact ? 2 : 8);
        page.addView(card, params);
    }

    private void openHomeCategory(int tab, String title) {
        activeTab = tab;
        detailLevel = 0;
        parentEntries = null;
        parentTitle = "";
        populateBrowser();
        detailLevel = 1;
        detailTitle = title;
        selectedPosition = 0;
        renderBrowser();
    }

    private void renderBrowser() {
        if (detailLevel == 0) { renderHomeScreen(); return; }
        if (activeTab == TAB_NOW_PLAYING) { renderNowPlaying(); return; }
        showingNowPlaying = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        toolbar.setBackgroundColor(COLOR_DARK);
        toolbarBack = text("‹", 25, 0xffffffff, true);
        toolbarBack.setGravity(Gravity.CENTER);
        toolbarBack.setFocusable(false);
        toolbarBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        toolbar.addView(toolbarBack, new LinearLayout.LayoutParams(dp(36), -1));
        toolbarTitle = text(detailTitle, 18, 0xffffffff, true);
        toolbarTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -1, 1);
        toolbar.addView(toolbarTitle, titleParams);
        TextView refresh = text("⟳", 22, 0xffffffff, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { loadLibraryAsync(); }
                catch (RuntimeException error) { reportUiError("רענון הספרייה", error); }
            }
        });
        toolbar.addView(refresh, new LinearLayout.LayoutParams(dp(36), -1));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(36)));

        listView = new ListView(this);
        listView.setCacheColorHint(0x00000000);
        listView.setBackgroundColor(COLOR_BG);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xffd8d8d8));
        listView.setDividerHeight(1);
        listView.setSelector(createRowSelector());
        listView.setFocusable(true);
        listView.setFocusableInTouchMode(true);
        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        // Populate the shared browser model before creating the adapter.
        // Each adapter receives its own snapshot so the previous ListView's
        // item count is never changed behind its back during screen navigation.
        populateBrowser();
        adapter = new BrowserAdapter(new ArrayList<Entry>(visibleEntries));
        LinearLayout browserContent = new LinearLayout(this);
        browserContent.setOrientation(LinearLayout.VERTICAL);
        if (detailLevel == 1 && activeTab == TAB_SONGS) {
            final EditText search = new EditText(this);
            search.setSingleLine(true);
            search.setFocusable(true);
            search.setFocusableInTouchMode(true);
            search.setMinHeight(dp(42));
            search.setTextSize(16);
            search.setHint("חיפוש שירים");
            search.setTextColor(COLOR_TEXT);
            search.setHintTextColor(COLOR_SUB);
            search.setPadding(dp(12), 0, dp(12), 0);
            search.setBackgroundDrawable(roundRect(0xffffffff, 7));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(46));
            searchParams.setMargins(dp(8), dp(7), dp(8), dp(7));
            browserContent.addView(search, searchParams);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    if (adapter != null) {
                        selectedPosition = 0;
                        adapter.filter(value.toString());
                        if (listView != null) listView.setSelection(0);
                    }
                }
                @Override public void afterTextChanged(Editable value) { }
            });
        }
        listView.setAdapter(adapter);
        browserContent.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(browserContent, new LinearLayout.LayoutParams(-1, 0, 1));
        listView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int dataPosition = position - listView.getHeaderViewsCount();
                if (dataPosition < 0 || adapter == null || dataPosition >= adapter.getCount()) return;
                int previous = selectedPosition;
                selectedPosition = dataPosition;
                if (previous >= 0) {
                    int oldChild = previous + listView.getHeaderViewsCount() - listView.getFirstVisiblePosition();
                    if (oldChild >= 0 && oldChild < listView.getChildCount()) {
                        listView.getChildAt(oldChild).setSelected(false);
                    }
                }
                if (view != null) view.setSelected(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int dataPosition = position - listView.getHeaderViewsCount();
                if (dataPosition < 0) return;
                selectedPosition = dataPosition;
                if (view != null) view.setSelected(true);
                if (adapter != null) adapter.notifyDataSetChanged();
                handleEntryClick(dataPosition);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                int dataPosition = position - listView.getHeaderViewsCount();
                if (adapter == null || dataPosition < 0 || dataPosition >= adapter.getCount()) return false;
                Object selected = adapter.getItem(dataPosition);
                if (!(selected instanceof Entry)) return false;
                Entry entry = (Entry) selected;
                if (entry.type != Entry.TYPE_SONG || entry.song == null) return false;
                showSongActions(entry.song); return true;
            }
        });

        miniPlayer = createMiniPlayer();
        if (currentSong != null) root.addView(miniPlayer, new LinearLayout.LayoutParams(-1, dp(44)));
        setContentView(root);
        updateTabs();
        listView.requestFocus();
        if (selectedPosition >= 0 && selectedPosition < (adapter == null ? 0 : adapter.getCount())) listView.setSelection(selectedPosition + listView.getHeaderViewsCount());
    }

    private void addTab(String label, final int tab) {
        TextView item = text(label, 14, COLOR_TEXT, true);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(13), 0, dp(13), 0);
        item.setFocusable(true);
        item.setBackgroundDrawable(createTabSelector());
        item.setSelected(tab == activeTab);
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    switchToTab(tab);
                } catch (RuntimeException error) {
                    reportUiError("מעבר בין לשוניות", error);
                }
            }
        });
        tabsRow.addView(item, new LinearLayout.LayoutParams(-2, -1));
    }

    private void switchToTab(final int tab) {
        if (detailLevel != 0) {
            detailLevel = 0;
            parentEntries = null;
            parentTitle = "";
        }
        activeTab = tab;
        if (tab != TAB_NOW_PLAYING) lastContentTab = tab;
        selectedPosition = 0;
        if (tab == TAB_NOW_PLAYING) {
            tabLoadToken++;
            renderNowPlaying();
            return;
        }
        if (tab == TAB_PLAYLISTS || browserEntriesCache.containsKey(tab)) {
            tabLoadToken++;
            renderBrowser();
            return;
        }

        final int token = ++tabLoadToken;
        final int revision = libraryRevision;
        final ArrayList<Song> snapshot = new ArrayList<Song>(allSongs);
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final HashMap<Integer, ArrayList<Entry>> prepared = prepareBaseBrowserEntries(snapshot);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || token != tabLoadToken || revision != libraryRevision
                                || activeTab != tab) return;
                        browserEntriesCache.putAll(prepared);
                        renderBrowser();
                    }
                });
            }
        }, "nagan-tab-switch");
        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
        // Do not rebuild the old list on the UI thread while the new tab is prepared.
        updateTabs();
    }

    private void updateTabs() {
        if (tabsRow == null) return;
        for (int i = 0; i < tabsRow.getChildCount(); i++) {
            TextView tab = (TextView) tabsRow.getChildAt(i);
            tab.setTextColor(i == activeTab ? COLOR_ACCENT : COLOR_TEXT);
            tab.setSelected(i == activeTab);
        }
    }

    private void populateBrowser() {
        // Detailed lists are already held in visibleEntries by showEntries().
        if (detailLevel > 0) return;

        visibleEntries.clear();
        ArrayList<Entry> cached = browserEntriesCache.get(activeTab);
        if (cached != null) {
            visibleEntries.addAll(cached);
            return;
        }

        if (activeTab == TAB_SONGS) {
            for (Song song : allSongs) visibleEntries.add(Entry.song(song));
        } else if (activeTab == TAB_ARTISTS) {
            Map<String, ArrayList<Song>> artists = artistIndex();
            ArrayList<String> names = new ArrayList<String>(artists.keySet());
            Collections.sort(names, STRING_SORT);
            for (String name : names) visibleEntries.add(Entry.artist(name, artists.get(name)));
        } else if (activeTab == TAB_ALBUMS) {
            ArrayList<AlbumGroup> albums = albumGroups(allSongs);
            for (AlbumGroup album : albums) visibleEntries.add(Entry.album(album.title, artistLabel(album.songs), album.songs));
        } else if (activeTab == TAB_MIXES) {
            addMoodEntries(visibleEntries);
        } else {
            visibleEntries.add(Entry.action("יצירת רשימת השמעה", "יצירת פלייליסט חדש באפליקציה", 1));
            visibleEntries.add(Entry.action("ייבוא רשימת השמעה", "טעינת קובץ M3U או PLS מהמכשיר", 2));
            for (int i = 0; i < customPlaylists.size(); i++) {
                UserPlaylist playlist = customPlaylists.get(i);
                visibleEntries.add(Entry.playlist(playlist.name, playlist.songIds.size() + " שירים", CUSTOM_PLAYLIST_BASE + i));
            }
            visibleEntries.add(Entry.playlist("המושמעים ביותר", "השירים המושמעים ביותר לפי מספר השמעות", 1));
            visibleEntries.add(Entry.playlist("כל השירים", allSongs.size() + " שירים", 2));
            visibleEntries.add(Entry.playlist("נוספו לאחרונה", "השירים שנוספו למכשיר לאחרונה", 3));
        }
        browserEntriesCache.put(activeTab, new ArrayList<Entry>(visibleEntries));
    }

    private void addMoodEntries(ArrayList<Entry> entries) {
        entries.add(moodEntry("שמח", "מז'ורי וקצב בינוני־מהיר", MIX_HAPPY));
        entries.add(moodEntry("עצוב", "מינורי וקצב איטי־מתון", MIX_SAD));
        entries.add(moodEntry("מהורהר", "מינורי וקצב מתון", MIX_THOUGHTFUL));
        entries.add(moodEntry("רגוע", "קצב איטי", MIX_RELAXED));
        entries.add(moodEntry("אנרגטי", "קצב מהיר", MIX_ENERGETIC));
        entries.add(moodEntry("דרמטי", "מינורי וקצב מהיר", MIX_DRAMATIC));
    }

    private Entry moodEntry(String title, String subtitle, int moodId) {
        ArrayList<Song> candidates = moodMixIndex.get(moodId);
        int count = candidates == null ? 0 : candidates.size();
        String detail = moodTagScanRunning ? "סורק תגיות..." : count + " שירים מתאימים";
        return Entry.action(title, subtitle + " · " + detail, moodId);
    }

    private HashMap<Integer, ArrayList<Song>> buildMoodMixIndex(List<Song> songs) {
        HashMap<Integer, ArrayList<Song>> index = new HashMap<Integer, ArrayList<Song>>();
        int[] moodIds = new int[]{MIX_HAPPY, MIX_SAD, MIX_THOUGHTFUL, MIX_RELAXED, MIX_ENERGETIC, MIX_DRAMATIC};
        for (int moodId : moodIds) index.put(moodId, new ArrayList<Song>());
        for (Song song : songs) {
            int mode = keyMode(song.key);
            int bpm = song.bpm;
            boolean hasBpm = bpm >= 30 && bpm <= 300;
            if (mode == 0 && (!hasBpm || (bpm >= 95 && bpm <= 155))) index.get(MIX_HAPPY).add(song);
            if (mode == 1 && (!hasBpm || bpm <= 105)) index.get(MIX_SAD).add(song);
            if (mode == 1 && (!hasBpm || (bpm >= 75 && bpm <= 125))) index.get(MIX_THOUGHTFUL).add(song);
            if (hasBpm && bpm <= 95) index.get(MIX_RELAXED).add(song);
            if (hasBpm && bpm >= 120) index.get(MIX_ENERGETIC).add(song);
            if (mode == 1 && hasBpm && bpm >= 105) index.get(MIX_DRAMATIC).add(song);
        }
        return index;
    }

    /** Returns 0 for major, 1 for minor, and -1 when the key format is unknown. */
    private int keyMode(String rawKey) {
        if (rawKey == null) return -1;
        String value = rawKey.trim().toLowerCase(Locale.US).replace(" ", "");
        if (value.length() == 0) return -1;
        if (value.contains("minor") || value.endsWith("min")) return 1;
        if (value.contains("major") || value.endsWith("maj")) return 0;

        char suffix = value.charAt(value.length() - 1);
        if (suffix == 'a' || suffix == 'b' || suffix == 'm' || suffix == 'd') {
            String number = value.substring(0, value.length() - 1);
            try {
                int circleNumber = Integer.parseInt(number);
                if (circleNumber >= 1 && circleNumber <= 12) {
                    return (suffix == 'a' || suffix == 'm') ? 1 : 0;
                }
            } catch (NumberFormatException ignored) { }
        }

        if (suffix == 'm' && value.length() >= 2) {
            String note = value.substring(0, value.length() - 1);
            if (isMusicNote(note)) return 1;
        }
        return isMusicNote(value) ? 0 : -1;
    }

    private boolean isMusicNote(String value) {
        if (value == null || value.length() < 1 || value.length() > 2) return false;
        char note = value.charAt(0);
        if (note < 'a' || note > 'g') return false;
        return value.length() == 1 || value.charAt(1) == '#' || value.charAt(1) == 'b';
    }

    private void openMoodMix(int moodId) {
        ArrayList<Song> candidates = moodMixIndex.get(moodId);
        if (candidates == null || candidates.size() == 0) {
            String message = moodTagScanRunning
                    ? "תגיות הסולם וה־BPM עדיין נסרקות, נסה שוב בעוד רגע"
                    : "לא נמצאו שירים עם תגיות מתאימות למיקס הזה";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<Song> randomized = new ArrayList<Song>(candidates);
        Collections.shuffle(randomized, random);
        // Pick a fresh random subset on every entry, up to 35 tracks.
        int mixSize = Math.min(35, randomized.size());
        ArrayList<Song> mix = new ArrayList<Song>(randomized.subList(0, mixSize));
        showTrackList("מיקס " + moodTitle(moodId), mix);
    }

    private String moodTitle(int moodId) {
        switch (moodId) {
            case MIX_HAPPY: return "שמח";
            case MIX_SAD: return "עצוב";
            case MIX_THOUGHTFUL: return "מהורהר";
            case MIX_RELAXED: return "רגוע";
            case MIX_ENERGETIC: return "אנרגטי";
            case MIX_DRAMATIC: return "דרמטי";
            default: return "מותאם";
        }
    }

    /** Build the heavy song, artist and album tabs away from the UI thread. */
    private HashMap<Integer, ArrayList<Entry>> prepareBaseBrowserEntries(List<Song> songs) {
        HashMap<Integer, ArrayList<Entry>> result = new HashMap<Integer, ArrayList<Entry>>();

        ArrayList<Entry> songEntries = new ArrayList<Entry>();
        for (Song song : songs) songEntries.add(Entry.song(song));
        result.put(TAB_SONGS, songEntries);

        Map<String, ArrayList<Song>> artists = artistIndex(songs);
        ArrayList<String> artistNames = new ArrayList<String>(artists.keySet());
        Collections.sort(artistNames, STRING_SORT);
        ArrayList<Entry> artistEntries = new ArrayList<Entry>();
        for (String name : artistNames) artistEntries.add(Entry.artist(name, artists.get(name)));
        result.put(TAB_ARTISTS, artistEntries);

        ArrayList<Entry> albumEntries = new ArrayList<Entry>();
        ArrayList<AlbumGroup> albums = albumGroups(songs);
        for (AlbumGroup album : albums) albumEntries.add(Entry.album(album.title, artistLabel(album.songs), album.songs));
        result.put(TAB_ALBUMS, albumEntries);
        return result;
    }

    private void prepareBrowserEntriesCacheAsync(final ArrayList<Song> snapshot) {
        final int revision = libraryRevision;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final HashMap<Integer, ArrayList<Entry>> prepared = prepareBaseBrowserEntries(snapshot);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || revision != libraryRevision || snapshot.size() != allSongs.size()) return;
                        browserEntriesCache.putAll(prepared);
                    }
                });
            }
        }, "nagan-browser-cache");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private void showEntries(String title, ArrayList<Entry> entries) {
        if (detailLevel == 1) {
            parentEntries = new ArrayList<Entry>(visibleEntries);
            parentTitle = detailTitle;
        }
        detailLevel++;
        detailTitle = title;
        visibleEntries.clear();
        visibleEntries.addAll(entries);
        selectedPosition = 0;
        renderBrowser();
    }

    private void showTrackList(String title, ArrayList<Song> songs) {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        for (Song s : songs) entries.add(Entry.song(s));
        showEntries(title, entries);
    }

    private void handleEntryClick(int position) {
        try {
            onEntrySelected(position);
        } catch (RuntimeException error) {
            reportUiError("פתיחת פריט", error);
        }
    }

    private void reportUiError(String action, RuntimeException error) {
        Log.e(TAG, action + " failed", error);
        String detail = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message != null && message.length() > 0) detail += ": " + message;
        Toast.makeText(this, "שגיאה בפתיחת הפריט (" + detail + ")", Toast.LENGTH_LONG).show();
    }

    private void onEntrySelected(int position) {
        ArrayList<Entry> selectionEntries = adapter == null ? visibleEntries : adapter.entries;
        if (position < 0 || position >= selectionEntries.size()) return;
        selectedPosition = position;
        Entry entry = selectionEntries.get(position);
        if (entry == null) return;

        if (entry.type == Entry.TYPE_ACTION) {
            if (entry.actionId == 1) promptCreatePlaylist(null);
            else if (entry.actionId == 2) launchPlaylistPicker();
            else if (entry.actionId >= MIX_ACTION_BASE) openMoodMix(entry.actionId);
        } else if (entry.type == Entry.TYPE_SONG) {
            if (entry.song == null) return;
            ArrayList<Song> playQueue = new ArrayList<Song>();
            for (Entry e : selectionEntries) {
                if (e != null && e.type == Entry.TYPE_SONG && e.song != null) playQueue.add(e.song);
            }
            int index = playQueue.indexOf(entry.song);
            if (index >= 0) startTrack(playQueue, index);
        } else if (entry.type == Entry.TYPE_ARTIST) {
            ArrayList<Song> artistSongs = entry.songs == null
                    ? new ArrayList<Song>() : new ArrayList<Song>(entry.songs);
            ArrayList<AlbumGroup> albums = albumGroups(artistSongs);
            if (albums.size() > 1) {
                ArrayList<Entry> entries = new ArrayList<Entry>();
                for (AlbumGroup a : albums) {
                    entries.add(Entry.artistAlbum(a.title, artistLabel(a.songs), a.songs));
                }
                showEntries(entry.title, entries);
            } else {
                showTrackList(entry.title, artistSongs);
            }
        } else if (entry.type == Entry.TYPE_ALBUM || entry.type == Entry.TYPE_ARTIST_ALBUM) {
            showTrackList(entry.title, entry.songs == null
                    ? new ArrayList<Song>() : new ArrayList<Song>(entry.songs));
        } else if (entry.type == Entry.TYPE_PLAYLIST) {
            ArrayList<Song> selected = new ArrayList<Song>();
            if (entry.playlistId >= CUSTOM_PLAYLIST_BASE) {
                int customIndex = entry.playlistId - CUSTOM_PLAYLIST_BASE;
                if (customIndex >= 0 && customIndex < customPlaylists.size()) selected.addAll(songsForPlaylist(customPlaylists.get(customIndex)));
            } else if (entry.playlistId == 1) {
                Map<Long, Integer> counts = getPlayCounts();
                for (Song s : allSongs) {
                    Integer count = counts.get(s.id);
                    if (count != null && count.intValue() > 0) selected.add(s);
                }
                Collections.sort(selected, new Comparator<Song>() {
                    @Override public int compare(Song a, Song b) {
                        int ca = getCount(a.id), cb = getCount(b.id);
                        if (ca != cb) return cb < ca ? -1 : 1;
                        return STRING_SORT.compare(a.title, b.title);
                    }
                });
            } else if (entry.playlistId == 2) {
                selected.addAll(allSongs);
            } else {
                selected.addAll(allSongs);
                Collections.sort(selected, new Comparator<Song>() {
                    @Override public int compare(Song a, Song b) {
                        if (a.dateAdded == b.dateAdded) return 0;
                        return b.dateAdded < a.dateAdded ? -1 : 1;
                    }
                });
            }
            showTrackList(entry.title, selected);
        }
    }

    private void showSongActions(final Song song) {
        final String[] actions = new String[]{"נגן אחרי השיר הנוכחי", "הוסף לרשימת השמעה"};
        new AlertDialog.Builder(this).setTitle(song.title).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) { if (which == 0) playAfterCurrent(song); else showAddSongToPlaylist(song); }
        }).show();
    }

    private void playAfterCurrent(Song song) {
        if (song == null) return;
        if (currentSong == null || queueIndex < 0 || queue.size() == 0) {
            ArrayList<Song> one = new ArrayList<Song>(); one.add(song); startTrack(one, 0); return;
        }
        int insertAt = Math.min(queue.size(), queueIndex + 1);
        queue.add(insertAt, song);
        forcedNextSongId = song.id;
        Toast.makeText(this, "השיר נוסף אחרי השיר הנוכחי", Toast.LENGTH_SHORT).show();
    }

    private void showAddSongToPlaylist(final Song song) {
        if (customPlaylists.size() == 0) { promptCreatePlaylist(song); return; }
        final String[] options = new String[customPlaylists.size() + 1];
        for (int i = 0; i < customPlaylists.size(); i++) options[i] = customPlaylists.get(i).name;
        options[options.length - 1] = "＋ יצירת רשימת השמעה חדשה";
        new AlertDialog.Builder(this).setTitle("הוסף לרשימת השמעה").setItems(options, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == customPlaylists.size()) promptCreatePlaylist(song); else addSongToPlaylist(customPlaylists.get(which), song);
            }
        }).show();
    }

    private void addSongToPlaylist(UserPlaylist playlist, Song song) {
        if (playlist == null || song == null) return;
        if (!playlist.songIds.contains(song.id)) playlist.songIds.add(song.id);
        browserEntriesCache.remove(TAB_PLAYLISTS);
        saveUserPlaylists();
        Toast.makeText(this, "נוסף ל־" + playlist.name, Toast.LENGTH_SHORT).show();
        if (activeTab == TAB_PLAYLISTS && detailLevel == 0) renderBrowser();
    }

    private void promptCreatePlaylist(final Song firstSong) {
        final EditText input = new EditText(this); input.setSingleLine(true); input.setHint("שם רשימת ההשמעה"); input.setTextSize(18);
        new AlertDialog.Builder(this).setTitle("רשימת השמעה חדשה").setView(input).setNegativeButton("ביטול", null)
                .setPositiveButton("יצירה", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) { Toast.makeText(MainActivity.this, "יש להזין שם", Toast.LENGTH_SHORT).show(); return; }
                        UserPlaylist playlist = new UserPlaylist(name);
                        if (firstSong != null) playlist.songIds.add(firstSong.id);
                        customPlaylists.add(playlist); browserEntriesCache.remove(TAB_PLAYLISTS); saveUserPlaylists();
                        Toast.makeText(MainActivity.this, "רשימת ההשמעה נוצרה", Toast.LENGTH_SHORT).show();
                        if (activeTab == TAB_PLAYLISTS && detailLevel == 0) renderBrowser();
                    }
                }).show();
    }

    private void launchPlaylistPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/x-mpegurl", "audio/mpegurl", "application/vnd.apple.mpegurl", "text/plain"});
            startActivityForResult(intent, REQUEST_IMPORT_PLAYLIST);
        } catch (Exception error) { Toast.makeText(this, "לא ניתן לפתוח בורר קבצים במכשיר", Toast.LENGTH_LONG).show(); }
    }

    private void importPlaylistFile(final Uri uri) {
        final ArrayList<Song> librarySnapshot = new ArrayList<Song>(allSongs);
        Toast.makeText(this, "מייבא את רשימת ההשמעה...", Toast.LENGTH_SHORT).show();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final PlaylistImportResult result = readPlaylistFile(uri, librarySnapshot);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing()) return;
                        if (result.errorMessage != null) {
                            Log.w(TAG, "Playlist import failed: " + result.errorMessage);
                            Toast.makeText(MainActivity.this, "לא ניתן לקרוא את קובץ רשימת ההשמעה", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (result.songIds.size() == 0) {
                            Toast.makeText(MainActivity.this, "לא נמצאו בקובץ שירים שקיימים בספריית הנגן", Toast.LENGTH_LONG).show();
                            return;
                        }
                        String name = result.displayName;
                        if (name == null || name.length() == 0) name = "רשימה מיובאת";
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) name = name.substring(0, dot);
                        UserPlaylist playlist = new UserPlaylist(name);
                        playlist.songIds.addAll(result.songIds);
                        customPlaylists.add(playlist);
                        browserEntriesCache.remove(TAB_PLAYLISTS);
                        saveUserPlaylists();
                        Toast.makeText(MainActivity.this, "יובאו " + result.songIds.size() + " שירים", Toast.LENGTH_LONG).show();
                        activeTab = TAB_PLAYLISTS;
                        lastContentTab = TAB_PLAYLISTS;
                        detailLevel = 0;
                        renderBrowser();
                    }
                });
            }
        }, "nagan-playlist-import");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private PlaylistImportResult readPlaylistFile(Uri uri, List<Song> songs) {
        PlaylistImportResult result = new PlaylistImportResult();
        Cursor nameCursor = null;
        InputStream input = null;
        BufferedReader reader = null;
        try {
            try {
                nameCursor = getContentResolver().query(uri,
                        new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (nameCursor != null && nameCursor.moveToFirst()) {
                    int nameColumn = nameCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameColumn >= 0) result.displayName = nameCursor.getString(nameColumn);
                }
            } catch (Exception error) {
                Log.d(TAG, "Could not read playlist display name", error);
            } finally {
                if (nameCursor != null) nameCursor.close();
            }

            PlaylistMatchIndex index = buildPlaylistMatchIndex(songs);
            input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IllegalStateException("Playlist stream is unavailable");
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8"), 8192);
            HashMap<Long, Boolean> addedIds = new HashMap<Long, Boolean>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) continue;
                if (line.charAt(0) == 65279) line = line.substring(1).trim();
                if (line.length() == 0 || line.startsWith("#")) continue;
                if (line.regionMatches(true, 0, "File", 0, 4) && line.indexOf('=') > 0) {
                    line = line.substring(line.indexOf('=') + 1).trim();
                }
                Song song = findPlaylistSong(line, index);
                if (song != null && !addedIds.containsKey(song.id)) {
                    addedIds.put(song.id, Boolean.TRUE);
                    result.songIds.add(song.id);
                }
            }
        } catch (Exception error) {
            result.errorMessage = error.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) { }
            } else if (input != null) {
                try { input.close(); } catch (Exception ignored) { }
            }
        }
        return result;
    }

    private PlaylistMatchIndex buildPlaylistMatchIndex(List<Song> songs) {
        PlaylistMatchIndex index = new PlaylistMatchIndex();
        for (Song song : songs) {
            if (song.uri != null) index.byUri.put(song.uri.toString().toLowerCase(Locale.US), song);
            if (song.path != null && song.path.length() > 0) {
                String normalizedPath = normalizePlaylistPath(song.path);
                index.byPath.put(normalizedPath, song);
                String fileName = new File(normalizedPath).getName();
                if (fileName.length() > 0 && !index.byFileName.containsKey(fileName)) {
                    index.byFileName.put(fileName, song);
                }
            }
        }
        return index;
    }

    private String normalizePlaylistPath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/').toLowerCase(Locale.US);
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized;
    }

    private Song findPlaylistSong(String rawPath, PlaylistMatchIndex index) {
        if (rawPath == null || index == null) return null;
        String candidate = rawPath.trim().replace('\\', '/');
        if (candidate.length() == 0) return null;

        String lowerCandidate = candidate.toLowerCase(Locale.US);
        Song directUriMatch = index.byUri.get(lowerCandidate);
        if (directUriMatch != null) return directUriMatch;

        if (lowerCandidate.startsWith("file://")) {
            try { candidate = Uri.decode(Uri.parse(candidate).getPath()); }
            catch (Exception ignored) { }
        }
        String normalized = normalizePlaylistPath(candidate);
        Song pathMatch = index.byPath.get(normalized);
        if (pathMatch != null) return pathMatch;

        String fileName = new File(normalized).getName();
        return fileName.length() == 0 ? null : index.byFileName.get(fileName);
    }

    private ArrayList<Song> songsForPlaylist(UserPlaylist playlist) {
        ArrayList<Song> result = new ArrayList<Song>(); if (playlist == null) return result;
        HashMap<Long, Song> byId = new HashMap<Long, Song>(); for (Song song : allSongs) byId.put(song.id, song);
        for (Long id : playlist.songIds) { Song song = byId.get(id); if (song != null) result.add(song); }
        return result;
    }

    private void loadUserPlaylists() {
        customPlaylists.clear(); String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("user_playlists_v1", "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i); if (object == null) continue;
                UserPlaylist playlist = new UserPlaylist(object.optString("name", "רשימת השמעה"));
                JSONArray ids = object.optJSONArray("songs");
                if (ids != null) for (int j = 0; j < ids.length(); j++) { long id = ids.optLong(j, -1L); if (id >= 0) playlist.songIds.add(id); }
                customPlaylists.add(playlist);
            }
        } catch (Exception error) { Log.w(TAG, "Could not load saved playlists", error); }
    }

    private void saveUserPlaylists() {
        JSONArray array = new JSONArray();
        for (UserPlaylist playlist : customPlaylists) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", playlist.name); JSONArray ids = new JSONArray();
                for (Long id : playlist.songIds) ids.put(id);
                object.put("songs", ids); array.put(object);
            } catch (JSONException ignored) { }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("user_playlists_v1", array.toString()).apply();
    }

    private Map<String, ArrayList<Song>> artistIndex() { return artistIndex(allSongs); }

    private Map<String, ArrayList<Song>> artistIndex(List<Song> sourceSongs) {
        Map<String, ArrayList<Song>> result = new LinkedHashMap<String, ArrayList<Song>>();
        for (Song song : sourceSongs) {
            String artistCredit = song.albumArtist == null || song.albumArtist.trim().length() == 0
                    ? song.artist : song.albumArtist;
            String[] names = splitArtists(artistCredit);
            for (String name : names) {
                if (name.length() == 0) continue;
                ArrayList<Song> list = result.get(name);
                if (list == null) { list = new ArrayList<Song>(); result.put(name, list); }
                if (!list.contains(song)) list.add(song);
            }
        }
        return result;
    }

    /** Splits only a standalone X separator, so letters inside normal artist names stay intact. */
    private String[] splitArtists(String raw) {
        String value = clean(raw, "אמן לא ידוע");
        String[] parts = value.split("(?i)\\s+X\\s+");
        if (parts.length == 1) return new String[]{value};
        ArrayList<String> names = new ArrayList<String>();
        for (String p : parts) {
            String name = p.trim();
            if (name.length() > 0 && !names.contains(name)) names.add(name);
        }
        return names.toArray(new String[names.size()]);
    }

    private ArrayList<AlbumGroup> albumGroups(List<Song> songs) {
        LinkedHashMap<String, AlbumGroup> groups = new LinkedHashMap<String, AlbumGroup>();
        for (Song s : songs) {
            String key = s.albumId >= 0 ? "id:" + s.albumId : "name:" + s.album;
            AlbumGroup group = groups.get(key);
            if (group == null) { group = new AlbumGroup(s.album); groups.put(key, group); }
            group.songs.add(s);
        }
        ArrayList<AlbumGroup> values = new ArrayList<AlbumGroup>(groups.values());
        Collections.sort(values, new Comparator<AlbumGroup>() {
            @Override public int compare(AlbumGroup a, AlbumGroup b) { return STRING_SORT.compare(a.title, b.title); }
        });
        return values;
    }

    private String artistLabel(List<Song> songs) {
        if (songs == null || songs.size() == 0) return "";
        String albumArtist = songs.get(0).albumArtist;
        if (albumArtist != null && albumArtist.trim().length() > 0) {
            String[] albumCredits = splitArtists(albumArtist);
            if (albumCredits.length > 0) return albumCredits[0];
        }
        ArrayList<String> common = new ArrayList<String>();
        for (String name : splitArtists(songs.get(0).artist)) if (name.length() > 0 && !common.contains(name)) common.add(name);
        for (int i = 1; i < songs.size() && common.size() > 0; i++) {
            String[] credits = splitArtists(songs.get(i).artist);
            for (int j = common.size() - 1; j >= 0; j--) {
                boolean found = false;
                for (String credit : credits) if (common.get(j).equalsIgnoreCase(credit)) { found = true; break; }
                if (!found) common.remove(j);
            }
        }
        return common.size() == 0 ? "אמן לא ידוע" : common.get(0);
    }

    private ArrayList<Song> getArtistSongs(String name) {
        ArrayList<Song> songs = artistIndex().get(name);
        return songs == null ? new ArrayList<Song>() : songs;
    }

    private LinearLayout createMiniPlayer() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        bar.setBackgroundColor(COLOR_BAR);
        miniPlay = new ImageButton(this);
        miniPlay.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        miniPlay.setImageResource(isPlaying() ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        miniPlay.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        miniPlay.setPadding(dp(7), dp(5), dp(7), dp(5));
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        bar.addView(miniPlay, new LinearLayout.LayoutParams(dp(42), -1));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        miniTitle = text(currentSong == null ? "לא מתנגן שיר" : currentSong.title, 13, 0xffffffff, true);
        miniSubtitle = text(currentSong == null ? "" : currentSong.artist, 11, 0xffcccccc, false);
        labels.addView(miniTitle, new LinearLayout.LayoutParams(-1, -2));
        labels.addView(miniSubtitle, new LinearLayout.LayoutParams(-1, -2));
        bar.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
        bar.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { renderNowPlaying(); }
        });
        return bar;
    }

    private void renderNowPlaying() {
        if (activeTab != TAB_NOW_PLAYING) lastContentTab = activeTab;
        activeTab = TAB_NOW_PLAYING;
        showingNowPlaying = true;
        shuffleButton = null;
        repeatButton = null;
        nowPlayingRoot = new LinearLayout(this);
        nowPlayingRoot.setOrientation(LinearLayout.VERTICAL);
        nowPlayingRoot.setBackgroundColor(COLOR_BG);

        // Fixed layout. Artwork leaves enough room for the timeline and controls.
        // after the song details, timeline and transport controls are placed.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(20), dp(20), 0);

        FrameLayout artHolder = new FrameLayout(this);
        nowArt = new RoundImageView(this);
        nowArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nowArt.setImageResource(R.drawable.albumart_mp_unknown_new);
        artHolder.setBackgroundColor(COLOR_BG);
        artHolder.addView(nowArt, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, 0, 1.02f);
        artParams.topMargin = dp(10);
        artParams.bottomMargin = dp(3);
        content.addView(artHolder, artParams);

        nowTitle = text(currentSong == null ? "אין שיר נבחר" : currentSong.title, 17, COLOR_TEXT, true);
        nowTitle.setGravity(Gravity.CENTER);
        nowTitle.setSingleLine(true);
        content.addView(nowTitle, new LinearLayout.LayoutParams(-1, dp(20)));
        nowArtist = text(currentSong == null ? "" : currentSong.artist, 13, COLOR_SUB, false);
        nowArtist.setGravity(Gravity.CENTER);
        content.addView(nowArtist, new LinearLayout.LayoutParams(-1, dp(15)));
        nowAlbum = text(currentSong == null ? "" : currentSong.album, 12, COLOR_SUB, false);
        nowAlbum.setGravity(Gravity.CENTER);
        content.addView(nowAlbum, new LinearLayout.LayoutParams(-1, dp(14)));
        content.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 0.05f));

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.setPadding(dp(8), 0, dp(8), 0);
        seekBar = new SeekBar(this);
        seekBar.setMax(currentSong == null ? 1 : (int)Math.max(1, currentSong.duration));
        seekBar.setProgress(player != null ? safePosition() : 0);
        seekBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        seekBar.setMinimumHeight(dp(38));
        if (Build.VERSION.SDK_INT >= 17) seekBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        seekBar.setProgressDrawable(new TimelineProgressDrawable());
        seekBar.setThumb(createTimelineThumb());
        seekBar.setThumbOffset(dp(8));

        // Use the platform SeekBar drawable. Custom LayerDrawables can behave
        // inconsistently on older vendor Android 4.4 builds.
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null) try { player.seekTo(bar.getProgress()); } catch (Exception ignored) { }
            }
        });
        timeline.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        elapsedText = text(formatTime(player == null ? 0 : safePosition()), 10, COLOR_SUB, false);
        durationText = text(formatTime(currentSong == null ? 0 : currentSong.duration), 10, COLOR_SUB, false);
        times.addView(elapsedText, new LinearLayout.LayoutParams(0, dp(16), 1));
        durationText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        times.addView(durationText, new LinearLayout.LayoutParams(0, dp(16), 1));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, dp(18));
        timeParams.topMargin = dp(3);
        timeline.addView(times, timeParams);
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(-1, dp(60));
        timelineParams.topMargin = dp(0);
        content.addView(timeline, timelineParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, 0, 0, 0);
        TransportButton previous = new TransportButton(this, TransportButton.TYPE_PREVIOUS);
        previous.setContentDescription("השיר הקודם; לחיצה ארוכה: הרצה אחורה");
        previous.setBackgroundDrawable(stateRoundRect(0xffe8eef0, 0xffd4e0e4, 26));
        installHoldSeek(previous, -1, new Runnable() {
            @Override public void run() { playPrevious(); }
        });
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        sideParams.rightMargin = dp(14);
        controls.addView(previous, sideParams);

        nowPlayButton = new TransportButton(this, TransportButton.TYPE_PLAY);
        nowPlayButton.setPlaying(isPlaying());
        nowPlayButton.setContentDescription("הפעל או השהה");
        nowPlayButton.setBackgroundDrawable(modernPlayBackground());
        nowPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        controls.addView(nowPlayButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TransportButton next = new TransportButton(this, TransportButton.TYPE_NEXT);
        next.setContentDescription("השיר הבא; לחיצה ארוכה: הרצה קדימה");
        next.setBackgroundDrawable(stateRoundRect(0xffe8eef0, 0xffd4e0e4, 26));
        installHoldSeek(next, 1, new Runnable() {
            @Override public void run() { playNext(true); }
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        nextParams.leftMargin = dp(14);
        controls.addView(next, nextParams);
        content.addView(controls, new LinearLayout.LayoutParams(-1, dp(54)));

        nowPlayingRoot.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(nowPlayingRoot);
        loadArtworkAsync(currentSong, nowArt);
        updatePlayerButtons();
    }

    private void loadArtworkAsync(final Song song, final ImageView target) {
        if (song == null || target == null) return;
        final long songId = song.id;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final Bitmap bitmap = loadArtwork(song);
                if (bitmap == null) return;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || currentSong == null || currentSong.id != songId
                                || nowArt != target) {
                            // The bitmap may still be held by the LRU cache; do not recycle it here.
                            return;
                        }
                        target.setImageBitmap(bitmap);
                        fitArtworkViewToBitmap(target, bitmap);
                    }
                });
            }
        }, "nagan-artwork");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private void fitArtworkViewToBitmap(final ImageView target, final Bitmap bitmap) {
        target.post(new Runnable() {
            @Override public void run() {
                if (target != nowArt || target.getParent() == null || bitmap == null) return;
                View holder = (View) target.getParent();
                int maxWidth = holder.getWidth() - holder.getPaddingLeft() - holder.getPaddingRight();
                int maxHeight = holder.getHeight() - holder.getPaddingTop() - holder.getPaddingBottom();
                if (maxWidth <= 0 || maxHeight <= 0 || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
                float aspect = bitmap.getWidth() / (float) bitmap.getHeight();
                float width = maxWidth;
                float height = width / aspect;
                if (height > maxHeight) {
                    height = maxHeight;
                    width = height * aspect;
                }
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                        Math.max(1, Math.round(width)), Math.max(1, Math.round(height)), Gravity.CENTER);
                target.setScaleType(ImageView.ScaleType.FIT_CENTER);
                target.setLayoutParams(params);
            }
        });
    }

    /**
     * Handle the whole press gesture ourselves. The platform long-click callback
     * can race with ACTION_UP on older touch drivers; this timer-based gesture
     * starts seeking after 450 ms and repeats until the finger is released.
     */
    private void installHoldSeek(final View control, final int direction, final Runnable shortClick) {
        if (!(control instanceof TransportButton)) return;
        ((TransportButton) control).setSeekGesture(direction, shortClick);
    }

    private void beginContinuousSeek(int direction) {
        if (player == null || !playerPrepared) return;
        try {
            int duration = player.getDuration();
            if (duration <= 0) return;
            heldSeekDuration = duration;
            heldSeekPosition = player.getCurrentPosition();
            seekDirection = direction;
            heldSeekPosition = Math.max(0, Math.min(heldSeekDuration,
                    heldSeekPosition + direction * 2500));
            player.seekTo(heldSeekPosition);
            if (seekBar != null) seekBar.setProgress(heldSeekPosition);
            if (elapsedText != null) elapsedText.setText(formatTime(heldSeekPosition));
            handler.removeCallbacks(heldSeekRunnable);
            handler.postDelayed(heldSeekRunnable, 450L);
        } catch (IllegalStateException ignored) {
            seekDirection = 0;
        }
    }

    private void seekToAbsolute(int requestedPosition) {
        if (player == null) return;
        try {
            int duration = heldSeekDuration > 0 ? heldSeekDuration : player.getDuration();
            int target = Math.max(0, Math.min(duration, requestedPosition));
            player.seekTo(target);
            if (seekBar != null) seekBar.setProgress(target);
            if (elapsedText != null) elapsedText.setText(formatTime(target));
        } catch (IllegalStateException ignored) { }
    }

    private TextView controlText(String label, int color) {
        TextView v = text(label, 22, color, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        return v;
    }

    private ImageButton controlImage(int resource) {
        ImageButton b = new ImageButton(this);
        b.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        b.setImageResource(resource);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        b.setFocusable(true);
        return b;
    }

    private int artworkSampleSize(int width, int height) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > MAX_ARTWORK_SIZE) sample *= 2;
        return Math.max(1, sample);
    }

    private Bitmap decodeArtworkFile(String path) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inDither = false;
            options.inScaled = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not decode album artwork file " + path, error);
            return null;
        }
    }

    private Bitmap decodeArtworkBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inDither = false;
            options.inScaled = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not decode embedded album artwork", error);
            return null;
        }
    }

    private Bitmap decodeArtworkUri(Uri uri) {
        java.io.InputStream input = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            input = getContentResolver().openInputStream(uri);
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
            input.close();
            input = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = artworkSampleSize(bounds.outWidth, bounds.outHeight);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            options.inDither = false;
            options.inScaled = false;
            input = getContentResolver().openInputStream(uri);
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        } catch (Exception error) {
            Log.w(TAG, "Could not decode album artwork URI " + uri, error);
            return null;
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private Bitmap decodeEmbeddedArtworkFromPath(String path) {
        if (path == null || path.length() == 0) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            return decodeArtworkBytes(retriever.getEmbeddedPicture());
        } catch (Exception error) {
            Log.d(TAG, "No embedded artwork at path " + path, error);
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private Bitmap decodeEmbeddedArtworkFromUri(Uri uri) {
        if (uri == null) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            return decodeArtworkBytes(retriever.getEmbeddedPicture());
        } catch (Exception error) {
            Log.d(TAG, "No embedded artwork at URI " + uri, error);
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private Bitmap decodeEmbeddedArtwork(Song song) {
        if (song == null) return null;
        Bitmap bitmap = decodeEmbeddedArtworkFromPath(song.path);
        if (bitmap == null) bitmap = decodeEmbeddedArtworkFromUri(song.uri);
        return bitmap;
    }

    private Bitmap findFolderArtwork(Song song) {
        if (song == null || song.path == null) return null;
        File folder = new File(song.path).getParentFile();
        if (folder == null || !folder.isDirectory()) return null;

        String[] preferred = new String[]{
                "cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.jpeg",
                "folder.png", "front.jpg", "front.png", "albumart.jpg", "albumart.png"
        };
        for (String name : preferred) {
            File candidate = new File(folder, name);
            if (candidate.isFile()) {
                Bitmap bitmap = decodeArtworkFile(candidate.getAbsolutePath());
                if (bitmap != null) return bitmap;
            }
        }

        File[] images = folder.listFiles();
        if (images == null) return null;
        File best = null;
        for (File candidate : images) {
            if (!candidate.isFile()) continue;
            String name = candidate.getName().toLowerCase(Locale.US);
            if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                    || name.endsWith(".webp"))) continue;
            if (best == null || candidate.length() > best.length()) best = candidate;
        }
        return best == null ? null : decodeArtworkFile(best.getAbsolutePath());
    }

    private Bitmap loadArtwork(Song song) {
        if (song == null) return null;
        Bitmap cached = artworkCache.get(song.id);
        if (cached != null && !cached.isRecycled()) return cached;

        // Embedded covers are often the original, highest-resolution image and
        // are not always exposed through MediaStore's ALBUM_ART path.
        Bitmap bitmap = decodeEmbeddedArtwork(song);
        if (bitmap == null) bitmap = findFolderArtwork(song);
        if (bitmap == null && song.albumArtPath != null) {
            File file = new File(song.albumArtPath);
            if (file.isFile()) bitmap = decodeArtworkFile(song.albumArtPath);
        }
        if (bitmap == null && song.albumId >= 0) {
            Uri artUri = Uri.parse("content://media/external/audio/albumart/" + song.albumId);
            bitmap = decodeArtworkUri(artUri);
        }
        if (bitmap != null && !bitmap.isRecycled()) artworkCache.put(song.id, bitmap);
        return bitmap;
    }

    private void updatePlayerButtons() {
        boolean playing = isPlaying();
        if (nowPlayButton != null) nowPlayButton.setPlaying(playing);
        if (miniPlay != null) miniPlay.setImageResource(playing ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        if (shuffleButton != null) shuffleButton.setTextColor(shuffle ? COLOR_ACCENT : COLOR_SUB);
        if (repeatButton != null) {
            repeatButton.setText(repeatMode == 2 ? "1↻" : "↻");
            repeatButton.setTextColor(repeatMode == 0 ? COLOR_SUB : COLOR_ACCENT);
        }
        updateMiniPlayerText();
    }

    private void readMoodTags(Song song) {
        if (song == null) return;
        MoodTagData data = readMoodTags(song.path);
        song.key = data.key;
        song.bpm = data.bpm;
        song.moodTagsScanned = true;
        song.moodTagReaderVersion = MOOD_TAG_READER_VERSION;
    }

    private MoodTagData readMoodTags(String path) {
        MoodTagData result = new MoodTagData();
        if (path == null || path.length() == 0) return result;
        File file = new File(path);
        if (!file.isFile()) return result;
        String name = path.toLowerCase(Locale.US);
        try {
            // ID3 TKEY/TBPM are used by MP3 and some WAV/AIFF files.
            readId3MoodTags(file, result);
            if (name.endsWith(".flac")) readFlacMoodTags(file, result);
            else if (name.endsWith(".ogg") || name.endsWith(".oga") || name.endsWith(".opus")) {
                readOggMoodTags(file, result);
            }
        } catch (Exception error) {
            Log.d(TAG, "Could not read mood tags from " + path, error);
        }
        return result;
    }

    private void readId3MoodTags(File file, MoodTagData result) {
        RandomAccessFile input = null;
        try {
            input = new RandomAccessFile(file, "r");
            if (input.length() < 10) return;
            byte[] header = new byte[10];
            input.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return;
            int version = header[3] & 255;
            if (version < 2 || version > 4) return;
            int flags = header[5] & 255;
            int tagSize = syncSafeInt(header, 6);
            if (tagSize <= 0) return;
            long tagEnd = Math.min(input.length(), 10L + tagSize);
            input.seek(10L);

            // Skip an extended header if present.
            if ((flags & 0x40) != 0 && version >= 3 && input.getFilePointer() + 4 <= tagEnd) {
                byte[] extSizeBytes = new byte[4];
                input.readFully(extSizeBytes);
                int extSize = version == 4 ? syncSafeInt(extSizeBytes, 0)
                        : bigEndianInt(extSizeBytes, 0);
                long target = version == 4 ? 10L + extSize : 14L + extSize;
                if (target > input.getFilePointer() && target <= tagEnd) input.seek(target);
            }

            int headerLength = version == 2 ? 6 : 10;
            while (input.getFilePointer() + headerLength <= tagEnd) {
                byte[] frameHeader = new byte[headerLength];
                input.readFully(frameHeader);
                int idLength = version == 2 ? 3 : 4;
                boolean empty = true;
                for (int i = 0; i < idLength; i++) if (frameHeader[i] != 0) empty = false;
                if (empty) break;
                String frameId = new String(frameHeader, 0, idLength, "ISO-8859-1");
                int frameSize;
                if (version == 2) {
                    frameSize = ((frameHeader[3] & 255) << 16)
                            | ((frameHeader[4] & 255) << 8) | (frameHeader[5] & 255);
                } else if (version == 4) {
                    frameSize = syncSafeInt(frameHeader, 4);
                } else {
                    frameSize = bigEndianInt(frameHeader, 4);
                }
                if (frameSize <= 0) break;
                long nextFrame = input.getFilePointer() + frameSize;
                if (nextFrame > tagEnd || nextFrame > input.length()) break;

                boolean keyFrame = "TKEY".equals(frameId) || "TKE".equals(frameId);
                boolean bpmFrame = "TBPM".equals(frameId) || "TBP".equals(frameId);
                if ((keyFrame || bpmFrame) && frameSize <= 4096) {
                    byte[] body = new byte[frameSize];
                    input.readFully(body);
                    String value = decodeId3Text(body);
                    if (keyFrame && result.key == null) result.key = value;
                    if (bpmFrame && result.bpm <= 0) result.bpm = parseBpm(value);
                }
                input.seek(nextFrame);
                if (result.key != null && result.bpm > 0) break;
            }
        } catch (Exception error) {
            Log.d(TAG, "ID3 mood tag read skipped for " + file, error);
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private int syncSafeInt(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 4 > data.length) return 0;
        return ((data[offset] & 127) << 21) | ((data[offset + 1] & 127) << 14)
                | ((data[offset + 2] & 127) << 7) | (data[offset + 3] & 127);
    }

    private int bigEndianInt(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 4 > data.length) return 0;
        return ((data[offset] & 255) << 24) | ((data[offset + 1] & 255) << 16)
                | ((data[offset + 2] & 255) << 8) | (data[offset + 3] & 255);
    }

    private String decodeId3Text(byte[] body) {
        if (body == null || body.length < 2) return null;
        int encoding = body[0] & 255;
        String charset;
        if (encoding == 1) charset = "UTF-16";
        else if (encoding == 2) charset = "UTF-16BE";
        else if (encoding == 3) charset = "UTF-8";
        else charset = "ISO-8859-1";
        try {
            String value = new String(body, 1, body.length - 1, charset)
                    .replace("\u0000", "").trim();
            return value.length() == 0 ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void readFlacMoodTags(File file, MoodTagData result) {
        RandomAccessFile input = null;
        try {
            input = new RandomAccessFile(file, "r");
            if (input.length() < 8) return;
            byte[] signature = new byte[4];
            input.readFully(signature);
            if (signature[0] != 'f' || signature[1] != 'L'
                    || signature[2] != 'a' || signature[3] != 'C') return;
            boolean lastBlock = false;
            while (!lastBlock && input.getFilePointer() + 4 <= input.length()) {
                int blockHeader = input.readUnsignedByte();
                lastBlock = (blockHeader & 128) != 0;
                int blockType = blockHeader & 127;
                int blockSize = (input.readUnsignedByte() << 16)
                        | (input.readUnsignedByte() << 8) | input.readUnsignedByte();
                long next = input.getFilePointer() + blockSize;
                if (next > input.length()) break;
                if (blockType == 4 && blockSize > 0 && blockSize <= 4 * 1024 * 1024) {
                    byte[] comments = new byte[blockSize];
                    input.readFully(comments);
                    parseVorbisComments(comments, 0, result);
                    if (result.key != null && result.bpm > 0) return;
                } else {
                    input.seek(next);
                }
            }
        } catch (Exception error) {
            Log.d(TAG, "FLAC mood tag read skipped for " + file, error);
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private void readOggMoodTags(File file, MoodTagData result) {
        RandomAccessFile input = null;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        try {
            input = new RandomAccessFile(file, "r");
            long readLimit = Math.min(input.length(), 8L * 1024L * 1024L);
            while (input.getFilePointer() + 27 <= readLimit) {
                byte[] pageHeader = new byte[27];
                input.readFully(pageHeader);
                if (pageHeader[0] != 'O' || pageHeader[1] != 'g'
                        || pageHeader[2] != 'g' || pageHeader[3] != 'S') break;
                int segmentCount = pageHeader[26] & 255;
                if (input.getFilePointer() + segmentCount > readLimit) break;
                byte[] lacing = new byte[segmentCount];
                input.readFully(lacing);
                for (int i = 0; i < lacing.length; i++) {
                    int length = lacing[i] & 255;
                    if (input.getFilePointer() + length > readLimit) return;
                    byte[] part = new byte[length];
                    input.readFully(part);
                    packet.write(part, 0, part.length);
                    if (length < 255) {
                        byte[] completed = packet.toByteArray();
                        int offset = -1;
                        if (startsWith(completed, new byte[]{3, 'v', 'o', 'r', 'b', 'i', 's'})) offset = 7;
                        else if (startsWith(completed, new byte[]{'O', 'p', 'u', 's', 'T', 'a', 'g', 's'})) offset = 8;
                        if (offset >= 0) {
                            parseVorbisComments(completed, offset, result);
                            if (result.key != null || result.bpm > 0) return;
                        }
                        packet.reset();
                    }
                }
            }
        } catch (Exception error) {
            Log.d(TAG, "Ogg/Opus mood tag read skipped for " + file, error);
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || prefix == null || data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    private void parseVorbisComments(byte[] data, int offset, MoodTagData result) {
        if (data == null || result == null || offset < 0 || offset + 8 > data.length) return;
        int position = offset;
        long vendorLength = littleEndianInt(data, position);
        position += 4;
        if (vendorLength < 0 || vendorLength > data.length - position) return;
        position += (int) vendorLength;
        if (position + 4 > data.length) return;
        long commentCount = littleEndianInt(data, position);
        position += 4;
        if (commentCount < 0 || commentCount > 10000) return;
        for (int i = 0; i < (int) commentCount && position + 4 <= data.length; i++) {
            long length = littleEndianInt(data, position);
            position += 4;
            if (length < 0 || length > data.length - position) return;
            String comment = new String(data, position, (int) length, "UTF-8");
            position += (int) length;
            int equals = comment.indexOf('=');
            if (equals <= 0 || equals >= comment.length() - 1) continue;
            applyMoodComment(comment.substring(0, equals), comment.substring(equals + 1), result);
        }
    }

    private long littleEndianInt(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 4 > data.length) return -1;
        return (data[offset] & 255L) | ((data[offset + 1] & 255L) << 8)
                | ((data[offset + 2] & 255L) << 16) | ((data[offset + 3] & 255L) << 24);
    }

    private void applyMoodComment(String rawName, String rawValue, MoodTagData result) {
        if (rawName == null || rawValue == null) return;
        String name = rawName.trim().toUpperCase(Locale.US);
        String value = rawValue.trim();
        if (value.length() == 0) return;
        if ("BPM".equals(name) || "TBPM".equals(name) || "TEMPO".equals(name)) {
            int bpm = parseBpm(value);
            if (bpm > 0) result.bpm = bpm;
        } else if ("TKEY".equals(name) || "KEY".equals(name)
                || "INITIALKEY".equals(name) || "INITIAL_KEY".equals(name)) {
            result.key = value;
        }
    }

    private int parseBpm(String rawValue) {
        if (rawValue == null) return 0;
        String value = rawValue.trim().replace(',', '.');
        value = value.replaceAll("(?i)\\s*(bpm|beats per minute)\\s*", "").trim();
        int separator = value.indexOf(';');
        if (separator >= 0) value = value.substring(0, separator).trim();
        try {
            double parsed = Double.parseDouble(value);
            if (parsed >= 30.0 && parsed <= 300.0) return (int) Math.round(parsed);
        } catch (NumberFormatException ignored) { }
        return 0;
    }

    private void scanMissingMoodTagsAsync() {
        final ArrayList<Song> librarySnapshot = new ArrayList<Song>(allSongs);
        final ArrayList<Song> toScan = new ArrayList<Song>();
        for (Song song : librarySnapshot) if (song.moodTagReaderVersion < MOOD_TAG_READER_VERSION) toScan.add(song);
        if (toScan.size() == 0) {
            moodMixIndex = buildMoodMixIndex(allSongs);
            return;
        }
        final int token = ++moodTagScanToken;
        final int revision = libraryRevision;
        moodTagScanRunning = true;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final HashMap<Long, MoodTagData> found = new HashMap<Long, MoodTagData>();
                for (Song song : toScan) found.put(song.id, readMoodTags(song.path));
                // Serialize a detached snapshot here, not on the UI thread.
                final String updatedCache = serializeMoodTagCache(librarySnapshot, found);
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || token != moodTagScanToken || revision != libraryRevision) return;
                        HashMap<Long, Song> byId = new HashMap<Long, Song>();
                        for (Song song : allSongs) byId.put(song.id, song);
                        for (Map.Entry<Long, MoodTagData> item : found.entrySet()) {
                            Song current = byId.get(item.getKey());
                            if (current == null) continue;
                            current.key = item.getValue().key;
                            current.bpm = item.getValue().bpm;
                            current.moodTagsScanned = true;
                            current.moodTagReaderVersion = MOOD_TAG_READER_VERSION;
                        }
                        moodTagScanRunning = false;
                        moodMixIndex = buildMoodMixIndex(allSongs);
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putString(LIBRARY_CACHE_KEY, updatedCache).apply();
                        browserEntriesCache.remove(TAB_MIXES);
                        if (activeTab == TAB_MIXES && detailLevel == 0) {
                            renderBrowser();
                        } else if (activeTab == TAB_MIXES && detailLevel == 1) {
                            visibleEntries.clear();
                            addMoodEntries(visibleEntries);
                            renderBrowser();
                        }
                        Toast.makeText(MainActivity.this, "סריקת תגיות המוזיקה הושלמה", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "nagan-mood-tag-scan");
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private String serializeMoodTagCache(List<Song> songs, Map<Long, MoodTagData> updates) {
        ArrayList<Song> detached = new ArrayList<Song>(songs.size());
        for (Song source : songs) {
            Song copy = new Song();
            copy.id = source.id;
            copy.albumId = source.albumId;
            copy.duration = source.duration;
            copy.dateAdded = source.dateAdded;
            copy.title = source.title;
            copy.artist = source.artist;
            copy.album = source.album;
            copy.path = source.path;
            copy.albumArtPath = source.albumArtPath;
            copy.albumArtist = source.albumArtist;
            copy.key = source.key;
            copy.bpm = source.bpm;
            copy.moodTagsScanned = source.moodTagsScanned;
            copy.moodTagReaderVersion = source.moodTagReaderVersion;
            MoodTagData data = updates.get(source.id);
            if (data != null) {
                copy.key = data.key;
                copy.bpm = data.bpm;
                copy.moodTagsScanned = true;
                copy.moodTagReaderVersion = MOOD_TAG_READER_VERSION;
            }
            detached.add(copy);
        }
        return serializeLibraryCache(detached);
    }

    private void updateMiniPlayerText() {
        if (miniTitle != null) miniTitle.setText(currentSong == null ? "לא מתנגן שיר" : currentSong.title);
        if (miniSubtitle != null) miniSubtitle.setText(currentSong == null ? "" : currentSong.artist);
    }

    private void startTrack(ArrayList<Song> songs, int index) {
        if (songs == null || songs.size() == 0 || index < 0 || index >= songs.size()) return;
        forcedNextSongId = -1L;
        queue.clear();
        queue.addAll(songs);
        queueIndex = index;
        startCurrentTrack();
    }

    private void startCurrentTrack() {
        if (queueIndex < 0 || queueIndex >= queue.size()) return;
        releasePlayer();
        currentSong = queue.get(queueIndex);
        final MediaPlayer newPlayer = new MediaPlayer();
        try {
            newPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            newPlayer.setOnPreparedListener(this);
            newPlayer.setOnCompletionListener(this);
            newPlayer.setOnErrorListener(this);
            if (currentSong.uri != null) {
                newPlayer.setDataSource(this, currentSong.uri);
            } else if (currentSong.path != null) {
                newPlayer.setDataSource(currentSong.path);
            } else {
                throw new java.io.IOException("Missing audio file location");
            }
            player = newPlayer;
        } catch (Exception error) {
            Log.e(TAG, "Could not set audio source for track " + currentSong.id, error);
            try { newPlayer.release(); } catch (Exception ignored) { }
            player = null;
            Toast.makeText(this, "לא ניתן לפתוח את קובץ השמע", Toast.LENGTH_LONG).show();
            return;
        }

        showingNowPlaying = true;
        try {
            renderNowPlaying();
        } catch (RuntimeException error) {
            Log.e(TAG, "Could not render now-playing screen", error);
            // Audio remains independent from the artwork and player UI.
            try { renderSafeNowPlaying(); }
            catch (RuntimeException fallbackError) {
                Log.e(TAG, "Fallback player screen failed", fallbackError);
                showingNowPlaying = false;
                activeTab = lastContentTab;
                renderBrowser();
            }
        }

        try {
            newPlayer.prepareAsync();
        } catch (RuntimeException error) {
            Log.e(TAG, "prepareAsync failed for track " + currentSong.id, error);
            releasePlayer();
            Toast.makeText(this, "לא ניתן להתחיל לנגן את הקובץ", Toast.LENGTH_LONG).show();
        }
        updatePlayerButtons();
    }

    /** Minimal controls-only fallback: playback must not depend on artwork rendering. */
    private void renderSafeNowPlaying() {
        activeTab = TAB_NOW_PLAYING;
        showingNowPlaying = true;
        LinearLayout safeRoot = new LinearLayout(this);
        safeRoot.setOrientation(LinearLayout.VERTICAL);
        safeRoot.setGravity(Gravity.CENTER);
        safeRoot.setPadding(dp(16), dp(16), dp(16), dp(16));
        safeRoot.setBackgroundColor(COLOR_BG);

        TextView title = text(currentSong == null ? "אין שיר נבחר" : currentSong.title, 20, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        safeRoot.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView artist = text(currentSong == null ? "" : currentSong.artist, 15, COLOR_SUB, false);
        artist.setGravity(Gravity.CENTER);
        safeRoot.addView(artist, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        TransportButton previous = new TransportButton(this, TransportButton.TYPE_PREVIOUS);
        previous.setContentDescription("השיר הקודם; לחיצה ארוכה: הרצה אחורה");
        previous.setBackgroundDrawable(stateRoundRect(0xffe8eef0, 0xffd4e0e4, 26));
        installHoldSeek(previous, -1, new Runnable() {
            @Override public void run() { playPrevious(); }
        });
        controls.addView(previous, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TransportButton play = new TransportButton(this, TransportButton.TYPE_PLAY);
        play.setPlaying(isPlaying());
        play.setContentDescription("הפעל או השהה");
        play.setBackgroundDrawable(stateRoundRect(COLOR_ACCENT, 0xff0095a5, 28));
        play.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        playParams.leftMargin = dp(18);
        playParams.rightMargin = dp(18);
        controls.addView(play, playParams);

        TransportButton next = new TransportButton(this, TransportButton.TYPE_NEXT);
        next.setContentDescription("השיר הבא; לחיצה ארוכה: הרצה קדימה");
        next.setBackgroundDrawable(stateRoundRect(0xffe8eef0, 0xffd4e0e4, 26));
        installHoldSeek(next, 1, new Runnable() {
            @Override public void run() { playNext(true); }
        });
        controls.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));

        safeRoot.addView(controls, new LinearLayout.LayoutParams(-1, dp(66)));
        setContentView(safeRoot);
        nowPlayButton = play;
        miniPlay = null;
        nowArt = null;
        seekBar = null;
        elapsedText = null;
        durationText = null;
        updatePlayerButtons();
    }

    @Override public void onPrepared(MediaPlayer mp) {
        if (mp == null || mp != player) return;
        playerPrepared = true;
        try {
            mp.start();
            if (currentSong != null) recordPlay(currentSong);
        } catch (RuntimeException error) {
            Log.e(TAG, "MediaPlayer start failed", error);
            Toast.makeText(this, "המכשיר לא הצליח להתחיל לנגן את הקובץ", Toast.LENGTH_LONG).show();
        }
        updatePlayerButtons();
    }

    @Override public void onCompletion(MediaPlayer mp) {
        if (repeatMode == 2 && player != null) {
            try { player.seekTo(0); player.start(); return; } catch (Exception ignored) { }
        }
        playNext(false);
    }

    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
        Toast.makeText(this, "שגיאה בהשמעת השיר (" + what + "/" + extra + ")", Toast.LENGTH_LONG).show();
        if (mp == player) releasePlayer();
        updatePlayerButtons();
        return true;
    }

    private void playNext(boolean manual) {
        if (queue.size() == 0) return;
        int next = -1;
        if (forcedNextSongId >= 0) {
            for (int i = Math.max(0, queueIndex + 1); i < queue.size(); i++) {
                if (queue.get(i).id == forcedNextSongId) { next = i; break; }
            }
            forcedNextSongId = -1L;
        }
        if (next < 0 && shuffle && queue.size() > 1) {
            next = queueIndex;
            while (next == queueIndex) next = random.nextInt(queue.size());
        } else if (next < 0) {
            next = queueIndex + 1;
        }
        if (next >= queue.size()) {
            if (repeatMode == 1 || manual) next = 0;
            else { releasePlayer(); updatePlayerButtons(); return; }
        }
        queueIndex = next;
        startCurrentTrack();
    }

    private void playPrevious() {
        if (queue.size() == 0) return;
        if (player != null && safePosition() > 3000) {
            try { player.seekTo(0); return; } catch (Exception ignored) { }
        }
        queueIndex = queueIndex <= 0 ? queue.size() - 1 : queueIndex - 1;
        startCurrentTrack();
    }

    private void togglePlayback() {
        if (player == null) {
            if (currentSong != null && queueIndex >= 0) startCurrentTrack();
            else if (allSongs.size() > 0) startTrack(allSongs, 0);
            return;
        }
        try {
            if (player.isPlaying()) player.pause();
            else player.start();
        } catch (IllegalStateException ignored) { }
        updatePlayerButtons();
    }

    private boolean isPlaying() {
        try { return player != null && player.isPlaying(); }
        catch (IllegalStateException error) { return false; }
    }

    private int safePosition() {
        try { return player == null ? 0 : player.getCurrentPosition(); }
        catch (Exception error) { return 0; }
    }

    private void releasePlayer() {
        playerPrepared = false;
        heldSeekPosition = 0;
        heldSeekDuration = 0;
        seekDirection = 0;
        handler.removeCallbacks(heldSeekRunnable);
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            try {
                old.setOnPreparedListener(null);
                old.setOnCompletionListener(null);
                old.setOnErrorListener(null);
            } catch (Exception ignored) { }
            try { old.reset(); } catch (Exception ignored) { }
            try { old.release(); } catch (Exception ignored) { }
        }
    }

    private void recordPlay(Song song) {
        if (song == null) return;
        int count = getCount(song.id) + 1;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("plays_" + song.id, count).apply();
    }

    private int getCount(long id) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt("plays_" + id, 0);
    }

    private Map<Long, Integer> getPlayCounts() {
        Map<Long, Integer> counts = new HashMap<Long, Integer>();
        for (Song s : allSongs) {
            int count = getCount(s.id);
            if (count > 0) counts.put(s.id, count);
        }
        return counts;
    }

    private String formatTime(long milliseconds) {
        long seconds = Math.max(0, milliseconds / 1000);
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", mins, secs);
    }

    @Override public void onBackPressed() {
        if (showingNowPlaying) {
            showingNowPlaying = false; activeTab = lastContentTab; renderBrowser(); return;
        }
        if (detailLevel > 1 && parentEntries != null) {
            detailLevel = 1;
            detailTitle = parentTitle;
            visibleEntries.clear();
            visibleEntries.addAll(parentEntries);
            parentEntries = null;
            parentTitle = "";
            selectedPosition = 0;
            renderBrowser();
            return;
        }
        if (detailLevel > 0) {
            detailLevel = 0;
            detailTitle = "";
            parentEntries = null;
            parentTitle = "";
            selectedPosition = 0;
            renderBrowser();
            return;
        }
        super.onBackPressed();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            togglePlayback(); return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) { playNext(true); return true; }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { playPrevious(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { onBackPressed(); return true; }
        if (!showingNowPlaying && detailLevel == 0 && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            return super.onKeyDown(keyCode, event);
        }
        if (showingNowPlaying) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { playPrevious(); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { playNext(true); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { togglePlayback(); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        libraryLoadToken++;
        tabLoadToken++;
        seekDirection = 0; handler.removeCallbacks(heldSeekRunnable);
        unregisterMediaObserver(); handler.removeCallbacks(scanChangedMedia);
        handler.removeCallbacks(progressRunnable);
        releasePlayer();
        super.onDestroy();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 17) t.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final Comparator<String> STRING_SORT = new Comparator<String>() {
        @Override public int compare(String a, String b) {
            return a.toLowerCase(Locale.getDefault()).compareTo(b.toLowerCase(Locale.getDefault()));
        }
    };

    private class BrowserAdapter extends BaseAdapter {
        private final ArrayList<Entry> allEntries = new ArrayList<Entry>();
        private final ArrayList<Entry> entries = new ArrayList<Entry>();

        BrowserAdapter(ArrayList<Entry> snapshot) {
            if (snapshot != null) allEntries.addAll(snapshot);
            entries.addAll(allEntries);
        }

        void filter(String query) {
            String value = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
            entries.clear();
            if (value.length() == 0) entries.addAll(allEntries);
            else for (Entry entry : allEntries) {
                String title = entry.title == null ? "" : entry.title.toLowerCase(Locale.getDefault());
                String subtitle = entry.subtitle == null ? "" : entry.subtitle.toLowerCase(Locale.getDefault());
                if (title.contains(value) || subtitle.contains(value)) entries.add(entry);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView title;
            TextView subtitle;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
                title = (TextView) row.getChildAt(0);
                subtitle = (TextView) row.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));
                row.setBackgroundDrawable(createRowSelector());
                row.setFocusable(false);
                title = text("", 18, COLOR_TEXT, true);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                subtitle = text("", 13, COLOR_SUB, false);
                subtitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                row.addView(title, new LinearLayout.LayoutParams(-1, -2));
                row.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
            }
            Entry entry = entries.get(position);
            title.setText(entry.title);
            String sub = entry.subtitle;
            if (entry.type == Entry.TYPE_SONG && currentSong != null && currentSong.id == entry.song.id) sub = "מתנגן עכשיו  •  " + currentSong.artist;
            if (sub == null || sub.length() == 0) sub = " ";
            subtitle.setText(sub);
            row.setSelected(position == selectedPosition);
            return row;
        }
    }

    private class TimelineProgressDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final RectF fill = new RectF();

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            float inset = dp(2);
            float left = bounds.left + inset;
            float right = bounds.right - inset;
            float centerY = bounds.exactCenterY();
            float halfHeight = Math.max(dp(2), dp(5) / 2f);
            track.set(left, centerY - halfHeight, right, centerY + halfHeight);
            paint.setColor(0xffd4dfe3);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(track, halfHeight, halfHeight, paint);

            float fraction = Math.max(0f, Math.min(1f, getLevel() / 10000f));
            float progressRight = left + (right - left) * fraction;
            if (progressRight > left) {
                fill.set(left, centerY - halfHeight, progressRight, centerY + halfHeight);
                paint.setColor(0xff00aabd);
                canvas.drawRoundRect(fill, halfHeight, halfHeight, paint);
            }
        }

        @Override protected boolean onLevelChange(int level) {
            invalidateSelf();
            return true;
        }

        @Override public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }

        @Override public int getIntrinsicHeight() {
            return dp(42);
        }
    }


    private Drawable createTimelineThumb() {
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xffffffff);
        thumb.setStroke(dp(2), 0xff008f9f);
        thumb.setSize(dp(16), dp(16));
        return thumb;
    }

    private Drawable modernPlayBackground() {
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff334e59, 0xff152b34});
        normal.setShape(GradientDrawable.OVAL);
        normal.setStroke(dp(2), 0xff68d3dc);
        GradientDrawable pressed = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff138c98, 0xff075866});
        pressed.setShape(GradientDrawable.OVAL);
        pressed.setStroke(dp(2), 0xffa6f0f3);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{android.R.attr.state_focused}, pressed);
        states.addState(new int[]{}, normal);
        return states;
    }

    private StateListDrawable stateRoundRect(int normalColor, int pressedColor, int radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, roundRect(pressedColor, radiusDp));
        states.addState(new int[]{android.R.attr.state_focused}, roundRect(pressedColor, radiusDp));
        states.addState(new int[]{}, roundRect(normalColor, radiusDp));
        return states;
    }

    /** Crisp, scalable transport icons drawn locally for reliable Android 4.4 rendering. */
    private class TransportButton extends View {
        static final int TYPE_PREVIOUS = 0;
        static final int TYPE_PLAY = 1;
        static final int TYPE_NEXT = 2;

        private final int type;
        private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean playing = false;
        private boolean longSeeking = false;
        private int holdDirection = 0;

        TransportButton(Activity context, int type) {
            super(context);
            this.type = type;
            setFocusable(true);
            setClickable(true);
        }

        void setSeekGesture(final int direction, final Runnable tap) {
            holdDirection = direction;
            setLongClickable(true);
            setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (!longSeeking && tap != null) tap.run();
                }
            });
            setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    // Native Android long-click recognition is more reliable than
                    // a manually timed ACTION_DOWN/ACTION_UP state machine on API 19.
                    longSeeking = true;
                    seekDirection = holdDirection;
                    heldSeekPosition = 0;
                    heldSeekDuration = 0;
                    handler.removeCallbacks(heldSeekRunnable);
                    beginContinuousSeek(holdDirection);
                    return true; // suppress the normal click so a held button never skips a track
                }
            });
        }

        void setPlaying(boolean value) {
            if (playing != value) {
                playing = value;
                invalidate();
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            boolean handled = super.onTouchEvent(event);
            if (holdDirection != 0
                    && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    && longSeeking) {
                longSeeking = false;
                seekDirection = 0;
                heldSeekPosition = 0;
                heldSeekDuration = 0;
                handler.removeCallbacks(heldSeekRunnable);
            }
            return handled;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float unit = Math.min(getWidth(), getHeight()) * (type == TYPE_PLAY ? 0.66f : 0.36f);
            iconPaint.setColor(type == TYPE_PLAY ? 0xffffffff : 0xff34434a);
            iconPaint.setStyle(Paint.Style.FILL);
            iconPaint.setStrokeWidth(dp(2.1f));
            iconPaint.setStrokeCap(Paint.Cap.ROUND);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);

            if (type == TYPE_PLAY) {
                if (playing) {
                    float barWidth = unit * 0.18f;
                    float barHeight = unit * 0.58f;
                    canvas.drawRoundRect(new RectF(cx - unit * 0.30f, cy - barHeight / 2f,
                            cx - unit * 0.30f + barWidth, cy + barHeight / 2f),
                            dp(2), dp(2), iconPaint);
                    canvas.drawRoundRect(new RectF(cx + unit * 0.12f, cy - barHeight / 2f,
                            cx + unit * 0.12f + barWidth, cy + barHeight / 2f),
                            dp(2), dp(2), iconPaint);
                } else {
                    Path play = new Path();
                    play.moveTo(cx - unit * 0.25f, cy - unit * 0.43f);
                    play.lineTo(cx + unit * 0.42f, cy);
                    play.lineTo(cx - unit * 0.25f, cy + unit * 0.43f);
                    play.close();
                    canvas.drawPath(play, iconPaint);
                }
                return;
            }

            Path triangle = new Path();
            float halfHeight = unit * 0.39f;
            if (type == TYPE_PREVIOUS) {
                triangle.moveTo(cx + unit * 0.20f, cy - halfHeight);
                triangle.lineTo(cx - unit * 0.32f, cy);
                triangle.lineTo(cx + unit * 0.20f, cy + halfHeight);
                triangle.close();
                canvas.drawPath(triangle, iconPaint);
                canvas.drawRoundRect(new RectF(cx + unit * 0.34f, cy - halfHeight,
                        cx + unit * 0.47f, cy + halfHeight), dp(1.2f), dp(1.2f), iconPaint);
            } else {
                triangle.moveTo(cx - unit * 0.20f, cy - halfHeight);
                triangle.lineTo(cx + unit * 0.32f, cy);
                triangle.lineTo(cx - unit * 0.20f, cy + halfHeight);
                triangle.close();
                canvas.drawPath(triangle, iconPaint);
                canvas.drawRoundRect(new RectF(cx - unit * 0.47f, cy - halfHeight,
                        cx - unit * 0.34f, cy + halfHeight), dp(1.2f), dp(1.2f), iconPaint);
            }
        }
    }

    private class RoundImageView extends ImageView {
        private final Path clipPath = new Path();
        private final RectF imageBounds = new RectF();
        private final float cornerRadius;

        RoundImageView(Activity context) {
            super(context);
            cornerRadius = 15f * getResources().getDisplayMetrics().density;
            setScaleType(ImageView.ScaleType.FIT_CENTER);
        }

        @Override protected void onDraw(Canvas canvas) {
            float contentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
            float contentHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            if (contentWidth <= 0 || contentHeight <= 0 || getDrawable() == null) {
                super.onDraw(canvas);
                return;
            }

            // Clip to the full view area; CENTER_CROP fills it instead of
            // leaving white/empty bands around square or rectangular artwork.
            imageBounds.set(getPaddingLeft(), getPaddingTop(),
                    getPaddingLeft() + contentWidth, getPaddingTop() + contentHeight);
            clipPath.reset();
            float radius = Math.min(cornerRadius, Math.min(imageBounds.width(), imageBounds.height()) / 2f);
            clipPath.addRoundRect(imageBounds, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clipPath);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static class MoodTagData {
        String key;
        int bpm;
    }

    private static class PlaylistImportResult {
        String displayName;
        String errorMessage;
        final ArrayList<Long> songIds = new ArrayList<Long>();
    }

    private static class PlaylistMatchIndex {
        final HashMap<String, Song> byUri = new HashMap<String, Song>();
        final HashMap<String, Song> byPath = new HashMap<String, Song>();
        final HashMap<String, Song> byFileName = new HashMap<String, Song>();
    }

    private static class UserPlaylist {
        final String name; final ArrayList<Long> songIds = new ArrayList<Long>();
        UserPlaylist(String name) { this.name = name; }
    }

    private static class Song {
        long id;
        long albumId;
        long duration;
        long dateAdded;
        String title;
        String artist;
        String album;
        String path;
        String albumArtPath;
        String albumArtist;
        String key;
        int bpm;
        boolean moodTagsScanned;
        int moodTagReaderVersion;
        Uri uri;
    }

    private static class AlbumGroup {
        final String title;
        final ArrayList<Song> songs = new ArrayList<Song>();
        AlbumGroup(String title) { this.title = title; }
    }

    private static class Entry {
        static final int TYPE_SONG = 1;
        static final int TYPE_ARTIST = 2;
        static final int TYPE_ALBUM = 3;
        static final int TYPE_PLAYLIST = 4;
        static final int TYPE_ARTIST_ALBUM = 5;
        static final int TYPE_ACTION = 6;
        final int type;
        final String title;
        final String subtitle;
        final Song song;
        final ArrayList<Song> songs;
        final int playlistId;
        final int actionId;
        private Entry(int type, String title, String subtitle, Song song, ArrayList<Song> songs, int playlistId) {
            this.type = type; this.title = title; this.subtitle = subtitle;
            this.song = song; this.songs = songs; this.playlistId = playlistId; this.actionId = 0;
        }
        private Entry(int type, String title, String subtitle, int actionId) {
            this.type = type; this.title = title; this.subtitle = subtitle;
            this.song = null; this.songs = null; this.playlistId = 0; this.actionId = actionId;
        }
        static Entry song(Song song) { return new Entry(TYPE_SONG, song.title, song.artist + "  •  " + song.album, song, null, 0); }
        static Entry artist(String name, ArrayList<Song> songs) { return new Entry(TYPE_ARTIST, name, songs.size() + " שירים", null, songs, 0); }
        static Entry album(String title, String artist, ArrayList<Song> songs) { return new Entry(TYPE_ALBUM, title, artist, null, songs, 0); }
        static Entry artistAlbum(String title, String artist, ArrayList<Song> songs) { return new Entry(TYPE_ARTIST_ALBUM, title, artist, null, songs, 0); }
        static Entry playlist(String title, String subtitle, int id) { return new Entry(TYPE_PLAYLIST, title, subtitle, null, null, id); }
        static Entry action(String title, String subtitle, int actionId) { return new Entry(TYPE_ACTION, title, subtitle, actionId); }
    }

}

