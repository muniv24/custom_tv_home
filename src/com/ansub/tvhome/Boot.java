package com.ansub.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Starts the volume box when the TV starts and after an update. With Detox on the home screen
// starts it anyway, but with Detox off the TV's own home screen opens instead.
public class Boot extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        VolumeOverlay.apply(c);
    }
}
