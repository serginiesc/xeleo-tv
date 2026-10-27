package com.xeleo.tv;

import android.app.ActivityManager;
import android.content.Context;

/**
 * Light mode for boxes with little memory (the Chromecast HD has 1.5 GB, the onn 2 GB): the sky and the season
 * themes draw less often and with fewer things, the focus ring stops after a couple of turns, and the image caches
 * are smaller, so more memory is left for the app you open. Decided once, from what Android says of the device.
 */
final class Perf {

    /** Below this much RAM a box counts as small. */
    private static final long SMALL = 1700L * 1024 * 1024;
    private static Boolean light;

    private Perf() {}

    static boolean light(Context c) {
        if (light == null) {
            // ControlReceiver LIGHT_MODE can force it on or off, to try it on any box.
            String forced = c.getSharedPreferences("profile", Context.MODE_PRIVATE).getString("light", "auto");
            if (!forced.equals("auto")) return light = forced.equals("on");
            ActivityManager am = c.getSystemService(ActivityManager.class);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            light = am.isLowRamDevice() || mi.totalMem < SMALL;
        }
        return light;
    }

    /** How long between frames of the sky and the themes: 30 a second, or 20 in light mode. */
    static long frameMs(Context c) {
        return light(c) ? 50 : 33;
    }
}
