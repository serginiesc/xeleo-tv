package com.xeleo.tv;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;

/**
 * The remote's app buttons (Netflix, YouTube…). On a TCL its own launcher opens those apps, and with XELEO in its
 * place nothing does; each brand sends its own key codes, so instead of guessing them the person presses the button
 * once in Personalizar > Botones del control and picks the app (ButtonsActivity). XeleoService opens it after that.
 */
final class RemoteKeys {

    interface Listener {
        void pressed(int keyCode);
    }

    /** Set while ButtonsActivity waits for a button: the next one goes there instead of doing anything. */
    static volatile Listener learning;

    private RemoteKeys() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("remote_keys", Context.MODE_PRIVATE);
    }

    static String app(Context c, int keyCode) {
        return prefs(c).getString("k" + keyCode, null);
    }

    static void assign(Context c, int keyCode, String pkg) {
        prefs(c).edit().putString("k" + keyCode, pkg).apply();
    }

    static void remove(Context c, int keyCode) {
        prefs(c).edit().remove("k" + keyCode).apply();
    }

    /** Keys that move around, go back or change the volume: never taken for an app, or the TV couldn't be used. */
    static boolean reserved(int k) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_SETTINGS: case KeyEvent.KEYCODE_NOTIFICATION:
                return true;
            default:
                return false;
        }
    }

    /** Opens the app; false when it isn't installed any more. */
    static boolean open(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        try {
            c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static String name(int keyCode) {
        String s = KeyEvent.keyCodeToString(keyCode);
        return s.startsWith("KEYCODE_") ? s.substring(8).replace('_', ' ').toLowerCase() : "botón " + s;
    }
}
