package com.ansub.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;

// The Detox switch on the home screen. On: the TV's own home screen and its ad apps are turned off,
// so Home opens this one. Off: they come back, and Home opens the TV's own home screen again.
// Runs through AdbShell, so it works from the remote with no computer.
final class Detox {
    static final String GOOGLE_HOME = "com.google.android.tvlauncher";
    // Turned off with the home screen: the sponsored row, Ambient Mode ads and unused Google apps.
    private static final String[] APPS = {GOOGLE_HOME, "com.google.android.tvrecommendations",
            "com.google.android.backdrop", "com.google.android.videos", "com.google.android.youtube.tvmusic",
            "com.google.android.play.games", "com.google.android.feedback"};
    // Ambient Mode is one of the apps turned off, and with no screensaver the TV goes to standby
    // after 5 minutes. So Detox switches to Android's plain one, and Off puts Ambient Mode back.
    private static final String PLAIN_SAVER = "com.android.dreams.basic/.Colors";
    private static final String AMBIENT_SAVER = "com.google.android.backdrop/.Backdrop";
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
        for (String p : APPS) {
            if (on) cmd.append("grep -qx ").append(p).append(' ').append(UNDO).append(" || echo ").append(p)
                    .append(" >> ").append(UNDO).append("; pm disable-user --user 0 ").append(p).append("; ");
            else cmd.append("pm enable ").append(p).append("; ");
        }
        cmd.append("settings put secure screensaver_components ").append(on ? PLAIN_SAVER : AMBIENT_SAVER).append("; ");
        if (on) cmd.append("cmd package set-home-activity com.ansub.tvhome/.MainActivity; ");
        cmd.append("input keyevent KEYCODE_HOME");
        AdbShell sh = AdbShell.connect(c, 60000);
        try { sh.run(cmd.toString()); } finally { sh.close(); }
    }
}
