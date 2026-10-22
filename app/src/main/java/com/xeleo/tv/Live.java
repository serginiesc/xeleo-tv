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
        /** ViX's callsign for a ViX channel (opened in the ViX app); empty for Pluto TV's. */
        String vix = "";
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

    /** Mis canales before anyone picks: Mexican free-to-air TV, news and a few favourites, when the region has them. */
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
    /** Pluto's categories first in the tab, in this order (as Pluto TV Latin America names them); the rest follow. */
    private static final String[] CATEGORIES = {"Noticias", "Deportes", "Películas", "Series", "Novelas", "Reality",
            "Competencia", "Comedia", "Entretenimiento", "Kids", "Música"};
    private static final int PER_ROW = 60;
    /** The guide is fetched again after this long; programmes in between are worked out from what we have. */
    private static final long FRESH_MS = 30 * 60_000L;
    /** Up to this old the cache still answers (programmes are worked out from the time, four hours ahead). */
    private static final long USABLE_MS = 3 * 3600_000L;
    private static final long HOURS_MS = 4 * 3600_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static List<Row> cache;
    private static long cachedAt;
    private static String token;
    private static boolean fetching;
    private static final List<Callback> waiting = new ArrayList<>();

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

    /** Mis canales, in order: the picked ones, else PICKS. */
    static List<String> favorites(Context c) {
        String saved = prefs(c).getString("fav", null);
        if (saved == null) return new ArrayList<>(java.util.Arrays.asList(PICKS));
        return new ArrayList<>(java.util.Arrays.asList(TextUtils.split(saved, ",")));
    }

    static void setFavorite(Context c, String id, boolean on) {
        List<String> fav = favorites(c);
        fav.remove(id);
        if (on) fav.add(id);
        prefs(c).edit().putString("fav", TextUtils.join(",", fav)).apply();
    }

    /** Channels hidden from every category (they stay under "Ocultos"). */
    static java.util.Set<String> hidden(Context c) {
        return new java.util.HashSet<>(prefs(c).getStringSet("hidden", java.util.Collections.emptySet()));
    }

    static void setHidden(Context c, String id, boolean on) {
        java.util.Set<String> h = hidden(c);
        if (on) h.add(id);
        else h.remove(id);
        prefs(c).edit().putStringSet("hidden", h).apply();
    }

    /** Every channel of the guide by id. */
    static Map<String, Channel> index(List<Row> rows) {
        Map<String, Channel> m = new LinkedHashMap<>();
        for (Row r : rows) for (Channel ch : r.channels) m.put(ch.id, ch);
        return m;
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

    /**
     * The rows for the tab, on the main thread. Home fetches them ahead (prefetch), so the tab usually gets them
     * at once from the cache; a cache older than FRESH_MS still answers at once and is renewed behind.
     */
    static void load(Context context, Callback cb) {
        long age = System.currentTimeMillis() - cachedAt;
        if (cache != null && age < USABLE_MS) {
            List<Row> rows = cache;
            if (cb != null) MAIN.post(() -> cb.onGuide(rows, null));
            if (age >= FRESH_MS) fetchBehind(context, null);
            return;
        }
        if (cache == null && !diskTried) {
            // After a restart, the copy on disk first (it also covers a TV without internet yet).
            diskTried = true;
            Context app = context.getApplicationContext();
            new Thread(() -> {
                List<Row> rows = readDisk(app);
                MAIN.post(() -> {
                    if (rows != null && cache == null) cache = rows;
                    else if (rows == null) cachedAt = 0;
                    load(app, cb);
                });
            }).start();
            return;
        }
        fetchBehind(context, cb);
    }

    /** Fetches the guide ahead of time, so the tab opens without waiting; Home calls it when it comes back. */
    static void prefetch(Context context) {
        if (enabled(context) && (cache == null || System.currentTimeMillis() - cachedAt >= FRESH_MS)) {
            load(context, null);
        }
    }

    // ---------------------------------------------------------------- the copy on disk

    private static boolean diskTried;
    /** Bumped when what the copy holds changes (2: ViX channels and the Deportes en vivo row). */
    private static final int DISK_FORMAT = 2;

    private static java.io.File diskFile(Context c) {
        return new java.io.File(c.getFilesDir(), "guia-pluto.json");
    }

    private static void writeDisk(Context c, List<Row> rows, long at) {
        try {
            JSONArray out = new JSONArray();
            for (Row r : rows) {
                JSONArray chans = new JSONArray();
                for (Channel ch : r.channels) {
                    JSONArray progs = new JSONArray();
                    for (Program p : ch.programs) {
                        progs.put(new JSONObject().put("t", p.title).put("e", p.episode).put("d", p.description)
                                .put("i", p.image).put("s", p.start).put("x", p.stop).put("l", p.live));
                    }
                    chans.put(new JSONObject().put("id", ch.id).put("slug", ch.slug).put("name", ch.name)
                            .put("logo", ch.logo).put("image", ch.image).put("v", ch.vix).put("p", progs));
                }
                out.put(new JSONObject().put("t", r.title).put("c", chans));
            }
            java.io.File tmp = new java.io.File(c.getFilesDir(), "guia-pluto.tmp");
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(tmp)) {
                f.write(new JSONObject().put("fmt", DISK_FORMAT).put("at", at).put("rows", out).toString().getBytes("UTF-8"));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(diskFile(c));
        } catch (Exception ignored) {
        }
    }

    /** The guide saved by the last fetch, if it is recent enough to still say what's on; sets cachedAt. */
    private static List<Row> readDisk(Context c) {
        java.io.File f = diskFile(c);
        if (!f.exists()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
            JSONObject all = new JSONObject(buf.toString("UTF-8"));
            if (all.optInt("fmt") != DISK_FORMAT) return null;   // saved by an older XELEO: fetch afresh
            long at = all.getLong("at");
            if (System.currentTimeMillis() - at >= USABLE_MS) return null;
            Map<String, Channel> seen = new HashMap<>();
            List<Row> rows = new ArrayList<>();
            JSONArray rs = all.getJSONArray("rows");
            for (int i = 0; i < rs.length(); i++) {
                JSONObject r = rs.getJSONObject(i);
                Row row = new Row(r.optString("t"));
                JSONArray chans = r.getJSONArray("c");
                for (int k = 0; k < chans.length(); k++) {
                    JSONObject o = chans.getJSONObject(k);
                    Channel ch = seen.get(o.optString("id"));
                    if (ch == null) {
                        ch = new Channel();
                        ch.id = o.optString("id");
                        ch.slug = o.optString("slug");
                        ch.name = o.optString("name");
                        ch.logo = o.optString("logo");
                        ch.image = o.optString("image");
                        ch.vix = o.optString("v");
                        JSONArray ps = o.getJSONArray("p");
                        for (int j = 0; j < ps.length(); j++) {
                            JSONObject q = ps.getJSONObject(j);
                            Program p = new Program();
                            p.title = q.optString("t");
                            p.episode = q.optString("e");
                            p.description = q.optString("d");
                            p.image = q.optString("i");
                            p.start = q.optLong("s");
                            p.stop = q.optLong("x");
                            p.live = q.optBoolean("l");
                            ch.programs.add(p);
                        }
                        seen.put(ch.id, ch);
                    }
                    row.channels.add(ch);
                }
                rows.add(row);
            }
            cachedAt = at;
            return rows;
        } catch (Exception e) {
            return null;
        }
    }

    /** One fetch at a time: callers that arrive meanwhile get its result. Main thread only. */
    private static void fetchBehind(Context context, Callback cb) {
        if (cb != null) waiting.add(cb);
        if (fetching) return;
        fetching = true;
        Context app = context.getApplicationContext();
        String client = clientId(app);
        new Thread(() -> {
            List<Row> rows;
            String error = null;
            try {
                rows = fetch(client);
                cache = rows;
                cachedAt = System.currentTimeMillis();
                writeDisk(app, rows, cachedAt);
                // The picture the tab opens with, so the preview isn't empty for a moment.
                Map<String, Channel> idx = index(rows);
                for (String id : favorites(app)) {
                    Channel first = idx.get(id);
                    if (first == null) continue;
                    Program p = first.at(System.currentTimeMillis());
                    Logos.warm(p != null && !p.image.isEmpty() ? p.image : first.image);
                    break;
                }
            } catch (Exception e) {
                token = null;   // a stale session is the usual cause; the next try starts a new one
                rows = cache != null ? cache : new ArrayList<>();
                if (rows.isEmpty()) error = "No se pudo cargar la guía de Pluto TV. Revisa la conexión.";
            }
            List<Row> result = rows;
            String err = error;
            MAIN.post(() -> {
                fetching = false;
                List<Callback> todo = new ArrayList<>(waiting);
                waiting.clear();
                for (Callback c : todo) c.onGuide(result, err);
            });
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

        // Every category, ours first in our order and then the rest as Pluto lists them.
        List<Row> rows = new ArrayList<>();
        Map<String, JSONArray> cats = new LinkedHashMap<>();
        JSONArray catList = get(api + "categories", token).getJSONArray("data");
        for (int i = 0; i < catList.length(); i++) {
            JSONObject o = catList.getJSONObject(i);
            cats.put(o.optString("name"), o.optJSONArray("channelIDs"));
        }
        List<String> order = new ArrayList<>();
        for (String name : CATEGORIES) if (cats.containsKey(name)) order.add(name);
        for (String name : cats.keySet()) if (!order.contains(name)) order.add(name);
        for (String name : order) {
            JSONArray ids = cats.get(name);
            Row r = new Row(name);
            for (int i = 0; ids != null && i < ids.length() && r.channels.size() < PER_ROW; i++) {
                Channel c = byId.get(ids.getString(i));
                if (c != null) r.channels.add(c);
            }
            if (!r.channels.isEmpty()) rows.add(r);
        }
        // Deportes en vivo: Pluto's sports with ViX Deportes first; then a ViX button with ViX's free channels.
        List<Channel> vix = vixChannels();
        int at = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).title.equals("Deportes")) continue;
            Row sports = new Row("Deportes en vivo");
            sports.channels.add(vix.get(0));
            sports.channels.addAll(rows.get(i).channels);
            rows.set(i, sports);
            at = i + 1;
        }
        Row vixRow = new Row("ViX");
        vixRow.channels.addAll(vix);
        rows.add(at, vixRow);
        // Channels in no category (rare) still count for Mis canales.
        Row rest = new Row("");
        for (String id : PICKS) {
            Channel c = byId.get(id);
            boolean inRow = false;
            for (Row r : rows) if (r.channels.contains(c)) inRow = true;
            if (c != null && !inRow) rest.channels.add(c);
        }
        if (!rest.channels.isEmpty()) rows.add(rest);

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

    /**
     * The channel in its app (Pluto TV or ViX); the app's home if the link isn't understood; the Play Store if it
     * isn't installed.
     */
    static void open(Context c, Channel ch) {
        boolean vix = !ch.vix.isEmpty();
        String pkg = vix ? VIX : PLUTO;
        Intent[] tries = vix ? new Intent[]{
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://vix.com/es-es/canales/channel-callsign-" + ch.vix)).setPackage(VIX),
                c.getPackageManager().getLeanbackLaunchIntentForPackage(VIX),
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + VIX)),
        } : new Intent[]{
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/latam/live-tv/" + ch.slug)).setPackage(PLUTO),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://pluto.tv/live-tv/" + ch.slug)).setPackage(PLUTO),
                c.getPackageManager().getLeanbackLaunchIntentForPackage(PLUTO),
                new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PLUTO)),
        };
        for (Intent i : tries) {
            if (i == null) continue;
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Recents.record(c, pkg);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- ViX

    static final String VIX = "com.univision.prendetv";

    /**
     * ViX's free channels: name, ViX's callsign (its link is vix.com/es-es/canales/channel-callsign-<callsign>,
     * which the ViX app opens on that channel) and its picture. ViX has no public guide, so they show without
     * programmes, and open in the ViX app.
     */
    private static final String[][] VIX_CHANNELS = {
            {"ViX Deportes 24/7", "Zona_TUDN_MX", "6aa2d624bcb5191e4fb4e0b0/a51fd7466992d218bbc8b88003a13a37"},
            {"N+ Foro", "ForoTV-LATAM", "66e2cd617099897d3c4e81a0/f65aaf96a012d7c891771d5f1f96fce4"},
            {"N+", "N_Plus_MEX", "63ea420d27b695a94b1d428b/3830196ffbfe732108455528f7103b6b"},
            {"N+ Univision 24/7", "Noticias-Univision-24-7", "697a4b24ad6b839cbd318b71/fb3b5fb29ebfce9b3e7ea6963d536c43"},
            {"Canal ViX", "Z224", "65007ff6e293209bb97af732/dd7d62a1cdd5e9fe401bb5d516652c11"},
            {"La Rosa de Guadalupe", "Z005", "61b75fb008f1b309bf36f659/bb3fa20e860e35705f5e06b179ba9e69"},
            {"Rubí", "Z033", "69dcd7e3e1c621e441c52695/500b5a6d7308dbc918f1ecd4672d72bb"},
            {"Amores que Fuimos", "Z038", "6a1d32ccdc48ae086d5ea591/f08730593bc42971d67c8f255d7449ba"},
            {"Lo que la Vida me Robó", "Z050", "636ebcf35cce1a695779709b/1428e1f661a06e4285cdf8f1ca57e206"},
            {"Vecinos", "Z071", "6209318522bbc72ac5ec12ee/ad895f26368f89f4b0a7786b97636891"},
    };

    private static List<Channel> vixChannels() {
        List<Channel> out = new ArrayList<>();
        for (String[] v : VIX_CHANNELS) {
            Channel ch = new Channel();
            ch.id = "vix:" + v[1];
            ch.vix = v[1];
            ch.name = v[0];
            String img = "https://images.vix.com/prd/channel/" + v[2];
            ch.logo = img + "?tr=w-240,q-70";
            ch.image = img + "?tr=w-960,q-60";
            out.add(ch);
        }
        return out;
    }
}
