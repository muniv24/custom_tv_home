package com.ansub.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

// Shows the volume as a percentage over whatever is playing. Some TVs (Xiaomi with an eARC soundbar)
// hide their own volume bar when a soundbar takes over, but the soundbar still reports its level over
// HDMI-CEC and Android keeps it as the music volume, so that is what this shows.
// Needs "draw over other apps": adb shell appops set com.ansub.tvhome SYSTEM_ALERT_WINDOW allow
// The service also runs DolbyKeeper, so it stays running when the box is switched off.
public class VolumeOverlay extends Service {
    private static final String VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION";
    private static final String MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION";
    private static final long SHOW_MS = 2500;

    private AudioManager audio;
    private WindowManager wm;
    private LinearLayout box;
    private TextView label;
    private View fill;
    private float scale = 1f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = () -> { if (box != null) box.setVisibility(View.GONE); };
    private DolbyKeeper dolby;

    // On unless switched off with the speaker icon on the home screen or --es volume off.
    static boolean enabled(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, 0).getBoolean("volume", true);
    }

    // Starts the service. It always runs, for DolbyKeeper; the volume setting only shows or hides the box.
    static void apply(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        try { c.startForegroundService(new Intent(c, VolumeOverlay.class)); } catch (Exception ignored) {}
    }

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) != AudioManager.STREAM_MUSIC) return;
            if (box == null || !enabled(c) || onTvSpeakers(audio)) return; // TV speakers: the TV shows its own bar
            if (VOLUME_CHANGED.equals(i.getAction())
                    && i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                    == i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -2)) return;
            changed();
        }
    };

    // Holding the volume button runs Android's number far ahead of the soundbar, which only takes some
    // of the repeated presses and then reports where it really is. So while presses come fast, keep
    // the number moving and stay up until the soundbar has answered, then show its real level.
    private static final long BURST_MS = 400, SETTLE_MS = 900;
    private long lastChange;
    private final Runnable settle = this::show;

    // True when music plays only on the TV's own speakers (Android's speaker device, 0x2). Asks
    // AudioManager's hidden getDevicesForStream; if a TV doesn't allow it, the box shows as before.
    static boolean onTvSpeakers(AudioManager audio) {
        try {
            int devices = (int) AudioManager.class.getMethod("getDevicesForStream", int.class)
                    .invoke(audio, AudioManager.STREAM_MUSIC);
            return devices == 0x2;
        } catch (Exception e) {
            return false;
        }
    }

    private void changed() {
        long now = android.os.SystemClock.uptimeMillis();
        boolean burst = now - lastChange < BURST_MS;
        lastChange = now;
        main.removeCallbacks(settle);
        if (!burst) { show(); return; }
        render();
        main.removeCallbacks(hide);
        main.postDelayed(settle, SETTLE_MS);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("volume", "Volume", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, "volume")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle("Volume").build();
        startForeground(1, n);

        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        if (Settings.canDrawOverlays(this)) buildBox();
        dolby = DolbyKeeper.start(this, audio);

        IntentFilter f = new IntentFilter(VOLUME_CHANGED);
        f.addAction(MUTE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changed, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(changed, f);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    @Override public void onDestroy() {
        unregisterReceiver(changed);
        if (dolby != null) dolby.stop(audio);
        main.removeCallbacks(hide);
        main.removeCallbacks(settle);
        try { wm.removeView(box); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void buildBox() {
        Typeface tf = new File("/system/fonts/MILanPro_SemiBold.ttf").exists()
                ? Typeface.createFromFile("/system/fonts/MILanPro_SemiBold.ttf")
                : Typeface.create("sans-serif-medium", Typeface.NORMAL);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(32), px(22), px(32), px(26));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6202020);
        bg.setCornerRadius(px(24));
        box.setBackground(bg);

        label = new TextView(this);
        label.setTextColor(Color.WHITE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(34));
        label.setTypeface(tf);
        box.addView(label);

        // The bar: a dim track with a white fill sized to the volume.
        FrameLayout track = new FrameLayout(this);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0x40FFFFFF);
        tbg.setCornerRadius(px(4));
        track.setBackground(tbg);
        fill = new View(this);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setColor(Color.WHITE);
        fbg.setCornerRadius(px(4));
        fill.setBackground(fbg);
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(320), px(8));
        lp.topMargin = px(14);
        box.addView(track, lp);
        box.setVisibility(View.GONE);

        WindowManager.LayoutParams wp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        wp.gravity = Gravity.TOP | Gravity.END;
        wp.x = px(64);
        wp.y = px(64);
        wm.addView(box, wp);
    }

    private void show() {
        render();
        main.removeCallbacks(hide);
        main.postDelayed(hide, SHOW_MS);
    }

    // The music volume's max is the soundbar's own scale (100 on Xiaomi), so this is its level.
    private void render() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int pct = Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max);
        boolean muted = Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC);
        label.setText(muted ? "Volume  muted" : "Volume  " + pct + "%");
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fill.getLayoutParams();
        lp.width = muted ? 0 : px(320) * pct / 100;
        fill.setLayoutParams(lp);
        box.setVisibility(View.VISIBLE);
    }

    private int px(int v) {
        return Math.round(v * scale);
    }
}
