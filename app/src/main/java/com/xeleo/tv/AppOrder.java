package com.xeleo.tv;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which apps sit on Home and in what order. Home shows only these: hold OK in Todas las apps to add one or take
 * it off, hold OK on a Home card to move it (Down takes it off). Every app stays in Todas las apps.
 */
final class AppOrder {

    private static final String PREFS = "app_order";
    private static final String KEY = "order";
    private static final String HOME = "home";
    /** How many apps Home starts with, before anyone picks: the dock apps first, then the old saved order. */
    static final int HOME_DEFAULT = 6;

    private AppOrder() {}

    /** Sorts apps by the order saved before Home had its own list, so a TV that had one keeps it. */
    static <T> void apply(Context context, List<T> apps, java.util.function.Function<T, String> pkg) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (saved.isEmpty()) return;
        List<String> order = Arrays.asList(TextUtils.split(saved, ","));
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        apps.sort((a, b) -> Integer.compare(rank.getOrDefault(pkg.apply(a), Integer.MAX_VALUE),
                rank.getOrDefault(pkg.apply(b), Integer.MAX_VALUE)));
    }

    /** The packages on Home, in order; null until Home has been set up once. */
    static List<String> home(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(HOME, null);
        return saved == null ? null : new ArrayList<>(Arrays.asList(TextUtils.split(saved, ",")));
    }

    static void saveHome(Context context, List<String> packages) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(HOME, TextUtils.join(",", packages)).apply();
    }
}
