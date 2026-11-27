package com.yedidyabc1000.nagan44;

import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.EnvironmentalReverb;
import android.os.Handler;
import android.util.Log;

/**
 * Experimental Android 4.4-compatible approximation of distant-room listening.
 *
 * Android's public MediaPlayer effect chain offers an Equalizer and auxiliary
 * EnvironmentalReverb, but does not expose a stereo mid/side matrix or a
 * general-purpose compressor. Those two requested controls are therefore not
 * simulated. EQ bands and reverb send are ramped to avoid abrupt parameter jumps.
 */
final class RemoteListeningEffect {
    private static final String TAG = "NaganRemoteAudio";
    private static final long UPDATE_MS = 35L;

    private final Handler handler = new Handler();
    private MediaPlayer player;
    private Equalizer equalizer;
    private EnvironmentalReverb reverb;
    private boolean enabled;
    private int distancePercent = 55;
    private int preset;
    private float currentSend;
    private float targetSend;
    private float[] currentBandLevels = new float[0];
    private short[] targetBandLevels = new short[0];
    private short minimumBandLevel = -1500;
    private short maximumBandLevel = 1500;

    private final Runnable smoother = new Runnable() {
        @Override public void run() {
            boolean pending = false;
            if (reverb != null) {
                currentSend += (targetSend - currentSend) * 0.34f;
                if (Math.abs(targetSend - currentSend) < 0.008f) currentSend = targetSend;
                try {
                    if (player != null) player.setAuxEffectSendLevel(currentSend);
                } catch (RuntimeException error) {
                    Log.d(TAG, "Could not update reverb send", error);
                }
                if (Math.abs(targetSend - currentSend) >= 0.008f) pending = true;
            }
            if (equalizer != null) {
                for (int i = 0; i < targetBandLevels.length; i++) {
                    float target = targetBandLevels[i];
                    currentBandLevels[i] += (target - currentBandLevels[i]) * 0.34f;
                    if (Math.abs(target - currentBandLevels[i]) < 7f) currentBandLevels[i] = target;
                    else pending = true;
                    try {
                        equalizer.setBandLevel((short)i, (short)Math.round(currentBandLevels[i]));
                    } catch (RuntimeException error) {
                        Log.d(TAG, "Could not update EQ band", error);
                    }
                }
            }
            if (pending) handler.postDelayed(this, UPDATE_MS);
        }
    };

    void attach(MediaPlayer targetPlayer) {
        release();
        player = targetPlayer;
        if (targetPlayer == null) return;
        int sessionId;
        try {
            sessionId = targetPlayer.getAudioSessionId();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not get audio session ID", error);
            return;
        }
        if (sessionId <= 0) return;

        try {
            equalizer = new Equalizer(0, sessionId);
            short[] range = equalizer.getBandLevelRange();
            if (range != null && range.length >= 2) {
                minimumBandLevel = range[0];
                maximumBandLevel = range[1];
            }
            int bands = equalizer.getNumberOfBands();
            currentBandLevels = new float[bands];
            targetBandLevels = new short[bands];
            for (short band = 0; band < bands; band++) {
                currentBandLevels[band] = equalizer.getBandLevel(band);
                targetBandLevels[band] = equalizer.getBandLevel(band);
            }
            equalizer.setEnabled(true);
        } catch (RuntimeException error) {
            Log.w(TAG, "Equalizer unavailable on this device", error);
            releaseEqualizer();
        }

        try {
            reverb = new EnvironmentalReverb(0, sessionId);
            configureReverb();
            reverb.setEnabled(true);
            targetPlayer.attachAuxEffect(reverb.getId());
            targetPlayer.setAuxEffectSendLevel(0f);
            currentSend = 0f;
        } catch (RuntimeException error) {
            Log.w(TAG, "Environmental reverb unavailable on this device", error);
            releaseReverb();
        }
        updateTargets();
    }

    void setSettings(boolean isEnabled, int distance, int selectedPreset) {
        enabled = isEnabled;
        distancePercent = Math.max(0, Math.min(100, distance));
        preset = Math.max(0, Math.min(2, selectedPreset));
        if (reverb != null) configureReverb();
        updateTargets();
    }

    private void updateTargets() {
        float amount = enabled ? distancePercent / 100f : 0f;
        if (equalizer != null) {
            int bands = equalizer.getNumberOfBands();
            if (targetBandLevels.length != bands) {
                targetBandLevels = new short[bands];
                currentBandLevels = new float[bands];
            }
            for (short band = 0; band < bands; band++) {
                int centerHz = Math.max(1, equalizer.getCenterFreq(band) / 1000);
                float attenuation = 0f;
                if (centerHz < 130) attenuation = 2.5f;
                else if (centerHz < 500) attenuation = 0.6f;
                else if (centerHz >= 2500) {
                    float high = Math.min(1f, (centerHz - 2500f) / 8500f);
                    attenuation = 1.3f + high * 5.8f;
                }
                float presetShape = preset == 1 && centerHz > 2500 ? 0.5f
                        : (preset == 2 && centerHz < 180 ? 0.3f : 0f);
                int level = Math.round(-(attenuation + presetShape) * amount * 100f);
                level = Math.max(minimumBandLevel, Math.min(maximumBandLevel, level));
                targetBandLevels[band] = (short)level;
            }
        }
        float send = preset == 1 ? 0.12f + 0.34f * amount
                : (preset == 2 ? 0.06f + 0.22f * amount : 0.08f + 0.27f * amount);
        targetSend = enabled ? send : 0f;
        handler.removeCallbacks(smoother);
        handler.post(smoother);
    }

    private void configureReverb() {
        if (reverb == null) return;
        try {
            if (preset == 1) {
                reverb.setRoomLevel((short)-900);
                reverb.setRoomHFLevel((short)-1100);
                reverb.setDecayTime(650 + distancePercent * 13);
                reverb.setDecayHFRatio((short)650);
                reverb.setReflectionsLevel((short)-1250);
                reverb.setReflectionsDelay(18);
                reverb.setReverbLevel((short)-950);
                reverb.setReverbDelay(38);
                reverb.setDiffusion((short)900);
                reverb.setDensity((short)900);
            } else if (preset == 2) {
                reverb.setRoomLevel((short)-2200);
                reverb.setRoomHFLevel((short)-500);
                reverb.setDecayTime(280 + distancePercent * 6);
                reverb.setDecayHFRatio((short)800);
                reverb.setReflectionsLevel((short)-2600);
                reverb.setReflectionsDelay(8);
                reverb.setReverbLevel((short)-2100);
                reverb.setReverbDelay(18);
                reverb.setDiffusion((short)650);
                reverb.setDensity((short)700);
            } else {
                reverb.setRoomLevel((short)-1450);
                reverb.setRoomHFLevel((short)-700);
                reverb.setDecayTime(430 + distancePercent * 9);
                reverb.setDecayHFRatio((short)720);
                reverb.setReflectionsLevel((short)-1800);
                reverb.setReflectionsDelay(12);
                reverb.setReverbLevel((short)-1450);
                reverb.setReverbDelay(26);
                reverb.setDiffusion((short)800);
                reverb.setDensity((short)850);
            }
        } catch (RuntimeException error) {
            Log.d(TAG, "Some reverb parameters unsupported", error);
        }
    }

    void release() {
        handler.removeCallbacks(smoother);
        if (player != null) {
            try { player.setAuxEffectSendLevel(0f); } catch (RuntimeException ignored) { }
        }
        releaseReverb();
        releaseEqualizer();
        player = null;
        currentSend = 0f;
        targetSend = 0f;
        currentBandLevels = new float[0];
        targetBandLevels = new short[0];
    }

    private void releaseEqualizer() {
        if (equalizer != null) {
            try { equalizer.setEnabled(false); } catch (RuntimeException ignored) { }
            try { equalizer.release(); } catch (RuntimeException ignored) { }
            equalizer = null;
        }
    }

    private void releaseReverb() {
        if (reverb != null) {
            try { reverb.setEnabled(false); } catch (RuntimeException ignored) { }
            try { reverb.release(); } catch (RuntimeException ignored) { }
            reverb = null;
        }
    }
}
