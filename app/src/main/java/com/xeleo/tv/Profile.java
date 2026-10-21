package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The names shown on Home: the TV's name next to the sparkle ("Ana TV", or "Mi TV" without a name) and the name in the greeting.
 * Changed on the TV in Quick settings > Personalizar; config.xml's greeting_name gives the defaults.
 */
final class Profile {

    private static final String PREFS = "profile";

    private Profile() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The name in the greeting; empty for a greeting without a name. */
    static String userName(Context c) {
        return prefs(c).getString("user_name", c.getString(R.string.greeting_name)).trim();
    }

    /** The TV's name next to the sparkle; defaults to "<name> TV". */
    static String tvName(Context c) {
        String saved = prefs(c).getString("tv_name", null);
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        String owner = c.getString(R.string.greeting_name);
        return owner.isEmpty() ? "Mi TV" : owner + " TV";
    }

    /** The weather's place: the one picked in Personalizar, else config.xml's. */
    static String weatherCity(Context c) {
        return prefs(c).getString("city", c.getString(R.string.weather_city));
    }

    static String latitude(Context c) {
        return prefs(c).getString("lat", c.getString(R.string.weather_latitude));
    }

    static String longitude(Context c) {
        return prefs(c).getString("lon", c.getString(R.string.weather_longitude));
    }

    static double lat(Context c) {
        try { return Double.parseDouble(latitude(c)); } catch (NumberFormatException e) { return 25.54; }
    }

    static double lon(Context c) {
        try { return Double.parseDouble(longitude(c)); } catch (NumberFormatException e) { return -103.42; }
    }

    static void saveLocation(Context c, String city, double lat, double lon) {
        prefs(c).edit().putString("city", city).putString("lat", String.valueOf(lat))
                .putString("lon", String.valueOf(lon)).apply();
        Weather.reset();
    }

    static void save(Context c, String tvName, String userName) {
        prefs(c).edit().putString("tv_name", tvName.trim()).putString("user_name", userName.trim()).apply();
    }
}
