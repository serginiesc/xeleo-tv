package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmes to remember, set from En vivo (hold OK on a channel → Recordar). XeleoService looks every 30 s and,
 * five minutes before one starts, shows a card over whatever is on screen. Kept in the "reminders" settings, so
 * they survive a restart and go into the backup.
 */
final class Reminders {

    static final long AHEAD_MS = 5 * 60_000L;
    /** A reminder that comes due more than this late (the TV was off) is dropped instead of shown. */
    private static final long STALE_MS = 10 * 60_000L;

    static final class Item {
        String key = "", title = "", channel = "", logo = "";
        long start;
    }

    private Reminders() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    static synchronized List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.key = o.optString("key");
                it.title = o.optString("title");
                it.channel = o.optString("channel");
                it.logo = o.optString("logo");
                it.start = o.optLong("start");
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static synchronized void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                a.put(new JSONObject().put("key", it.key).put("title", it.title).put("channel", it.channel)
                        .put("logo", it.logo).put("start", it.start));
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("list", a.toString()).apply();
    }

    /** key tells reminders apart: the channel and the programme's start. */
    static String key(String channelId, long start) {
        return channelId + "@" + start;
    }

    static boolean has(Context c, String key) {
        for (Item it : all(c)) if (it.key.equals(key)) return true;
        return false;
    }

    static void add(Context c, String key, String title, String channel, String logo, long start) {
        List<Item> items = all(c);
        items.removeIf(it -> it.key.equals(key));
        Item it = new Item();
        it.key = key;
        it.title = title;
        it.channel = channel;
        it.logo = logo;
        it.start = start;
        items.add(it);
        save(c, items);
    }

    static void remove(Context c, String key) {
        List<Item> items = all(c);
        items.removeIf(it -> it.key.equals(key));
        save(c, items);
    }

    /** The reminders whose moment has come, taken off the list (late ones are just dropped). */
    static List<Item> due(Context c, long now) {
        List<Item> items = all(c), due = new ArrayList<>(), keep = new ArrayList<>();
        for (Item it : items) {
            long at = it.start - AHEAD_MS;
            if (now < at) keep.add(it);
            else if (now - at < STALE_MS) due.add(it);
        }
        if (keep.size() != items.size()) save(c, keep);
        return due;
    }
}
