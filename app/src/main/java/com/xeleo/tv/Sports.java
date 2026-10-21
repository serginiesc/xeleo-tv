package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Sports mode: today's games of the leagues you follow and your team's next game, from ESPN's public scoreboard
 * (site.api.espn.com, no key; not an official, documented API, so it may change). Fetched in the background and
 * cached: every minute while a game is on, every 15 minutes otherwise. Settings live here too (Quick settings >
 * Deportes).
 */
final class Sports {

    /** A league as ESPN names it, with our label. Selecciones covers several competitions, filtered to NATIONS. */
    static final class League {
        final String key, label, sport;
        final String[] paths;

        League(String key, String label, String sport, String... paths) {
            this.key = key;
            this.label = label;
            this.sport = sport;
            this.paths = paths;
        }
    }

    static final League[] LEAGUES = {
            new League("nfl", "NFL", "football", "football/nfl"),
            new League("ligamx", "Liga MX", "soccer", "soccer/mex.1"),
            new League("selecciones", "Selecciones", "soccer", "soccer/fifa.world", "soccer/fifa.friendly",
                    "soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/concacaf.nations.league",
                    "soccer/concacaf.gold", "soccer/uefa.euro"),
            new League("nba", "NBA", "basketball", "basketball/nba"),
            new League("mlb", "MLB", "baseball", "baseball/mlb"),
    };

    /** National teams shown under Selecciones (ESPN abbreviations). */
    static final Set<String> NATIONS = new HashSet<>(java.util.Arrays.asList("POR", "MEX"));
    static final Set<String> NATIONS_NAMES = new HashSet<>(java.util.Arrays.asList("Portugal", "México", "Mexico"));

    static League league(String key) {
        for (League l : LEAGUES) if (l.key.equals(key)) return l;
        return null;
    }

    static final class Team {
        String abbr = "", name = "", shortName = "", logo = "", id = "";
        int color = 0xFF5E6874, altColor = 0xFFFFFFFF;
        String score = "";
        boolean winner;
    }

    static final class Game {
        String id, leagueKey, sport, state = "pre", detail = "", broadcast = "";
        long start;
        Team home = new Team(), away = new Team();

        boolean live() {
            return "in".equals(state);
        }

        boolean done() {
            return "post".equals(state);
        }

        /** The team that is `abbr` in this game, or null. */
        Team side(String abbr) {
            if (abbr.equals(home.abbr)) return home;
            if (abbr.equals(away.abbr)) return away;
            return null;
        }

        Team other(String abbr) {
            return abbr.equals(home.abbr) ? away : home;
        }
    }

    // ---------------------------------------------------------------- settings

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("sports", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("on", false);
    }

    static Set<String> leagues(Context c) {
        return new HashSet<>(prefs(c).getStringSet("leagues",
                new HashSet<>(java.util.Arrays.asList("nfl", "ligamx", "selecciones", "nba", "mlb"))));
    }

    /** Your team: league key, ESPN team id and abbreviation; empty when none is picked. */
    static String teamLeague(Context c) {
        return prefs(c).getString("team_league", "");
    }

    static String teamId(Context c) {
        return prefs(c).getString("team_id", "");
    }

    static String teamAbbr(Context c) {
        return prefs(c).getString("team_abbr", "");
    }

    static String teamName(Context c) {
        return prefs(c).getString("team_name", "");
    }

    static int teamColor(Context c) {
        return prefs(c).getInt("team_color", 0xFF5E6874);
    }

    static int teamAltColor(Context c) {
        return prefs(c).getInt("team_alt", 0xFFFFFFFF);
    }

    static String teamLogo(Context c) {
        return prefs(c).getString("team_logo", "");
    }

    static boolean alerts(Context c) {
        return prefs(c).getBoolean("alerts", true);
    }


    static void set(Context c, String key, Object value) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) e.putInt(key, (Integer) value);
        else if (value instanceof Set) {
            @SuppressWarnings("unchecked") Set<String> s = (Set<String>) value;
            e.putStringSet(key, s);
        } else e.putString(key, String.valueOf(value));
        e.apply();
        synchronized (CACHE) {
            CACHE.clear();
        }
        nextGame = null;
        nextGameAt = 0;
    }

    static void setTeam(Context c, String leagueKey, Team t) {
        prefs(c).edit().putString("team_league", leagueKey).putString("team_id", t.id).putString("team_abbr", t.abbr)
                .putString("team_name", t.name).putString("team_short", t.shortName).putInt("team_color", t.color)
                .putInt("team_alt", t.altColor).putString("team_logo", t.logo).apply();
        nextGame = null;
        nextGameAt = 0;
    }

    static String teamShort(Context c) {
        String s = prefs(c).getString("team_short", "");
        return s.isEmpty() ? teamName(c) : s;
    }

    // ---------------------------------------------------------------- data

    private static final Map<String, List<Game>> CACHE = new HashMap<>();
    private static final Map<String, Long> FETCHED = new HashMap<>();
    private static Game nextGame;
    private static long nextGameAt;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onGames(List<Game> today, Game myGame, Game next);
    }

    /**
     * Today's games of the followed leagues (your team's first, then live, upcoming and finished), your team's game
     * today if any, and its next game. Calls back on the main thread; uses the cache when it is fresh enough.
     */
    static void load(Context context, Callback cb) {
        Context app = context.getApplicationContext();
        if (!enabled(app)) {
            MAIN.post(() -> cb.onGames(Collections.emptyList(), null, null));
            return;
        }
        Set<String> leagues = leagues(app);
        String myLeague = teamLeague(app), myAbbr = teamAbbr(app), myId = teamId(app);
        new Thread(() -> {
            List<Game> all = new ArrayList<>();
            for (League l : LEAGUES) {
                if (!leagues.contains(l.key) && !l.key.equals(myLeague)) continue;
                all.addAll(today(l));
            }
            Game mine = null;
            if (!myAbbr.isEmpty()) {
                for (Game g : all) if (g.leagueKey.equals(myLeague) && g.side(myAbbr) != null) mine = g;
            }
            Game next = mine;
            if (next == null && !myId.isEmpty()) next = nextGame(myLeague, myId);
            // Your team's game first, then live, then upcoming by time, then finished.
            final Game myGame = mine;
            all.sort((a, b) -> {
                if (a == myGame) return -1;
                if (b == myGame) return 1;
                int ra = a.live() ? 0 : a.done() ? 2 : 1, rb = b.live() ? 0 : b.done() ? 2 : 1;
                return ra != rb ? Integer.compare(ra, rb) : Long.compare(a.start, b.start);
            });
            List<Game> shown = all;
            if (!leagues.contains(myLeague) && myGame != null) {
                shown = new ArrayList<>();
                for (Game g : all) if (leagues.contains(g.leagueKey) || g == myGame) shown.add(g);
            }
            final List<Game> result = shown;
            final Game n = next;
            MAIN.post(() -> cb.onGames(result, myGame, n));
        }, "sports").start();
    }

    /** True when something worth a minute-by-minute refresh is happening. */
    static boolean anyLive(List<Game> games) {
        for (Game g : games) if (g.live()) return true;
        return false;
    }

    /** Today's games (local date) of one league, cached. Never throws: an error just means no games. */
    static List<Game> today(League l) {
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            List<Game> cached = CACHE.get(l.key);
            Long at = FETCHED.get(l.key);
            if (cached != null && at != null && now - at < (anyLive(cached) ? 55_000 : 15 * 60_000)) return cached;
        }
        List<Game> games = new ArrayList<>();
        String date = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        for (String path : l.paths) {
            try {
                JSONObject d = get("https://site.api.espn.com/apis/site/v2/sports/" + path + "/scoreboard?dates=" + date);
                JSONArray events = d.optJSONArray("events");
                if (events == null) continue;
                for (int i = 0; i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), l);
                    if (g == null || !sameDay(g.start, now)) continue;
                    if (l.key.equals("selecciones") && !NATIONS.contains(g.home.abbr) && !NATIONS.contains(g.away.abbr)) continue;
                    games.add(g);
                }
            } catch (Exception ignored) {
                // No network or a changed answer: this competition shows nothing this time.
            }
        }
        synchronized (CACHE) {
            CACHE.put(l.key, games);
            FETCHED.put(l.key, now);
        }
        return games;
    }

    /** Your team's next game (or the one on now) from its schedule, cached for an hour. */
    static Game nextGame(String leagueKey, String teamId) {
        long now = System.currentTimeMillis();
        if (nextGame != null && now - nextGameAt < 60 * 60_000 && (nextGame.start > now - 4 * 3_600_000L)) return nextGame;
        League l = league(leagueKey);
        if (l == null) return null;
        try {
            JSONObject d = get("https://site.api.espn.com/apis/site/v2/sports/" + l.paths[0] + "/teams/" + teamId + "/schedule");
            JSONArray events = d.optJSONArray("events");
            Game best = null;
            for (int i = 0; events != null && i < events.length(); i++) {
                Game g = parse(events.getJSONObject(i), l);
                if (g == null || g.done()) continue;
                if (best == null || g.start < best.start) best = g;
            }
            nextGame = best;
            nextGameAt = now;
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    /** One ESPN event; null if it lacks what we show. */
    static Game parse(JSONObject e, League l) {
        try {
            Game g = new Game();
            g.id = e.optString("id");
            g.leagueKey = l.key;
            g.sport = l.sport;
            g.start = parseDate(e.getString("date"));
            JSONObject comp = e.getJSONArray("competitions").getJSONObject(0);
            JSONObject status = e.has("status") ? e.getJSONObject("status") : comp.getJSONObject("status");
            JSONObject type = status.getJSONObject("type");
            g.state = type.optString("state", "pre");
            g.detail = detail(l.sport, g, status, type);
            JSONArray teams = comp.getJSONArray("competitors");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject c = teams.getJSONObject(i);
                Team t = "home".equals(c.optString("homeAway")) ? g.home : g.away;
                JSONObject team = c.getJSONObject("team");
                t.id = team.optString("id");
                t.abbr = team.optString("abbreviation");
                t.name = team.optString("displayName");
                t.shortName = team.optString("shortDisplayName", t.name);
                t.color = color(team.optString("color"), 0xFF5E6874);
                t.altColor = color(team.optString("alternateColor"), 0xFFFFFFFF);
                t.logo = team.optString("logo");
                if (t.logo.isEmpty() && team.optJSONArray("logos") != null && team.getJSONArray("logos").length() > 0) {
                    t.logo = team.getJSONArray("logos").getJSONObject(0).optString("href");
                }
                Object score = c.opt("score");
                if (score instanceof JSONObject) t.score = ((JSONObject) score).optString("displayValue");
                else if (score != null) t.score = String.valueOf(score);
                t.winner = c.optBoolean("winner");
            }
            JSONArray bc = comp.optJSONArray("broadcasts");
            if (bc != null && bc.length() > 0) {
                JSONArray names = bc.getJSONObject(0).optJSONArray("names");
                if (names != null && names.length() > 0) g.broadcast = names.getString(0);
            }
            return g;
        } catch (Exception ex) {
            return null;
        }
    }

    /** The game's state in Spanish: kickoff time, period and clock, or how it ended. */
    private static String detail(String sport, Game g, JSONObject status, JSONObject type) {
        String name = type.optString("name");
        if (name.contains("POSTPONED")) return "Pospuesto";
        if (name.contains("CANCELED")) return "Cancelado";
        if ("post".equals(g.state)) return name.contains("OVERTIME") || type.optString("shortDetail").contains("OT") ? "Final (TE)" : "Final";
        if ("pre".equals(g.state)) return "";
        if (name.contains("HALFTIME")) return "Medio tiempo";
        int period = status.optInt("period");
        String clock = status.optString("displayClock");
        switch (sport) {
            case "soccer":
                return clock.isEmpty() ? "En vivo" : clock;
            case "baseball": {
                String sd = type.optString("shortDetail").toLowerCase(Locale.ROOT);
                return (sd.startsWith("bot") ? "Baja " : sd.startsWith("mid") || sd.startsWith("end") ? "Fin " : "Alta ") + period + "ª";
            }
            default:
                return (period > 4 ? "TE" : period + "º") + (clock.isEmpty() ? "" : "  " + clock);
        }
    }

    /** "6:20 p. m." today, or "dom 6:20 p. m." / "dom 25 oct" further out. */
    static String when(Context c, long start) {
        Calendar now = Calendar.getInstance(), s = Calendar.getInstance();
        s.setTimeInMillis(start);
        String time = DateFormat.getTimeFormat(c).format(new Date(start));
        if (sameDay(start, now.getTimeInMillis())) return time;
        long days = (start - now.getTimeInMillis()) / 86_400_000L;
        String day = new SimpleDateFormat(days < 6 ? "EEE" : "EEE d MMM", new Locale("es", "MX")).format(new Date(start));
        return day + " " + time;
    }

    /** "faltan 2 h 15 min" until kickoff; empty once it started. */
    static String countdown(long start) {
        long m = (start - System.currentTimeMillis()) / 60_000;
        if (m <= 0) return "";
        if (m < 60) return "faltan " + m + " min";
        return "faltan " + (m / 60) + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
    }

    static boolean sameDay(long a, long b) {
        Calendar x = Calendar.getInstance(), y = Calendar.getInstance();
        x.setTimeInMillis(a);
        y.setTimeInMillis(b);
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    private static long parseDate(String iso) throws Exception {
        String s = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
        SimpleDateFormat f = new SimpleDateFormat(s.length() > 16 ? "yyyy-MM-dd'T'HH:mm:ss" : "yyyy-MM-dd'T'HH:mm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.parse(s).getTime();
    }

    private static int color(String hex, int fallback) {
        if (TextUtils.isEmpty(hex)) return fallback;
        try {
            return Color.parseColor("#" + hex.replace("#", ""));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** A small logo through ESPN's image resizer, so a 500 px PNG doesn't land in memory. */
    static String smallLogo(String url, int px) {
        if (url == null || !url.contains("espncdn.com/")) return url;
        String path = url.substring(url.indexOf("espncdn.com/") + "espncdn.com".length());
        return "https://a.espncdn.com/combiner/i?img=" + path + "&w=" + px + "&h=" + px;
    }

    static JSONObject get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }

    /** All teams of a league, for the team picker (Selecciones: the followed nations). */
    static List<Team> teams(League l) {
        List<Team> out = new ArrayList<>();
        if (l.key.equals("selecciones")) {
            String[][] nations = {{"POR", "Portugal", "482", "DA291C", "0D6938"}, {"MEX", "México", "203", "006847", "CE1126"}};
            for (String[] n : nations) {
                Team t = new Team();
                t.abbr = n[0];
                t.name = n[1];
                t.shortName = n[1];
                t.id = n[2];
                t.color = color(n[3], 0xFF5E6874);
                t.altColor = color(n[4], 0xFFFFFFFF);
                t.logo = "https://a.espncdn.com/i/teamlogos/countries/500/" + n[0].toLowerCase(Locale.ROOT) + ".png";
                out.add(t);
            }
            return out;
        }
        try {
            JSONObject d = get("https://site.api.espn.com/apis/site/v2/sports/" + l.paths[0] + "/teams");
            JSONArray teams = d.getJSONArray("sports").getJSONObject(0).getJSONArray("leagues").getJSONObject(0)
                    .getJSONArray("teams");
            for (int i = 0; i < teams.length(); i++) {
                JSONObject team = teams.getJSONObject(i).getJSONObject("team");
                Team t = new Team();
                t.id = team.optString("id");
                t.abbr = team.optString("abbreviation");
                t.name = team.optString("displayName");
                t.shortName = team.optString("shortDisplayName", t.name);
                t.color = color(team.optString("color"), 0xFF5E6874);
                t.altColor = color(team.optString("alternateColor"), 0xFFFFFFFF);
                JSONArray logos = team.optJSONArray("logos");
                if (logos != null && logos.length() > 0) t.logo = logos.getJSONObject(0).optString("href");
                out.add(t);
            }
            out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        } catch (Exception ignored) {
        }
        return out;
    }
}
