package com.ansub.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

// Keeps Dolby going to the soundbar on Xiaomi TVs. With Digital output set to Passthrough, the TV's
// audio chip still falls back to PCM whenever a new video starts, and only goes back to Dolby when the
// Settings app sends it the output mode again (opening the Sound panel does that). Settings also sends
// it when it hears the soundbar was plugged in, so a few seconds after any video starts this tells it
// so. Turn off with --es dolby off. Does nothing on TVs without Xiaomi's sound receiver.
final class DolbyKeeper extends AudioManager.AudioPlaybackCallback {
    private static final ComponentName RECEIVER =
            new ComponentName("com.android.tv.settings", "com.android.tv.settings.mitv.SoundDeviceReceiver");
    private static final long[] NUDGE_AT_MS = {3000, 10000};

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable nudge = this::nudge;
    private List<AudioPlaybackConfiguration> playing = new ArrayList<>();

    private DolbyKeeper(Context c) { context = c; }

    // Starts watching for playback. Returns null where it doesn't apply.
    static DolbyKeeper start(Context c, AudioManager audio) {
        if (Build.VERSION.SDK_INT < 26) return null;
        Intent probe = new Intent("android.media.action.HDMI_ARC_PLUG").setComponent(RECEIVER);
        if (c.getPackageManager().queryBroadcastReceivers(probe, 0).isEmpty()) return null;
        DolbyKeeper k = new DolbyKeeper(c);
        audio.registerAudioPlaybackCallback(k, k.main);
        return k;
    }

    void stop(AudioManager audio) {
        audio.unregisterAudioPlaybackCallback(this);
        main.removeCallbacks(nudge);
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, 0).getBoolean("dolby", true);
    }

    @Override public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> now) {
        boolean started = false;
        for (AudioPlaybackConfiguration p : now) {
            int usage = p.getAudioAttributes().getUsage();
            if (usage == AudioAttributes.USAGE_MEDIA && !playing.contains(p)) started = true;
        }
        playing = new ArrayList<>(now);
        if (!started || !enabled(context)) return;
        main.removeCallbacks(nudge);
        for (long ms : NUDGE_AT_MS) main.postDelayed(nudge, ms);
    }

    // "The soundbar is connected": Settings answers by sending the saved output mode to the chip again.
    private void nudge() {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (VolumeOverlay.onTvSpeakers(audio)) return; // only for the soundbar
        Intent i = new Intent("android.media.action.HDMI_ARC_PLUG").setComponent(RECEIVER)
                .putExtra("android.media.extra.AUDIO_PLUG_STATE", 1);
        try { context.sendBroadcast(i); } catch (Exception ignored) {}
    }
}
