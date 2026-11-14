package com.yedidyabc1000.nagan;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_STORAGE = 401;

    private final ArrayList<Song> songs = new ArrayList<Song>();
    private final ArrayList<View> songRows = new ArrayList<View>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean preparing = false;
    private boolean permissionRequested = false;

    private LinearLayout songList;
    private TextView countLabel;
    private TextView titleLabel;
    private TextView artistLabel;
    private TextView timeLabel;
    private TextView playIcon;
    private TextView emptyLabel;
    private ProgressBar progressBar;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private static class Song {
        long id;
        String title;
        String artist;
        String album;
        long duration;

        Song(long id, String title, String artist, String album, long duration) {
            this.id = id;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.duration = duration;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        buildInterface();

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(11, 16, 32));
        if (Build.VERSION.SDK_INT >= 21) {
            window.setNavigationBarColor(Color.rgb(11, 16, 32));
        }

        if (hasStoragePermission()) {
            scanLibrary();
        } else {
            showPermissionMessage();
            requestStoragePermission();
        }
        handler.post(progressUpdater);
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.setBackgroundColor(Color.rgb(11, 16, 32));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView mark = new TextView(this);
        mark.setText("♫");
        mark.setTextSize(25);
        mark.setTextColor(Color.rgb(11, 16, 32));
        mark.setGravity(Gravity.CENTER);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setBackground(roundDrawable(Color.rgb(39, 215, 179), 0, 0, 100));
        header.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(10), 0, 0, 0);

        TextView brand = makeText("נגן", 20, Color.rgb(245, 249, 255), true);
        TextView tagline = makeText("המוזיקה שלך. פשוט.", 11, Color.rgb(144, 158, 180), false);
        brandText.addView(brand);
        brandText.addView(tagline);
        header.addView(brandText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView refresh = makeText("רענון", 12, Color.rgb(39, 215, 179), true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(dp(12), dp(8), dp(12), dp(8));
        refresh.setFocusable(true);
        refresh.setClickable(true);
        refresh.setBackground(focusDrawable(Color.rgb(23, 39, 54), Color.rgb(39, 215, 179), 100));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { scanLibrary(); }
        });
        header.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout playerCard = new LinearLayout(this);
        playerCard.setOrientation(LinearLayout.VERTICAL);
        playerCard.setGravity(Gravity.CENTER_HORIZONTAL);
        playerCard.setPadding(dp(16), dp(13), dp(16), dp(12));
        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(23, 49, 63), Color.rgb(21, 30, 52), Color.rgb(25, 27, 48)});
        cardBg.setCornerRadius(dp(22));
        cardBg.setStroke(dp(1), Color.rgb(48, 67, 84));
        playerCard.setBackground(cardBg);

        TextView section = makeText("מנגן עכשיו", 11, Color.rgb(39, 215, 179), true);
        section.setGravity(Gravity.CENTER);
        playerCard.addView(section);

        titleLabel = makeText("בחר שיר מהספרייה", 20, Color.rgb(247, 250, 255), true);
        titleLabel.setGravity(Gravity.CENTER);
        titleLabel.setMaxLines(2);
        titleLabel.setPadding(0, dp(5), 0, 0);
        playerCard.addView(titleLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        artistLabel = makeText("האמנים שלך יופיעו כאן", 12, Color.rgb(156, 174, 194), false);
        artistLabel.setGravity(Gravity.CENTER);
        artistLabel.setPadding(0, dp(3), 0, dp(7));
        playerCard.addView(artistLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgress(0);
        if (Build.VERSION.SDK_INT >= 21) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(39, 215, 179)));
            progressBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(63, 81, 99)));
        }
        playerCard.addView(progressBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        timeLabel = makeText("0:00  /  0:00", 10, Color.rgb(145, 162, 183), false);
        timeLabel.setGravity(Gravity.CENTER);
        timeLabel.setPadding(0, dp(4), 0, dp(6));
        playerCard.addView(timeLabel);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        View previous = createControl("|◀", "הקודם", false, new View.OnClickListener() {
            @Override public void onClick(View v) { previousSong(); }
        });
        playIcon = new TextView(this);
        View play = createControlWithIcon(playIcon, "▶", "הפעל", true, new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayback(); }
        });
        View next = createControl("▶|", "הבא", false, new View.OnClickListener() {
            @Override public void onClick(View v) { nextSong(); }
        });

        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(0, dp(62), 1);
        controlParams.setMargins(dp(5), 0, dp(5), 0);
        controls.addView(previous, controlParams);
        controls.addView(play, controlParams);
        controls.addView(next, controlParams);
        playerCard.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(9);
        root.addView(playerCard, cardParams);

        LinearLayout libraryHeader = new LinearLayout(this);
        libraryHeader.setOrientation(LinearLayout.HORIZONTAL);
        libraryHeader.setGravity(Gravity.CENTER_VERTICAL);
        libraryHeader.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        libraryHeader.setPadding(dp(2), dp(10), dp(2), dp(6));

        TextView libraryTitle = makeText("הספרייה שלך", 15, Color.rgb(238, 243, 250), true);
        libraryHeader.addView(libraryTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        countLabel = makeText("0 שירים", 11, Color.rgb(137, 153, 175), false);
        libraryHeader.addView(countLabel);
        root.addView(libraryHeader, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(true);
        listScroll.setVerticalScrollBarEnabled(false);
        songList = new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);
        songList.setPadding(0, dp(1), 0, dp(2));
        listScroll.addView(songList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(listScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        emptyLabel = makeText("לא נמצאו שירים במכשיר", 13, Color.rgb(143, 160, 182), false);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setPadding(dp(10), dp(16), dp(10), dp(16));
        songList.addView(emptyLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = makeText("↑  ↓   ניווט     •     OK   בחירה", 10, Color.rgb(105, 123, 147), false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(5), 0, 0);
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        setContentView(root);
    }

    private View createControl(String symbol, String label, boolean primary, View.OnClickListener click) {
        TextView icon = makeText(symbol, 22, primary ? Color.rgb(10, 26, 35) : Color.rgb(220, 232, 243), true);
        return createControlWithIcon(icon, symbol, label, primary, click);
    }

    private View createControlWithIcon(TextView icon, String symbol, String label, boolean primary, View.OnClickListener click) {
        final int normal = primary ? Color.rgb(39, 215, 179) : Color.rgb(34, 47, 66);
        final int focused = primary ? Color.rgb(79, 237, 202) : Color.rgb(42, 70, 86);

        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.VERTICAL);
        control.setGravity(Gravity.CENTER);
        control.setFocusable(true);
        control.setFocusableInTouchMode(true);
        control.setClickable(true);
        control.setPadding(dp(3), dp(4), dp(3), dp(3));
        control.setBackground(focusDrawable(normal, focused, 15));
        icon.setText(symbol);
        icon.setTextSize(primary ? 27 : 22);
        icon.setTextColor(primary ? Color.rgb(9, 31, 37) : Color.rgb(226, 237, 247));
        icon.setGravity(Gravity.CENTER);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        control.addView(icon, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView caption = makeText(label, 10, primary ? Color.rgb(9, 31, 37) : Color.rgb(165, 183, 203), true);
        caption.setGravity(Gravity.CENTER);
        control.addView(caption);

        control.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(focusDrawable(hasFocus ? focused : normal,
                        hasFocus ? Color.rgb(111, 250, 220) : Color.rgb(51, 69, 87), 15));
            }
        });
        control.setOnClickListener(click);
        return control;
    }

    private void showPermissionMessage() {
        if (emptyLabel != null) {
            emptyLabel.setText("כדי להציג שירים, צריך לאשר גישה לקובצי מוזיקה");
        }
    }

    private boolean hasStoragePermission() {
        return Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && !permissionRequested) {
            permissionRequested = true;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanLibrary();
            } else if (emptyLabel != null) {
                emptyLabel.setText("אין גישה למוזיקה. אפשר לאשר אותה בהגדרות המכשיר.");
            }
        }
    }

    private void scanLibrary() {
        if (!hasStoragePermission()) {
            showPermissionMessage();
            requestStoragePermission();
            return;
        }

        songs.clear();
        songRows.clear();
        songList.removeAllViews();

        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION
            };
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            cursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            );

            if (cursor != null) {
                int idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    String title = safeText(cursor.getString(titleCol), "ללא שם");
                    String artist = safeText(cursor.getString(artistCol), "אמן לא ידוע");
                    String album = safeText(cursor.getString(albumCol), "");
                    long duration = durationCol >= 0 ? cursor.getLong(durationCol) : 0;
                    songs.add(new Song(id, title, artist, album, duration));
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "אין הרשאה לקריאת קובצי המוזיקה", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן לסרוק את ספריית המוזיקה", Toast.LENGTH_LONG).show();
        } finally {
            if (cursor != null) cursor.close();
        }

        countLabel.setText(songs.size() + (songs.size() == 1 ? " שיר" : " שירים"));
        if (songs.isEmpty()) {
            emptyLabel = makeText("לא נמצאו שירים במכשיר\nהעתק קובצי MP3 או מוזיקה למכשיר ולחץ רענון", 12,
                    Color.rgb(143, 160, 182), false);
            emptyLabel.setGravity(Gravity.CENTER);
            emptyLabel.setPadding(dp(12), dp(18), dp(12), dp(18));
            songList.addView(emptyLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            emptyLabel = null;
            for (int i = 0; i < songs.size(); i++) {
                addSongRow(i);
            }
        }
    }

    private void addSongRow(final int index) {
        final Song song = songs.get(index);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setPadding(dp(11), dp(7), dp(11), dp(7));
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setMinimumHeight(dp(55));
        row.setBackground(focusDrawable(Color.rgb(19, 29, 47), Color.rgb(41, 67, 82), 13));

        TextView note = makeText("♫", 19, Color.rgb(39, 215, 179), true);
        note.setGravity(Gravity.CENTER);
        row.addView(note, new LinearLayout.LayoutParams(dp(34), dp(36)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(9), 0, dp(4), 0);

        TextView songTitle = makeText(song.title, 13, Color.rgb(235, 241, 249), true);
        songTitle.setSingleLine(true);
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView artist = makeText(song.artist, 10, Color.rgb(132, 151, 174), false);
        artist.setSingleLine(true);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(songTitle);
        labels.addView(artist);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView duration = makeText(formatTime(song.duration), 10, Color.rgb(119, 139, 163), false);
        duration.setGravity(Gravity.CENTER);
        row.addView(duration, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) {
                int color = (hasFocus || index == currentIndex)
                        ? Color.rgb(29, 63, 70) : Color.rgb(19, 29, 47);
                v.setBackground(focusDrawable(color, hasFocus ? Color.rgb(39, 215, 179) : Color.rgb(41, 57, 76), 13));
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playSong(index); }
        });

        songRows.add(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(5));
        songList.addView(row, params);
    }

    private void playSong(int index) {
        if (index < 0 || index >= songs.size()) return;
        currentIndex = index;
        final Song song = songs.get(index);
        titleLabel.setText(song.title);
        artistLabel.setText(song.artist + (song.album.length() > 0 ? "  •  " + song.album : ""));
        timeLabel.setText("0:00  /  " + formatTime(song.duration));
        progressBar.setProgress(0);
        setPlayIcon(false);
        updateRowHighlights();

        releasePlayer();
        preparing = true;

        try {
            Uri trackUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id);
            player = new MediaPlayer();
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(this, trackUri);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) {
                    preparing = false;
                    mp.start();
                    setPlayIcon(true);
                    if (mp.getDuration() > 0) {
                        timeLabel.setText("0:00  /  " + formatTime(mp.getDuration()));
                    }
                    updateProgress();
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) {
                    nextSong();
                }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    preparing = false;
                    releasePlayer();
                    setPlayIcon(false);
                    Toast.makeText(MainActivity.this, "לא ניתן לנגן את הקובץ הזה", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            preparing = false;
            releasePlayer();
            setPlayIcon(false);
            Toast.makeText(this, "שגיאה בפתיחת השיר", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayback() {
        if (preparing) return;
        if (player == null) {
            if (currentIndex >= 0) playSong(currentIndex);
            else if (!songs.isEmpty()) playSong(0);
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                setPlayIcon(false);
            } else {
                player.start();
                setPlayIcon(true);
            }
        } catch (IllegalStateException ignored) {
            if (currentIndex >= 0) playSong(currentIndex);
        }
    }

    private void nextSong() {
        if (songs.isEmpty()) return;
        int next = currentIndex < 0 ? 0 : (currentIndex + 1) % songs.size();
        playSong(next);
    }

    private void previousSong() {
        if (songs.isEmpty()) return;
        if (player != null && !preparing) {
            try {
                if (player.getCurrentPosition() > 3000) {
                    player.seekTo(0);
                    return;
                }
            } catch (IllegalStateException ignored) { }
        }
        int previous = currentIndex <= 0 ? songs.size() - 1 : currentIndex - 1;
        playSong(previous);
    }

    private void updateProgress() {
        if (player == null || preparing) return;
        try {
            int duration = player.getDuration();
            int position = player.getCurrentPosition();
            if (duration > 0) {
                progressBar.setProgress((int) ((position * 1000L) / duration));
                timeLabel.setText(formatTime(position) + "  /  " + formatTime(duration));
            }
        } catch (IllegalStateException ignored) { }
    }

    private void updateRowHighlights() {
        for (int i = 0; i < songRows.size(); i++) {
            View row = songRows.get(i);
            if (!row.hasFocus()) {
                int color = i == currentIndex ? Color.rgb(29, 63, 70) : Color.rgb(19, 29, 47);
                row.setBackground(focusDrawable(color, Color.rgb(41, 57, 76), 13));
            }
        }
    }

    private void setPlayIcon(boolean playing) {
        if (playIcon != null) {
            playIcon.setText(playing ? "Ⅱ" : "▶");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.setOnPreparedListener(null);
                player.setOnCompletionListener(null);
                player.setOnErrorListener(null);
                player.stop();
            } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        preparing = false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlayback();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (player == null || !isPlaying()) togglePlayback();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (isPlaying()) togglePlayback();
                return true;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                nextSong();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                previousSong();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private boolean isPlaying() {
        try { return player != null && player.isPlaying(); }
        catch (IllegalStateException e) { return false; }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        releasePlayer();
        super.onDestroy();
    }

    private TextView makeText(String text, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        return view;
    }

    private GradientDrawable roundDrawable(int color, int strokeColor, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private GradientDrawable focusDrawable(int color, int strokeColor, int radius) {
        return roundDrawable(color, strokeColor, 1, radius);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().length() == 0 || "<unknown>".equalsIgnoreCase(value)) return fallback;
        return value.trim();
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, remainder);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
