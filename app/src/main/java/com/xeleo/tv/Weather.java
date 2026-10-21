package com.xeleo.tv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * One weather line next to the date: "TORREÓN · 24° · DESPEJADO · 31° / 18°", from Open-Meteo (no key).
 * Fetched at most every 30 minutes, and only while Home is on screen (onResume and the minute tick).
 * Coordinates come from config.xml (weather_latitude, weather_longitude); empty turns the line off.
 */
final class Weather {

    private static final long REFRESH_MS = 30 * 60 * 1000L;
    /** After a failed fetch (often just the TV booting before Wi-Fi is up), try again a minute later. */
    private static final long RETRY_MS = 60 * 1000L;

    private static String line;
    private static long lastAttempt;
    private static boolean fetching;

    private Weather() {}

    /** The network just came up: if there is no fresh weather yet, the next update fetches at once. */
    static void networkAvailable() {
        if (line == null || saved) lastAttempt = 0;
    }

    /** Forget the cached weather (the place changed); the next update fetches at once. */
    static void reset() {
        line = null;
        compact = null;
        lastAttempt = 0;
        code = -1;
    }

    /** Short form for a top bar: "☀ 25°". */
    private static String compact;
    /** The last WMO weather code fetched, -1 before the first answer; drives the landscape (Scenery.Kind). */
    private static volatile int code = -1;
    /** Set over adb (ControlReceiver) to preview another weather; -1 follows the real one. */
    static volatile int previewCode = -1;

    /** What the sky looks like now, for the landscape. */
    static Scenery.Kind kind() {
        return Scenery.kindOf(previewCode >= 0 ? previewCode : code);
    }

    static void update(Context context, TextView view) {
        update(context, view, false);
    }

    static void update(Context context, TextView view, boolean small) {
        String lat = Profile.latitude(context);
        String lon = Profile.longitude(context);
        if (lat.isEmpty() || lon.isEmpty()) {
            view.setVisibility(android.view.View.GONE);
            return;
        }
        if (line == null) restore(context);   // after a restart: the last weather, until a fresh one comes
        if (line != null) {
            view.setText(small ? compact : line);
            view.setVisibility(android.view.View.VISIBLE);
        }
        long now = System.currentTimeMillis();
        long wait = line == null || saved ? RETRY_MS : REFRESH_MS;
        if (fetching || (lastAttempt != 0 && now - lastAttempt < wait)) return;
        fetching = true;
        lastAttempt = now;
        String city = Profile.weatherCity(context);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String result = null;
            try {
                result = fetch(lat, lon, city);
            } catch (Exception ignored) {
                // No network or a bad answer: keep the last line and try again later.
            }
            final String text = result;
            main.post(() -> {
                fetching = false;
                if (text == null) return;
                line = text;
                saved = false;
                context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE).edit().putString("line", line)
                        .putString("compact", compact).putInt("code", code).putLong("at", System.currentTimeMillis())
                        .putString("place", lat + "," + lon).apply();
                view.setText(small ? compact : line);
                view.setVisibility(android.view.View.VISIBLE);
            });
        }, "weather").start();
    }

    /** True while the line shown is the saved one (no fresh answer yet since the restart). */
    private static boolean saved;

    /** The weather saved by the last fetch, if it is from the last six hours and for the same place. */
    private static void restore(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("weather_cache", Context.MODE_PRIVATE);
        String place = Profile.latitude(c) + "," + Profile.longitude(c);
        if (System.currentTimeMillis() - p.getLong("at", 0) > 6 * 3600_000L || !place.equals(p.getString("place", ""))) return;
        line = p.getString("line", null);
        compact = p.getString("compact", null);
        if (code < 0) code = p.getInt("code", -1);
        saved = line != null;
    }

    private static String fetch(String lat, String lon, String city) throws Exception {
        URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject json = new JSONObject(out.toString("UTF-8"));
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            long temp = Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            Weather.code = code;
            boolean day = current.optInt("is_day", 1) == 1;
            long max = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
            long min = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
            compact = icon(code, day) + " " + temp + "°";
            String head = city.isEmpty() ? "" : city + "  ·  ";
            return String.format(Locale.ROOT, "%s%s %d°  ·  %s  ·  %d° / %d°",
                    head, icon(code, day), temp, describe(code), max, min);
        } finally {
            conn.disconnect();
        }
    }

    /** WMO weather codes, as Open-Meteo reports them. */
    private static String describe(int code) {
        if (code == 0) return "Despejado";
        if (code <= 2) return "Parcialmente nublado";
        if (code == 3) return "Nublado";
        if (code <= 48) return "Neblina";
        if (code <= 57) return "Llovizna";
        if (code <= 67) return "Lluvia";
        if (code <= 77) return "Nieve";
        if (code <= 82) return "Chubascos";
        if (code <= 86) return "Nevadas";
        return "Tormenta";
    }

    private static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return day ? "⛅" : "☁";
        if (code == 3 || code <= 48) return "☁";
        if (code <= 67 || (code >= 80 && code <= 82)) return "☂";
        if (code <= 77 || code <= 86) return "❄";
        return "⚡";
    }
}
