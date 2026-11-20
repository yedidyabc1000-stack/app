package com.yedidyabc1000.nagan44;

import android.Manifest;
import android.app.Activity;
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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

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
    private static final int COLOR_DARK = 0xff252525;
    private static final int COLOR_BAR = 0xff383838;
    private static final int COLOR_ACCENT = 0xff00b9cc;
    private static final int COLOR_BG = 0xffeeeeee;
    private static final int COLOR_ROW = 0xffffffff;
    private static final int COLOR_TEXT = 0xff202020;
    private static final int COLOR_SUB = 0xff777777;
    private static final String PREFS = "nagan_player_stats";

    private final ArrayList<Song> allSongs = new ArrayList<Song>();
    private final ArrayList<Song> queue = new ArrayList<Song>();
    private final Handler handler = new Handler();
    private final Random random = new Random();

    private MediaPlayer player;
    private Song currentSong;
    private int queueIndex = -1;
    private int activeTab = TAB_SONGS;
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
        if (hasStoragePermission()) {
            loadLibrary();
            renderBrowser();
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                loadLibrary();
                renderBrowser();
            } else {
                Toast.makeText(this, "נדרשת הרשאת אחסון כדי לסרוק שירים", Toast.LENGTH_LONG).show();
                renderPermissionScreen();
            }
        }
    }

    private void loadLibrary() {
        loading = true;
        allSongs.clear();
        Cursor cursor = null;
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
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    Song song = new Song();
                    song.id = id;
                    song.title = clean(cursor.getString(titleCol), "ללא שם");
                    song.artist = clean(cursor.getString(artistCol), "אמן לא ידוע");
                    song.album = clean(cursor.getString(albumCol), "אלבום לא ידוע");
                    song.albumId = albumIdCol >= 0 ? cursor.getLong(albumIdCol) : -1;
                    song.duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                    song.path = pathCol >= 0 ? cursor.getString(pathCol) : null;
                    song.dateAdded = dateCol >= 0 ? cursor.getLong(dateCol) : 0;
                    song.uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                    song.albumArtPath = lookupAlbumArt(song.albumId);
                    allSongs.add(song);
                }
            }
        } catch (Exception error) {
            Toast.makeText(this, "לא ניתן לסרוק את ספריית המוזיקה", Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
            loading = false;
        }
    }

    private String lookupAlbumArt(long albumId) {
        if (albumId < 0) return null;
        Cursor c = null;
        try {
            c = getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Audio.Albums.ALBUM_ART},
                    MediaStore.Audio.Albums._ID + "=?", new String[]{String.valueOf(albumId)}, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        finally { if (c != null) c.close(); }
        return null;
    }

    private String clean(String value, String fallback) {
        if (value == null || value.trim().length() == 0 || "<unknown>".equalsIgnoreCase(value.trim())) return fallback;
        return value.trim();
    }

    private void renderBrowser() {
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
            @Override public void onClick(View v) { loadLibrary(); renderBrowser(); }
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
            tabsScroll.addView(tabsRow);
            root.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(34)));
        }

        listView = new ListView(this);
        listView.setCacheColorHint(0x00000000);
        listView.setBackgroundColor(COLOR_BG);
        listView.setDivider(new android.graphics.drawable.ColorDrawable(0xffd8d8d8));
        listView.setDividerHeight(1);
        listView.setSelector(android.R.drawable.list_selector_background);
        listView.setFocusable(true);
        adapter = new BrowserAdapter();
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));
        listView.setOnItemClickListener((parent, view, position, id) -> onEntrySelected(position));

        miniPlayer = createMiniPlayer();
        if (currentSong != null) root.addView(miniPlayer, new LinearLayout.LayoutParams(-1, dp(44)));
        setContentView(root);
        populateBrowser();
        updateTabs();
        listView.requestFocus();
        if (selectedPosition >= 0 && selectedPosition < visibleEntries.size()) listView.setSelection(selectedPosition);
    }

    private void addTab(String label, final int tab) {
        TextView item = text(label, 14, COLOR_TEXT, true);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(13), 0, dp(13), 0);
        item.setFocusable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (detailLevel != 0) detailLevel = 0;
                activeTab = tab;
                selectedPosition = 0;
                renderBrowser();
            }
        });
        tabsRow.addView(item, new LinearLayout.LayoutParams(-2, -1));
    }

    private void updateTabs() {
        if (tabsRow == null) return;
        for (int i = 0; i < tabsRow.getChildCount(); i++) {
            TextView tab = (TextView) tabsRow.getChildAt(i);
            tab.setTextColor(i == activeTab ? COLOR_ACCENT : COLOR_TEXT);
            tab.setBackgroundColor(i == activeTab ? 0xfff8f8f8 : 0xffdddddd);
        }
    }

    private void populateBrowser() {
        visibleEntries.clear();
        if (detailLevel > 0) {
            // Detail lists are assigned explicitly by showEntries().
            adapter.notifyDataSetChanged();
            return;
        }
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
            visibleEntries.add(Entry.playlist("המושמעים ביותר", "השירים המושמעים ביותר לפי מספר השמעות", 1));
            visibleEntries.add(Entry.playlist("כל השירים", allSongs.size() + " שירים", 2));
            visibleEntries.add(Entry.playlist("נוספו לאחרונה", "השירים שנוספו למכשיר לאחרונה", 3));
        }
        adapter.notifyDataSetChanged();
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
        // renderBrowser does not repopulate detail lists.
        visibleEntries.clear();
        visibleEntries.addAll(entries);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showTrackList(String title, ArrayList<Song> songs) {
        ArrayList<Entry> entries = new ArrayList<Entry>();
        for (Song s : songs) entries.add(Entry.song(s));
        showEntries(title, entries);
    }

    private void onEntrySelected(int position) {
        if (position < 0 || position >= visibleEntries.size()) return;
        selectedPosition = position;
        Entry entry = visibleEntries.get(position);
        if (entry.type == Entry.TYPE_SONG) {
            ArrayList<Song> playQueue = new ArrayList<Song>();
            for (Entry e : visibleEntries) if (e.type == Entry.TYPE_SONG) playQueue.add(e.song);
            int index = playQueue.indexOf(entry.song);
            startTrack(playQueue, index);
        } else if (entry.type == Entry.TYPE_ARTIST) {
            ArrayList<AlbumGroup> albums = albumGroups(entry.songs);
            if (albums.size() > 1) {
                ArrayList<Entry> entries = new ArrayList<Entry>();
                for (AlbumGroup a : albums) entries.add(Entry.artistAlbum(a.title, artistLabel(a.songs), a.songs));
                showEntries(entry.title, entries);
            } else {
                showTrackList(entry.title, new ArrayList<Song>(entry.songs));
            }
        } else if (entry.type == Entry.TYPE_ALBUM || entry.type == Entry.TYPE_ARTIST_ALBUM) {
            showTrackList(entry.title, new ArrayList<Song>(entry.songs));
        } else if (entry.type == Entry.TYPE_PLAYLIST) {
            ArrayList<Song> selected = new ArrayList<Song>();
            if (entry.playlistId == 1) {
                Map<Long, Integer> counts = getPlayCounts();
                for (Song s : allSongs) if (counts.containsKey(s.id) && counts.get(s.id) > 0) selected.add(s);
                Collections.sort(selected, new Comparator<Song>() {
                    @Override public int compare(Song a, Song b) {
                        int ca = getCount(a.id), cb = getCount(b.id);
                        if (ca != cb) return cb - ca;
                        return STRING_SORT.compare(a.title, b.title);
                    }
                });
            } else if (entry.playlistId == 2) {
                selected.addAll(allSongs);
            } else {
                selected.addAll(allSongs);
                Collections.sort(selected, new Comparator<Song>() {
                    @Override public int compare(Song a, Song b) { return Long.compare(b.dateAdded, a.dateAdded); }
                });
            }
            showTrackList(entry.title, selected);
        }
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
        if (songs.size() == 0) return "";
        String artist = songs.get(0).artist;
        return artist;
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
        showingNowPlaying = true;
        nowPlayingRoot = new LinearLayout(this);
        nowPlayingRoot.setOrientation(LinearLayout.VERTICAL);
        nowPlayingRoot.setBackgroundColor(COLOR_BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        bar.setBackgroundColor(COLOR_DARK);
        TextView back = text("‹", 25, 0xffffffff, true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showingNowPlaying = false; renderBrowser(); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(36), -1));
        TextView title = text("מתנגן כעת", 18, 0xffffffff, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        bar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        nowPlayingRoot.addView(bar, new LinearLayout.LayoutParams(-1, dp(36)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.HORIZONTAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        details.setPadding(dp(14), dp(7), dp(14), dp(4));
        nowArt = new RoundArtView(this);
        Bitmap art = currentSong == null ? null : loadArtwork(currentSong);
        nowArt.setBitmap(art == null ? BitmapFactory.decodeResource(getResources(), R.drawable.albumart_mp_unknown_new) : art);
        int artSize = Math.min(dp(112), Math.max(dp(76), getResources().getDisplayMetrics().heightPixels / 3));
        details.addView(nowArt, new LinearLayout.LayoutParams(artSize, artSize));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        labels.setPadding(dp(12), 0, 0, 0);
        nowTitle = text(currentSong == null ? "אין שיר נבחר" : currentSong.title, 16, COLOR_TEXT, true);
        nowArtist = text(currentSong == null ? "" : currentSong.artist, 13, COLOR_SUB, false);
        nowAlbum = text(currentSong == null ? "" : currentSong.album, 12, COLOR_SUB, false);
        labels.addView(nowTitle, new LinearLayout.LayoutParams(-1, -2));
        labels.addView(nowArtist, new LinearLayout.LayoutParams(-1, -2));
        labels.addView(nowAlbum, new LinearLayout.LayoutParams(-1, -2));
        details.addView(labels, new LinearLayout.LayoutParams(0, -1, 1));
        nowPlayingRoot.addView(details, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout timeline = new LinearLayout(this);
        timeline.setOrientation(LinearLayout.VERTICAL);
        timeline.setPadding(dp(10), 0, dp(10), 0);
        seekBar = new SeekBar(this);
        seekBar.setMax(currentSong == null ? 1 : (int) Math.max(1, currentSong.duration));
        seekBar.setProgress(player != null ? safePosition() : 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                if (player != null) try { player.seekTo(bar.getProgress()); } catch (Exception ignored) { }
            }
        });
        timeline.addView(seekBar, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        elapsedText = text(formatTime(player == null ? 0 : safePosition()), 10, COLOR_SUB, false);
        durationText = text(formatTime(currentSong == null ? 0 : currentSong.duration), 10, COLOR_SUB, false);
        times.addView(elapsedText, new LinearLayout.LayoutParams(0, -2, 1));
        durationText.setGravity(Gravity.RIGHT);
        times.addView(durationText, new LinearLayout.LayoutParams(0, -2, 1));
        timeline.addView(times, new LinearLayout.LayoutParams(-1, -2));
        nowPlayingRoot.addView(timeline, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(6), dp(2), dp(6), dp(5));
        TextView shuffleControl = controlText(shuffle ? "⇄" : "⇄", shuffle ? COLOR_ACCENT : COLOR_SUB);
        shuffleButton = shuffleControl;
        shuffleControl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shuffle = !shuffle; updatePlayerButtons(); }
        });
        controls.addView(shuffleControl, new LinearLayout.LayoutParams(0, dp(42), 1));
        ImageButton previous = controlImage(R.drawable.play_previous_normal);
        previous.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playPrevious(); }
        });
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(42), 1));
        nowPlayButton = controlImage(isPlaying() ? R.drawable.ic_media_pause_new : R.drawable.ic_media_play_new);
        nowPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        controls.addView(nowPlayButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        ImageButton next = controlImage(R.drawable.play_next_normal);
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playNext(true); }
        });
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView repeatControl = controlText(repeatMode == 2 ? "1↻" : "↻", repeatMode == 0 ? COLOR_SUB : COLOR_ACCENT);
        repeatButton = repeatControl;
        repeatControl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { repeatMode = (repeatMode + 1) % 3; updatePlayerButtons(); }
        });
        controls.addView(repeatControl, new LinearLayout.LayoutParams(0, dp(42), 1));
        nowPlayingRoot.addView(controls, new LinearLayout.LayoutParams(-1, dp(48)));

        setContentView(nowPlayingRoot);
        updatePlayerButtons();
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
        if (song == null || song.albumArtPath == null) return null;
        try {
            File file = new File(song.albumArtPath);
            if (file.exists()) return BitmapFactory.decodeFile(song.albumArtPath);
        } catch (Exception ignored) { }
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
        updatePlayerButtons();
        if (showingNowPlaying) renderNowPlaying();
    }

    @Override public void onPrepared(MediaPlayer mp) {
        if (mp != player) return;
        try {
            mp.start();
            recordPlay(currentSong);
        } catch (IllegalStateException ignored) { }
        updatePlayerButtons();
        if (showingNowPlaying) renderNowPlaying();
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
        int next;
        if (shuffle && queue.size() > 1) {
            next = queueIndex;
            while (next == queueIndex) next = random.nextInt(queue.size());
        } else {
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
            showingNowPlaying = false;
            renderBrowser();
            return;
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
            activeTab = (activeTab + delta + 4) % 4;
            selectedPosition = 0;
            renderBrowser();
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
        @Override public int getCount() { return visibleEntries.size(); }
        @Override public Object getItem(int position) { return visibleEntries.get(position); }
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
                row.setPadding(dp(12), dp(5), dp(12), dp(5));
                row.setBackgroundColor(COLOR_ROW);
                title = text("", 15, COLOR_TEXT, true);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                subtitle = text("", 11, COLOR_SUB, false);
                subtitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
                row.addView(title, new LinearLayout.LayoutParams(-1, -2));
                row.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
            }
            Entry entry = visibleEntries.get(position);
            title.setText(entry.title);
            String sub = entry.subtitle;
            if (entry.type == Entry.TYPE_SONG && currentSong != null && currentSong.id == entry.song.id) sub = "מתנגן עכשיו  •  " + currentSong.artist;
            if (sub == null || sub.length() == 0) sub = " ";
            subtitle.setText(sub);
            return row;
        }
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
        final int type;
        final String title;
        final String subtitle;
        final Song song;
        final ArrayList<Song> songs;
        final int playlistId;
        private Entry(int type, String title, String subtitle, Song song, ArrayList<Song> songs, int playlistId) {
            this.type = type; this.title = title; this.subtitle = subtitle;
            this.song = song; this.songs = songs; this.playlistId = playlistId;
        }
        static Entry song(Song song) { return new Entry(TYPE_SONG, song.title, song.artist + "  •  " + song.album, song, null, 0); }
        static Entry artist(String name, ArrayList<Song> songs) { return new Entry(TYPE_ARTIST, name, songs.size() + " שירים", null, songs, 0); }
        static Entry album(String title, String artist, ArrayList<Song> songs) { return new Entry(TYPE_ALBUM, title, artist, null, songs, 0); }
        static Entry artistAlbum(String title, String artist, ArrayList<Song> songs) { return new Entry(TYPE_ARTIST_ALBUM, title, artist, null, songs, 0); }
        static Entry playlist(String title, String subtitle, int id) { return new Entry(TYPE_PLAYLIST, title, subtitle, null, null, id); }
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
