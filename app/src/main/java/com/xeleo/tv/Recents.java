package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The apps used most recently, newest first. XeleoService records every app that comes to the screen, however
 * it was opened (Home, the remote's app buttons, Gemini); Home records its own launches too.
 */
final class Recents {

    private static final String PREFS = "recents";
    private static final String KEY = "opened";

    private Recents() {}

    static void record(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        List<String> list = new ArrayList<>(Arrays.asList(TextUtils.split(prefs.getString(KEY, ""), ",")));
        list.remove(pkg);
        list.add(0, pkg);
        while (list.size() > 10) list.remove(list.size() - 1);
        prefs.edit().putString(KEY, TextUtils.join(",", list)).apply();
    }

    /** Up to max packages from launchable, newest first. */
    static List<String> list(Context context, Set<String> launchable, int max) {
        List<String> out = new ArrayList<>();
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        for (String pkg : TextUtils.split(saved, ",")) {
            if (out.size() == max) break;
            if (launchable.contains(pkg)) out.add(pkg);
        }
        return out;
    }
}
