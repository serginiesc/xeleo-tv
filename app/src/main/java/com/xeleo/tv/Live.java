package com.xeleo.tv;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * En vivo: Pluto TV's free channels and what each shows now, from the guide Pluto's own web player reads
 * (boot.pluto.tv for a session, service-channels.clusters.pluto.tv for channels and times). No key; not an
 * official, documented API, so it may change: then the tab says the guide didn't load and nothing else breaks.
 * Channels open in the Pluto TV app; XELEO only shows the guide. Off by default (Quick settings > En vivo).
 */
final class Live {

    static final String PLUTO = "tv.pluto.android";

    static final class Program {
        String title = "", episode = "", description = "", image = "";
        long start, stop;
        boolean live;
    }

    static final class Channel {
        String id = "", slug = "", name = "", logo = "", image = "";
        final List<Program> programs = new ArrayList<>();

        /** What is on at `t`, or null. */
        Program at(long t) {
            for (Program p : programs) if (p.start <= t && t < p.stop) return p;
            return null;
        }

        Program after(Program p) {
            int i = programs.indexOf(p);
            return i >= 0 && i + 1 < programs.size() ? programs.get(i + 1) : null;
        }
    }

    static final class Row {
        final String title;
        final List<Channel> channels = new ArrayList<>();

        Row(String title) {
            this.title = title;
        }
    }

    interface Callback {
        /** rows is empty and error set when the guide couldn't be loaded. */
        void onGuide(List<Row> rows, String error);
    }

    /** Shown first, as "Destacados", when the region has them: Mexican free-to-air TV, news and a few favourites. */
    private static final String[] PICKS = {
            "646ccc7a2858cb0008127367",   // Azteca UNO -1 hora
            "64e35aff6b1fdb0008ea8441",   // Imagen TV+
            "652e922db4b047000825f975",   // Milenio Televisión
            "646ccd3681844c000974d6f4",   // ADN Noticias
            "6a68cb5a116cd6d17dba0b95",   // NFL Channel
            "5e3ddbd27091820009f86dd9",   // MasterChef México
            "609ae5cd48d3200007b0a98e",   // Comedy Central South Park
            "65cf60d166eec80008724e6f",   // Azteca Deportes Premium
    };
    /** Pluto's categories shown as rows, in this order (as Pluto TV Latin America names them). */
    private static final String[] CATEGORIES = {"Noticias", "Deportes", "Películas", "Series", "Novelas", "Reality",
            "Kids", "Música"};
    private static final int PER_ROW = 10;
    /** The guide is fetched again after this long; programmes in between are worked out from what we have. */
    private static final long FRESH_MS = 30 * 60_000L;
    private static final long HOURS_MS = 4 * 3600_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static List<Row> cache;
    private static long cachedAt;
    private static String token;

    private Live() {}

    // ---------------------------------------------------------------- settings

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("live", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("on", on).apply();
    }

    private static String clientId(Context c) {
        String id = prefs(c).getString("client", "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            prefs(c).edit().putString("client", id).apply();
        }
        return id;
    }

    // ---------------------------------------------------------------- guide

    /** The rows for the tab; calls back on the main thread, from the cache when it is fresh enough. */
    static void load(Context context, Callback cb) {
        if (cache != null && System.currentTimeMillis() - cachedAt < FRESH_MS) {
            List<Row> rows = cache;
            MAIN.post(() -> cb.onGuide(rows, null));
            return;
        }
        String client = clientId(context.getApplicationContext());
        new Thread(() -> {
            List<Row> rows;
            String error = null;
            try {
                rows = fetch(client);
                cache = rows;
                cachedAt = System.currentTimeMillis();
            } catch (Exception e) {
                token = null;   // a stale session is the usual cause; the next try starts a new one
                rows = cache != null ? cache : new ArrayList<>();
                if (rows.isEmpty()) error = "No se pudo cargar la guía de Pluto TV. Revisa la conexión.";
            }
            List<Row> result = rows;
            String err = error;
            MAIN.post(() -> cb.onGuide(result, err));
        }).start();
    }

    private static List<Row> fetch(String client) throws Exception {
        if (token == null) {
            JSONObject boot = get("https://boot.pluto.tv/v4/start?appName=web&appVersion=9.10.0&deviceVersion=120.0.0"
                    + "&deviceModel=web&deviceMake=chrome&deviceType=web&clientModelNumber=1.0.0&serverSideAds=false"
                    + "&drmCapabilities=widevine%3AL3&clientID=" + client, null);
            token = boot.getString("sessionToken");
        }
        String api = "https://service-channels.clusters.pluto.tv/v2/guide/";
        Map<String, Channel> byId = new HashMap<>();
        JSONArray chans = get(api + "channels?channelIds=&offset=0&limit=1000&sort=number%3Aasc", token)
                .getJSONArray("data");
        for (int i = 0; i < chans.length(); i++) {
            JSONObject o = chans.getJSONObject(i);
            Channel c = new Channel();
            c.id = o.optString("id");
            c.slug = o.optString("slug");
            c.name = o.optString("name");
            JSONArray imgs = o.optJSONArray("images");
            for (int k = 0; imgs != null && k < imgs.length(); k++) {
                JSONObject im = imgs.getJSONObject(k);
                String type = im.optString("type"), url = im.optString("url");
                if (type.equals("colorLogoPNG")) c.logo = sized(url, 182, 52, "png");
                if (type.equals("featuredImage")) c.image = sized(url, 960, 540, "jpg");
            }
            byId.put(c.id, c);
        }

        List<Row> rows = new ArrayList<>();
        Row picks = new Row("Destacados");
        for (String id : PICKS) if (byId.containsKey(id)) picks.channels.add(byId.get(id));
        if (!picks.channels.isEmpty()) rows.add(picks);
        Map<String, JSONArray> cats = new LinkedHashMap<>();
        JSONArray catList = get(api + "categories", token).getJSONArray("data");
        for (int i = 0; i < catList.length(); i++) {
            JSONObject o = catList.getJSONObject(i);
            cats.put(o.optString("name"), o.optJSONArray("channelIDs"));
        }
        for (String name : CATEGORIES) {
            JSONArray ids = cats.get(name);
            if (ids == null) continue;
            Row r = new Row(name);
            for (int i = 0; i < ids.length() && r.channels.size() < PER_ROW; i++) {
                Channel c = byId.get(ids.getString(i));
                if (c != null) r.channels.add(c);
            }
            if (!r.channels.isEmpty()) rows.add(r);
        }

        // What's on, four hours from now, for every channel in a row; in batches so the URL stays short.
        Map<String, Channel> shown = new LinkedHashMap<>();
        for (Row r : rows) for (Channel c : r.channels) shown.put(c.id, c);
        List<String> ids = new ArrayList<>(shown.keySet());
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String start = URLEncoder.encode(iso.format(new Date(System.currentTimeMillis() - 30 * 60_000L)), "UTF-8");
        for (int from = 0; from < ids.size(); from += 30) {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + 30));
            JSONArray data = get(api + "timelines?start=" + start + "&duration=" + (HOURS_MS / 60_000)
                    + "&channelIds=" + TextUtils.join(",", batch), token).getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject o = data.getJSONObject(i);
                Channel c = shown.get(o.optString("channelId"));
                JSONArray tl = o.optJSONArray("timelines");
                for (int k = 0; c != null && tl != null && k < tl.length(); k++) c.programs.add(program(tl.getJSONObject(k), iso));
            }
        }
        return rows;
    }

    private static Program program(JSONObject t, SimpleDateFormat iso) throws Exception {
        Program p = new Program();
        p.title = t.optString("title");
        p.start = iso.parse(t.getString("start")).getTime();
        p.stop = iso.parse(t.getString("stop")).getTime();
        JSONObject ep = t.optJSONObject("episode");
        if (ep != null) {
            p.episode = ep.optString("name");
            p.description = ep.optString("description");
            p.live = ep.optBoolean("liveBroadcast");
            // The series' picture first: many episodes only carry Pluto's generic one.
            JSONObject series = ep.optJSONObject("series");
            String img = series != null ? path(series.optJSONObject("featuredImage")) : "";
            if (img.isEmpty()) img = path(ep.optJSONObject("featuredImage"));
            if (!img.isEmpty()) p.image = sized(img, 960, 540, "jpg");
        }
        // Placeholders some channels put in their guide.
        if (p.episode.equalsIgnoreCase(p.title) || p.episode.equalsIgnoreCase("TBD")) p.episode = "";
        if (p.description.equalsIgnoreCase("TBD") || p.description.equalsIgnoreCase(p.title)) p.description = "";
        return p;
    }

    private static String path(JSONObject o) {
        return o == null ? "" : o.optString("path");
    }

    /** Pluto's image server resizes on request, so a 1600 px picture doesn't land in memory. */
    private static String sized(String url, int w, int h, String fm) {
        if (url == null || url.isEmpty()) return "";
        int q = url.indexOf('?');
        return (q < 0 ? url : url.substring(0, q)) + "?fit=fill&fm=" + fm + "&w=" + w + "&h=" + h + "&q=75";
    }

    private static JSONObject get(String url, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }

    // ---------------------------------------------------------------- opening a channel

    static boolean plutoInstalled(Context c) {
        return c.getPackageManager().getLeanbackLaunchIntentForPackage(PLUTO) != null
                || c.getPackageManager().getLaunchIntentForPackage(PLUTO) != null;
    }

    /** The channel in the Pluto TV app; its home if the link isn't understood; the Play Store if it isn't installed. */
    static void open(Context c, Channel ch) {
        Intent[] tries = {
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/latam/live-tv/" + ch.slug)).setPackage(PLUTO),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/live-tv/" + ch.slug)).setPackage(PLUTO),
                c.getPackageManager().getLeanbackLaunchIntentForPackage(PLUTO),
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PLUTO)),
        };
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Recents.record(c, PLUTO);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }
}
