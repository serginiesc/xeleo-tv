package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The TV's settings in one JSON file, to carry them over a reinstall or to another TV: Personalizar (names and
 * city), the apps on Home, Sports (leagues, team, alerts) and En vivo (on, my channels, hidden ones). install.sh
 * saves it before installing and puts it back on a fresh install; over adb, ControlReceiver's BACKUP and
 * RESTORE do the same. Android's own backup (allowBackup) keeps the same files too.
 */
final class Backup {

    /** The settings files that make up the backup; recents and caches stay out. */
    static final String[] FILES = {"profile", "app_order", "sports", "live", "reminders"};
    private static final Set<String> SKIP = new HashSet<>(Collections.singletonList("client"));
    static final String NAME = "xeleo-respaldo.json";

    private Backup() {}

    /** Where BACKUP writes and RESTORE reads: the app's folder in shared storage, reachable over adb. */
    static File file(Context c) {
        File dir = c.getExternalFilesDir(null);
        return new File(dir != null ? dir : c.getFilesDir(), NAME);
    }

    static JSONObject export(Context c) throws Exception {
        JSONObject all = new JSONObject();
        all.put("version", 1);
        JSONObject files = new JSONObject();
        for (String name : FILES) {
            JSONObject prefs = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                if (SKIP.contains(e.getKey())) continue;
                Object v = e.getValue();
                JSONObject item = new JSONObject();
                if (v instanceof Set) {
                    item.put("t", "set");
                    item.put("v", new org.json.JSONArray((Set<?>) v));
                } else {
                    item.put("t", v instanceof Boolean ? "bool" : v instanceof Integer ? "int" : v instanceof Long ? "long"
                            : v instanceof Float ? "float" : "string");
                    item.put("v", v);
                }
                prefs.put(e.getKey(), item);
            }
            files.put(name, prefs);
        }
        all.put("prefs", files);
        return all;
    }

    /** Writes the backup file; returns its path. */
    static String save(Context c) throws Exception {
        File f = file(c);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(export(c).toString(2).getBytes(StandardCharsets.UTF_8));
        }
        return f.getAbsolutePath();
    }

    /** Puts back the settings from the backup file, replacing the current ones; false if there is none. */
    static boolean restore(Context c) throws Exception {
        File f = file(c);
        if (!f.exists()) return false;
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0;
            for (int n; off < data.length && (n = in.read(data, off, data.length - off)) > 0; ) off += n;
        }
        JSONObject files = new JSONObject(new String(data, StandardCharsets.UTF_8)).getJSONObject("prefs");
        for (String name : FILES) {
            JSONObject prefs = files.optJSONObject(name);
            if (prefs == null) continue;
            SharedPreferences.Editor e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
                String key = it.next();
                if (SKIP.contains(key)) continue;
                JSONObject item = prefs.getJSONObject(key);
                switch (item.optString("t")) {
                    case "bool": e.putBoolean(key, item.getBoolean("v")); break;
                    case "int": e.putInt(key, item.getInt("v")); break;
                    case "long": e.putLong(key, item.getLong("v")); break;
                    case "float": e.putFloat(key, (float) item.getDouble("v")); break;
                    case "set": {
                        Set<String> s = new HashSet<>();
                        org.json.JSONArray a = item.getJSONArray("v");
                        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
                        e.putStringSet(key, s);
                        break;
                    }
                    default: e.putString(key, item.optString("v"));
                }
            }
            e.commit();
        }
        Weather.reset();
        Sports.forget();
        return true;
    }
}
