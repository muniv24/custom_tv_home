package com.ansub.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;

// The Detox switch on the home screen. On: the TV's own home screen is turned off, so Home opens
// this one. Off: the TV's own home screen comes back. The ad and tracking apps stay off either way.
// Runs through AdbShell, so it works from the remote with no computer.
final class Detox {
    static final String GOOGLE_HOME = "com.google.android.tvlauncher";
    // Kept off in both modes: the sponsored row, Ambient Mode ads, unused Google apps, Xiaomi tracking.
    private static final String[] APPS = {GOOGLE_HOME, "com.google.android.tvrecommendations",
            "com.google.android.backdrop", "com.google.android.videos", "com.google.android.youtube.tvmusic",
            "com.google.android.play.games", "com.google.android.feedback", "com.google.android.tv.bugreportsender",
            "com.miui.tv.analytics", "com.xiaomi.statistic", "com.xiaomi.mitv.handbook"};
    // Ambient Mode is off, and with no screensaver the TV goes to standby after 5 minutes,
    // so the screensaver is Android's plain one.
    private static final String PLAIN_SAVER = "com.android.dreams.basic/.Colors";
    // install.sh's undo list: undo.sh turns everything on it back on.
    private static final String UNDO = "/data/local/tmp/tvhome-disabled.txt";

    static boolean isOn(Context c) {
        try {
            int s = c.getPackageManager().getApplicationEnabledSetting(GOOGLE_HOME);
            return s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    || s == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (IllegalArgumentException notInstalled) {
            return true;
        }
    }

    // Blocking: call it off the main thread.
    static void set(Context c, boolean on) throws Exception {
        StringBuilder cmd = new StringBuilder();
        if (on) {
            for (String p : APPS) cmd.append("grep -qx ").append(p).append(' ').append(UNDO).append(" || echo ").append(p)
                    .append(" >> ").append(UNDO).append("; pm disable-user --user 0 ").append(p).append("; ");
            cmd.append("cmd package set-home-activity com.ansub.tvhome/.MainActivity; ");
        } else {
            cmd.append("pm enable ").append(GOOGLE_HOME).append("; ");
        }
        cmd.append("settings put secure screensaver_components ").append(PLAIN_SAVER).append("; ");
        cmd.append("input keyevent KEYCODE_HOME");
        AdbShell sh = AdbShell.connect(c, 60000);
        try { sh.run(cmd.toString()); } finally { sh.close(); }
    }
}
