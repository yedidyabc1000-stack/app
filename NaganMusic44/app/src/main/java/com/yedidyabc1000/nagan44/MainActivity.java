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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.BitmapShader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
    private static final int REQUEST_IMPORT_PLAYLIST = 411;
    private static final int CUSTOM_PLAYLIST_BASE = 1000;
    private static final int COLOR_DARK = 0xff252525;
    private static final int COLOR_BAR = 0xff383838;
    private static final int COLOR_ACCENT = 0xff00b9cc;
    private static final int COLOR_BG = 0xffeeeeee;
    private static final int COLOR_ROW = 0xffffffff;
    private static final int COLOR_TEXT = 0xff202020;
    private static final int COLOR_SUB = 0xff777777;
    private static final String PREFS = "nagan_player_stats";
    private static final String TAG = "NaganMusic44";
    private static final int COLOR_FOCUS = 0xffd6f0f2;
    private static final int COLOR_FOCUS_PRESSED = 0xffb6e4e8;

    private final ArrayList<Song> allSongs = new ArrayList<Song>();
    private final ArrayList<Song> queue = new ArrayList<Song>();
    private final ArrayList<UserPlaylist> customPlaylists = new ArrayList<UserPlaylist>();
    private final Handler handler = new Handler();
    private final Random random = new Random();

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
    private RoundArtView nowArt;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowAlbum;
    private TextView elapsedText;
    private TextView durationText;
    private SeekBar seekBar;
    private ImageButton nowPlayButton;
    private TextView shuffleButton;
    private TextView repeatButton;
    private boolean userSeeking = false;
    private int seekDirection = 0;
    private final Runnable heldSeekRunnable = new Runnable() {
        @Override public void run() {
            if (seekDirection != 0) { seekBy(seekDirection * 2000); handler.postDelayed(this, 350L); }
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        handler.post(progressRunnable);
        loadUserPlaylists();
        if (hasStoragePermission()) {
            loadLibraryAsync();
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
        if (hasStoragePermission()) {
            registerMediaObserver();
            if (allSongs.size() > 0) handler.postDelayed(scanChangedMedia, 700L);
        }
    }

    @Override protected void onPause() {
        unregisterMediaObserver(); handler.removeCallbacks(scanChangedMedia); super.onPause();
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
        if (showLoading) renderLoadingScreen();
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                final ArrayList<Song> loadedSongs = scanLibrary();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (isFinishing() || token != libraryLoadToken) return;
                        reconcileLibrary(loadedSongs);
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
            cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection, MediaStore.Audio.Media.IS_MUSIC + "=1", null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC");
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
                        if (!isVisibleAudioPath(song.path)) continue;
                        song.dateAdded = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                        song.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        song.albumArtPath = artworkByAlbum.get(song.albumId);
                        songs.add(song);
                    }
                }
            }
        } catch (Exception ignored) {
            // Return any songs found so far; the UI thread remains responsive.
        } finally {
            if (cursor != null) cursor.close();
        }
        return songs;
    }

    private boolean isVisibleAudioPath(String path) {
        if (path == null || path.length() == 0) return false;
        String normalized = path.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.US);
        if (lower.contains("/android/data/") || lower.contains("/android/obb/") || lower.contains("/lost.dir/")) return false;
        for (String part : normalized.split("/")) if (part.length() > 1 && part.charAt(0) == '.') return false;
        File parent = new File(path).getParentFile();
        while (parent != null) {
            if (parent.getName().startsWith(".") || new File(parent, ".nomedia").exists()) return false;
            parent = parent.getParentFile();
        }
        return true;
    }

    private void reconcileLibrary(ArrayList<Song> loadedSongs) {
        HashMap<Long, Song> byId = new HashMap<Long, Song>();
        for (Song song : loadedSongs) byId.put(song.id, song);
        Song oldCurrent = currentSong;
        allSongs.clear(); allSongs.addAll(loadedSongs);
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

    private void renderBrowser() {
        if (detailLevel == 0 && activeTab == TAB_NOW_PLAYING) { renderNowPlaying(); return; }
        showingNowPlaying = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        toolbar.setBackgroundColor(COLOR_DARK);
        toolbarBack = text(detailLevel > 0 ? "‹" : "♫", 25, 0xffffffff, true);
        toolbarBack.setGravity(Gravity.CENTER);
        toolbarBack.setFocusable(false);
        toolbarBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        toolbar.addView(toolbarBack, new LinearLayout.LayoutParams(dp(36), -1));
        toolbarTitle = text(detailLevel > 0 ? detailTitle : "נגן מוזיקה", 18, 0xffffffff, true);
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

        if (detailLevel == 0) {
            tabsScroll = new HorizontalScrollView(this);
            tabsScroll.setHorizontalScrollBarEnabled(false);
            tabsScroll.setBackgroundColor(0xffdddddd);
            tabsRow = new LinearLayout(this);
            tabsRow.setOrientation(LinearLayout.HORIZONTAL);
            addTab("שירים", TAB_SONGS);
            addTab("אמנים", TAB_ARTISTS);
            addTab("אלבומים", TAB_ALBUMS);
            addTab("פלייליסטים", TAB_PLAYLISTS);
            addTab("מתנגן עכשיו", TAB_NOW_PLAYING);
            tabsScroll.addView(tabsRow);
            root.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(34)));
        }

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
        if (detailLevel == 0 && activeTab == TAB_SONGS) {
            final EditText search = new EditText(this);
            search.setSingleLine(true);
            search.setTextSize(16);
            search.setHint("חיפוש שירים");
            search.setTextColor(COLOR_TEXT);
            search.setHintTextColor(COLOR_SUB);
            search.setPadding(dp(12), 0, dp(12), 0);
            search.setBackgroundDrawable(roundRect(0xffffffff, 7));
            listView.addHeaderView(search, null, false);
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                    if (adapter != null) { selectedPosition = 0; adapter.filter(value.toString()); }
                }
                @Override public void afterTextChanged(Editable value) { }
            });
        }
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
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
                    if (detailLevel != 0) detailLevel = 0;
                    activeTab = tab;
                    if (tab != TAB_NOW_PLAYING) lastContentTab = tab;
                    selectedPosition = 0;
                    if (tab == TAB_NOW_PLAYING) renderNowPlaying(); else renderBrowser();
                } catch (RuntimeException error) {
                    reportUiError("מעבר בין לשוניות", error);
                }
            }
        });
        tabsRow.addView(item, new LinearLayout.LayoutParams(-2, -1));
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
        // Detail entries are already held in visibleEntries by showEntries().
        // The adapter gets a private snapshot after this method returns.
        if (detailLevel > 0) return;

        visibleEntries.clear();
        if (activeTab == TAB_SONGS) {
            for (Song s : allSongs) visibleEntries.add(Entry.song(s));
        } else if (activeTab == TAB_ARTISTS) {
            Map<String, ArrayList<Song>> artists = artistIndex();
            ArrayList<String> names = new ArrayList<String>(artists.keySet());
            Collections.sort(names, STRING_SORT);
            for (String name : names) visibleEntries.add(Entry.artist(name, artists.get(name)));
        } else if (activeTab == TAB_ALBUMS) {
            ArrayList<AlbumGroup> albums = albumGroups(allSongs);
            for (AlbumGroup a : albums) visibleEntries.add(Entry.album(a.title, artistLabel(a.songs), a.songs));
        } else {
            visibleEntries.add(Entry.action("יצירת רשימת השמעה", "יצירת פלייליסט חדש באפליקציה", 1));
            visibleEntries.add(Entry.action("ייבוא רשימת השמעה", "טעינת קובץ M3U או PLS מהמכשיר", 2));
            for (int i = 0; i < customPlaylists.size(); i++) {
                UserPlaylist p = customPlaylists.get(i);
                visibleEntries.add(Entry.playlist(p.name, p.songIds.size() + " שירים", CUSTOM_PLAYLIST_BASE + i));
            }
            visibleEntries.add(Entry.playlist("המושמעים ביותר", "השירים המושמעים ביותר לפי מספר השמעות", 1));
            visibleEntries.add(Entry.playlist("כל השירים", allSongs.size() + " שירים", 2));
            visibleEntries.add(Entry.playlist("נוספו לאחרונה", "השירים שנוספו למכשיר לאחרונה", 3));
        }
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
                        customPlaylists.add(playlist); saveUserPlaylists();
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

    private void importPlaylistFile(Uri uri) {
        BufferedReader reader = null; ArrayList<Song> selected = new ArrayList<Song>(); String displayName = null;
        Cursor nameCursor = null;
        try { nameCursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null); if (nameCursor != null && nameCursor.moveToFirst()) displayName = nameCursor.getString(0); }
        catch (Exception ignored) { } finally { if (nameCursor != null) nameCursor.close(); }
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IllegalStateException("קובץ לא זמין");
            reader = new BufferedReader(new InputStreamReader(input, "UTF-8")); String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim(); if (line.length() == 0 || line.startsWith("#")) continue;
                if (line.startsWith("File") && line.indexOf('=') > 0) line = line.substring(line.indexOf('=') + 1).trim();
                Song song = findPlaylistSong(line); if (song != null && !selected.contains(song)) selected.add(song);
            }
        } catch (Exception error) { Toast.makeText(this, "לא ניתן לקרוא את קובץ רשימת ההשמעה", Toast.LENGTH_LONG).show(); return; }
        finally { if (reader != null) try { reader.close(); } catch (Exception ignored) { } }
        if (selected.size() == 0) { Toast.makeText(this, "לא נמצאו בקובץ שירים שקיימים בספריית הנגן", Toast.LENGTH_LONG).show(); return; }
        if (displayName == null || displayName.length() == 0) displayName = "רשימה מיובאת";
        int dot = displayName.lastIndexOf('.'); if (dot > 0) displayName = displayName.substring(0, dot);
        UserPlaylist playlist = new UserPlaylist(displayName); for (Song song : selected) playlist.songIds.add(song.id);
        customPlaylists.add(playlist); saveUserPlaylists();
        Toast.makeText(this, "יובאו " + selected.size() + " שירים", Toast.LENGTH_LONG).show();
        activeTab = TAB_PLAYLISTS; lastContentTab = TAB_PLAYLISTS; detailLevel = 0; renderBrowser();
    }

    private Song findPlaylistSong(String rawPath) {
        if (rawPath == null) return null;
        String candidate = rawPath.trim().replace('\\', '/');
        if (candidate.startsWith("file://")) try { candidate = Uri.decode(Uri.parse(candidate).getPath()); } catch (Exception ignored) { }
        String normalized = candidate.toLowerCase(Locale.US); String fileName = new File(candidate).getName().toLowerCase(Locale.US);
        for (Song song : allSongs) {
            if (song.uri != null && song.uri.toString().equalsIgnoreCase(candidate)) return song;
            if (song.path != null) {
                String p = song.path.replace('\\', '/'); String lower = p.toLowerCase(Locale.US);
                if (lower.equals(normalized) || lower.endsWith("/" + normalized) || normalized.endsWith("/" + lower)) return song;
                if (new File(p).getName().equalsIgnoreCase(fileName)) return song;
            }
        }
        return null;
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

    private Map<String, ArrayList<Song>> artistIndex() {
        Map<String, ArrayList<Song>> result = new LinkedHashMap<String, ArrayList<Song>>();
        for (Song song : allSongs) {
            String[] names = splitArtists(song.artist);
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

        tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setBackgroundColor(0xffdddddd);
        tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        addTab("שירים", TAB_SONGS);
        addTab("אמנים", TAB_ARTISTS);
        addTab("אלבומים", TAB_ALBUMS);
        addTab("פלייליסטים", TAB_PLAYLISTS);
        addTab("מתנגן עכשיו", TAB_NOW_PLAYING);
        tabsScroll.addView(tabsRow);
        nowPlayingRoot.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(34)));
        updateTabs();

        // Fixed, non-scrolling layout. Artwork receives only the height left
        // after the song details, timeline and transport controls are placed.
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(12), 0, dp(12), 0);

        FrameLayout artHolder = new FrameLayout(this);
        nowArt = new RoundArtView(this);
        Bitmap art = currentSong == null ? null : loadArtwork(currentSong);
        nowArt.setBitmap(art == null
                ? BitmapFactory.decodeResource(getResources(), R.drawable.albumart_mp_unknown_new) : art);
        artHolder.addView(nowArt, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        artParams.bottomMargin = dp(2);
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

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.setPadding(dp(3), 0, dp(3), 0);
        seekBar = new SeekBar(this);
        seekBar.setMax(currentSong == null ? 1 : (int)Math.max(1, currentSong.duration));
        seekBar.setProgress(player != null ? safePosition() : 0);

        android.graphics.drawable.GradientDrawable bg = roundRect(0xffd4d9de, 2);
        bg.setSize(-1, dp(2));
        android.graphics.drawable.GradientDrawable fill = roundRect(COLOR_ACCENT, 2);
        fill.setSize(-1, dp(2));
        android.graphics.drawable.ClipDrawable clipped = new android.graphics.drawable.ClipDrawable(
                fill, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers =
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{bg, clipped});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        seekBar.setProgressDrawable(layers);
        android.graphics.drawable.GradientDrawable thumb = roundRect(0xff202020, 5);
        thumb.setSize(dp(9), dp(9));
        seekBar.setThumb(thumb);
        seekBar.setPadding(dp(3), 0, dp(3), 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null) try { player.seekTo(bar.getProgress()); } catch (Exception ignored) { }
            }
        });
        timeline.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(20)));
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        elapsedText = text(formatTime(player == null ? 0 : safePosition()), 11, COLOR_SUB, false);
        durationText = text(formatTime(currentSong == null ? 0 : currentSong.duration), 11, COLOR_SUB, false);
        times.addView(elapsedText, new LinearLayout.LayoutParams(0, -2, 1));
        durationText.setGravity(Gravity.RIGHT);
        times.addView(durationText, new LinearLayout.LayoutParams(0, -2, 1));
        timeline.addView(times, new LinearLayout.LayoutParams(-1, dp(13)));
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(-1, dp(34));
        timelineParams.topMargin = dp(1);
        content.addView(timeline, timelineParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(0, dp(1), 0, dp(1));
        TransportButton previous = new TransportButton(this, false);
        previous.setContentDescription("השיר הקודם; לחיצה ארוכה: הרצה אחורה");
        previous.setBackgroundDrawable(roundRect(0xffe7ecef, 24));
        previous.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playPrevious(); }
        });
        installHoldSeek(previous, -1);
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(dp(50), dp(40));
        sideParams.rightMargin = dp(16);
        controls.addView(previous, sideParams);

        nowPlayButton = controlImage(isPlaying()
                ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        nowPlayButton.setBackgroundDrawable(roundRect(COLOR_ACCENT, 25));
        nowPlayButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        nowPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        controls.addView(nowPlayButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TransportButton next = new TransportButton(this, true);
        next.setContentDescription("השיר הבא; לחיצה ארוכה: הרצה קדימה");
        next.setBackgroundDrawable(roundRect(0xffe7ecef, 24));
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playNext(true); }
        });
        installHoldSeek(next, 1);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(50), dp(40));
        nextParams.leftMargin = dp(16);
        controls.addView(next, nextParams);
        content.addView(controls, new LinearLayout.LayoutParams(-1, dp(46)));

        nowPlayingRoot.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(nowPlayingRoot);
        updatePlayerButtons();
    }

    private void installHoldSeek(View control, final int direction) {
        control.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                seekDirection = direction;
                handler.removeCallbacks(heldSeekRunnable);
                seekBy(direction * 2000);
                handler.postDelayed(heldSeekRunnable, 350L);
                return true;
            }
        });
        control.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (seekDirection == direction) {
                        seekDirection = 0;
                        handler.removeCallbacks(heldSeekRunnable);
                    }
                }
                return false;
            }
        });
    }

    private void seekBy(int deltaMs) {
        if (player == null) return;
        try {
            int duration = player.getDuration();
            int current = player.getCurrentPosition();
            int target = Math.max(0, Math.min(duration, current + deltaMs));
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

    private Bitmap loadArtwork(Song song) {
        if (song == null) return null;
        if (song.albumArtPath != null) {
            try {
                File file = new File(song.albumArtPath);
                if (file.exists()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(song.albumArtPath);
                    if (bitmap != null) return bitmap;
                }
            } catch (Exception ignored) { }
        }

        // Some Android 4.4 music libraries do not expose a readable ALBUM_ART path.
        // The MediaStore album-art provider can still supply the image by album ID.
        if (song.albumId >= 0) {
            java.io.InputStream input = null;
            try {
                Uri artUri = Uri.parse("content://media/external/audio/albumart/" + song.albumId);
                input = getContentResolver().openInputStream(artUri);
                if (input != null) return BitmapFactory.decodeStream(input);
            } catch (Exception ignored) {
            } finally {
                if (input != null) {
                    try { input.close(); } catch (Exception ignored) { }
                }
            }
        }
        return null;
    }

    private void updatePlayerButtons() {
        boolean playing = isPlaying();
        if (nowPlayButton != null) nowPlayButton.setImageResource(playing ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        if (miniPlay != null) miniPlay.setImageResource(playing ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        if (shuffleButton != null) shuffleButton.setTextColor(shuffle ? COLOR_ACCENT : COLOR_SUB);
        if (repeatButton != null) {
            repeatButton.setText(repeatMode == 2 ? "1↻" : "↻");
            repeatButton.setTextColor(repeatMode == 0 ? COLOR_SUB : COLOR_ACCENT);
        }
        updateMiniPlayerText();
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
        player = new MediaPlayer();
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setOnPreparedListener(this);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setDataSource(this, currentSong.uri);
            player.prepareAsync();
            Toast.makeText(this, currentSong.title, Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "לא ניתן לנגן את הקובץ", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
        showingNowPlaying = true;
        renderNowPlaying();
        updatePlayerButtons();
    }

    @Override public void onPrepared(MediaPlayer mp) {
        if (mp != player) return;
        try {
            mp.start();
            recordPlay(currentSong);
        } catch (IllegalStateException ignored) { }
        updatePlayerButtons();
    }

    @Override public void onCompletion(MediaPlayer mp) {
        if (repeatMode == 2 && player != null) {
            try { player.seekTo(0); player.start(); return; } catch (Exception ignored) { }
        }
        playNext(false);
    }

    @Override public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(this, "שגיאה בהשמעת השיר", Toast.LENGTH_SHORT).show();
        releasePlayer();
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
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
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
            int delta = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            activeTab = (activeTab + delta + 5) % 5;
            if (activeTab != TAB_NOW_PLAYING) lastContentTab = activeTab;
            selectedPosition = 0; renderBrowser();
            return true;
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

    private static class TransportButton extends View {
        private final boolean next;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TransportButton(Activity context, boolean next) {
            super(context);
            this.next = next;
            setFocusable(true);
            setClickable(true);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float unit = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setColor(0xff27343b);
            paint.setStyle(Paint.Style.FILL);
            Path triangle = new Path();
            if (next) {
                triangle.moveTo(cx - 5f * unit, cy - 10f * unit);
                triangle.lineTo(cx - 5f * unit, cy + 10f * unit);
                triangle.lineTo(cx + 7f * unit, cy);
                canvas.drawRoundRect(cx + 9f * unit, cy - 10f * unit,
                        cx + 12f * unit, cy + 10f * unit, unit, unit, paint);
            } else {
                triangle.moveTo(cx + 5f * unit, cy - 10f * unit);
                triangle.lineTo(cx + 5f * unit, cy + 10f * unit);
                triangle.lineTo(cx - 7f * unit, cy);
                canvas.drawRoundRect(cx - 12f * unit, cy - 10f * unit,
                        cx - 9f * unit, cy + 10f * unit, unit, unit, paint);
            }
            triangle.close();
            canvas.drawPath(triangle, paint);
        }
    }

    /** Album-cover renderer: FIT_CENTER sizing with a small rounded corner radius. */
    private static class RoundArtView extends View {
        private Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Matrix matrix = new Matrix();
        private final Path clip = new Path();
        private final RectF rect = new RectF();
        RoundArtView(Activity context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setBitmap(Bitmap bitmap) { this.bitmap = bitmap; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) return;
            float scale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = (getWidth() - width) / 2f;
            float top = (getHeight() - height) / 2f;
            rect.set(left, top, left + width, top + height);
            clip.reset();
            clip.addRoundRect(rect, 6f * getResources().getDisplayMetrics().density,
                    6f * getResources().getDisplayMetrics().density, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(scale, scale);
            matrix.postTranslate(left, top);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            canvas.restore();
        }
    }
}
