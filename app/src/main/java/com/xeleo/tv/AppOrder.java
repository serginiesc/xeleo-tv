package com.xeleo.tv;

import android.content.Context;
import android.text.TextUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The order of the app row, as rearranged on the TV (hold OK on a card). Apps not in it keep their place after. */
final class AppOrder {

    private static final String PREFS = "app_order";
    private static final String KEY = "order";

    private AppOrder() {}

    static void save(Context context, List<String> packages) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, TextUtils.join(",", packages)).apply();
    }

    /** Sorts apps (anything with a package name) by the saved order; a stable sort, so new apps keep theirs. */
    static <T> void apply(Context context, List<T> apps, java.util.function.Function<T, String> pkg) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (saved.isEmpty()) return;
        List<String> order = Arrays.asList(TextUtils.split(saved, ","));
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        apps.sort((a, b) -> Integer.compare(rank.getOrDefault(pkg.apply(a), Integer.MAX_VALUE),
                rank.getOrDefault(pkg.apply(b), Integer.MAX_VALUE)));
    }
}
