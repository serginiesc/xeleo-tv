package com.xeleo.tv;

import android.content.Context;
import android.content.Intent;
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
 * cached: every 30 seconds while a game is on, every 15 minutes otherwise. Settings live here too (Quick settings >
 * Deportes).
 */
final class Sports {

    /**
     * A league as ESPN names it, with our label and its group in Deportes. paths are its competitions (all their games
     * go in the "Hoy" row); cups are the other competitions its teams play (the Champions League, a national cup),
     * where only your team's games count. Selecciones covers several competitions, filtered to your nation.
     */
    static final class League {
        final String key, label, group, sport;
        final String[] paths, cups;

        League(String key, String label, String group, String sport, String[] paths, String... cups) {
            this.key = key;
            this.label = label;
            this.group = group;
            this.sport = sport;
            this.paths = paths;
            this.cups = cups;
        }
    }

    private static String[] p(String... paths) {
        return paths;
    }

    /** The groups of Deportes, in their order. */
    static final String[] GROUPS = {"México", "Estados Unidos", "Europa", "Selecciones"};

    private static final String[] UEFA = {"soccer/uefa.champions", "soccer/uefa.europa"};
    private static final String[] CONCACAF = {"soccer/concacaf.champions", "soccer/concacaf.leagues.cup"};

    static final League[] LEAGUES = {
            new League("ligamx", "Liga MX", "México", "soccer", p("soccer/mex.1"), CONCACAF),
            new League("nfl", "NFL", "Estados Unidos", "football", p("football/nfl")),
            new League("nba", "NBA", "Estados Unidos", "basketball", p("basketball/nba")),
            new League("mlb", "MLB", "Estados Unidos", "baseball", p("baseball/mlb")),
            new League("nhl", "NHL", "Estados Unidos", "hockey", p("hockey/nhl")),
            new League("mls", "MLS", "Estados Unidos", "soccer", p("soccer/usa.1"), CONCACAF),
            new League("laliga", "LaLiga", "Europa", "soccer", p("soccer/esp.1"),
                    UEFA[0], UEFA[1], "soccer/esp.copa_del_rey"),
            new League("premier", "Premier League", "Europa", "soccer", p("soccer/eng.1"),
                    UEFA[0], UEFA[1], "soccer/eng.fa", "soccer/eng.league_cup"),
            new League("seriea", "Serie A", "Europa", "soccer", p("soccer/ita.1"), UEFA[0], UEFA[1], "soccer/ita.coppa_italia"),
            new League("bundesliga", "Bundesliga", "Europa", "soccer", p("soccer/ger.1"),
                    UEFA[0], UEFA[1], "soccer/ger.dfb_pokal"),
            new League("champions", "Champions League", "Europa", "soccer", p("soccer/uefa.champions")),
            new League("selecciones", "Selecciones", "Selecciones", "soccer", p("soccer/fifa.world", "soccer/fifa.friendly",
                    "soccer/uefa.nations", "soccer/fifa.worldq.uefa", "soccer/fifa.worldq.concacaf",
                    "soccer/fifa.worldq.conmebol", "soccer/concacaf.nations.league", "soccer/concacaf.gold",
                    "soccer/uefa.euro", "soccer/conmebol.america")),
    };

    /**
     * Your national team (Selecciones), by ESPN abbreviation and name, set by load: outside the World Cup only its games
     * show. Empty until you pick one; then the World Cup still shows whole.
     */
    private static volatile String nationAbbr = "", nationName = "";

    /** A national team's name ("México", "Portugal") takes no article. */
    static boolean isNation(String team) {
        return team.equals(nationName) || team.equals("México") || team.equals("Mexico") || team.equals("Portugal");
    }

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
        /** ESPN's path for the competition ("football/nfl"), for the game's summary. */
        String path = "";
        /** Every channel or app ESPN lists for the game (mostly the US ones); broadcast is the first. */
        final List<String> channels = new ArrayList<>();
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

    // ---------------------------------------------------------------- your teams, one per league

    /** One of your teams, as saved under its league ("t.nfl.id", "t.nfl.abbr"…). */
    static final class MyTeam {
        String league = "", id = "", abbr = "", name = "", shortName = "", logo = "";
        int color = 0xFF5E6874, alt = 0xFFFFFFFF;
    }

    /** The league of the team Home is following now: the one that plays soonest (set by load). */
    private static volatile String focusLeague = "";

    /** Your teams, in the leagues' order (at most one per league). */
    static List<MyTeam> myTeams(Context c) {
        migrate(c);
        List<MyTeam> out = new ArrayList<>();
        for (League l : LEAGUES) {
            MyTeam t = myTeam(c, l.key);
            if (t != null) out.add(t);
        }
        return out;
    }

    /** Your team in a league, or null. */
    static MyTeam myTeam(Context c, String league) {
        SharedPreferences p = prefs(c);
        String k = "t." + league + ".";
        String id = p.getString(k + "id", "");
        if (id.isEmpty()) return null;
        MyTeam t = new MyTeam();
        t.league = league;
        t.id = id;
        t.abbr = p.getString(k + "abbr", "");
        t.name = p.getString(k + "name", "");
        t.shortName = p.getString(k + "short", t.name);
        t.logo = p.getString(k + "logo", "");
        t.color = p.getInt(k + "color", 0xFF5E6874);
        t.alt = p.getInt(k + "alt", 0xFFFFFFFF);
        return t;
    }

    /** The single team of older versions becomes the team of its league. */
    private static void migrate(Context c) {
        SharedPreferences p = prefs(c);
        String league = p.getString("team_league", "");
        if (league.isEmpty()) return;
        if (p.getString("t." + league + ".id", "").isEmpty() && !p.getString("team_id", "").isEmpty()) {
            String k = "t." + league + ".";
            p.edit().putString(k + "id", p.getString("team_id", "")).putString(k + "abbr", p.getString("team_abbr", ""))
                    .putString(k + "name", p.getString("team_name", "")).putString(k + "short", p.getString("team_short", ""))
                    .putString(k + "logo", p.getString("team_logo", "")).putInt(k + "color", p.getInt("team_color", 0xFF5E6874))
                    .putInt(k + "alt", p.getInt("team_alt", 0xFFFFFFFF)).commit();
        }
        p.edit().remove("team_league").remove("team_id").remove("team_abbr").remove("team_name").remove("team_short")
                .remove("team_logo").remove("team_color").remove("team_alt").commit();
    }

    /** The team Home follows now (the one that plays soonest), else your first one; null without teams. */
    static MyTeam focus(Context c) {
        MyTeam t = focusLeague.isEmpty() ? null : myTeam(c, focusLeague);
        if (t != null) return t;
        List<MyTeam> all = myTeams(c);
        return all.isEmpty() ? null : all.get(0);
    }

    /** The followed team's details; empty when you have no team. */
    static String teamLeague(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.league;
    }

    static String teamId(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.id;
    }

    static String teamAbbr(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.abbr;
    }

    static String teamName(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.name;
    }

    static int teamColor(Context c) {
        MyTeam t = focus(c);
        return t == null ? 0xFF5E6874 : t.color;
    }

    static int teamAltColor(Context c) {
        MyTeam t = focus(c);
        return t == null ? 0xFFFFFFFF : t.alt;
    }

    static String teamLogo(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.logo;
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
        NEXT.clear();
    }

    /** Drops what was fetched for the old settings (after a restore). */
    static void forget() {
        synchronized (CACHE) {
            CACHE.clear();
            FETCHED.clear();
        }
        NEXT.clear();
        focusLeague = "";
    }

    /** Your team in that league; a Team without id takes it off. */
    static void setTeam(Context c, String leagueKey, Team t) {
        migrate(c);
        String k = "t." + leagueKey + ".";
        SharedPreferences.Editor e = prefs(c).edit();
        if (t.id.isEmpty()) {
            for (String f : new String[]{"id", "abbr", "name", "short", "logo", "color", "alt"}) e.remove(k + f);
        } else {
            e.putString(k + "id", t.id).putString(k + "abbr", t.abbr).putString(k + "name", t.name)
                    .putString(k + "short", t.shortName).putString(k + "logo", t.logo).putInt(k + "color", t.color)
                    .putInt(k + "alt", t.altColor);
        }
        e.apply();
        NEXT.clear();
        focusLeague = "";
    }

    static String teamShort(Context c) {
        MyTeam t = focus(c);
        return t == null ? "" : t.shortName.isEmpty() ? t.name : t.shortName;
    }

    /** "Chiefs, Santos" for the quick settings tile. */
    static String teamsLabel(Context c) {
        List<String> names = new ArrayList<>();
        for (MyTeam t : myTeams(c)) names.add(t.shortName.isEmpty() ? t.name : t.shortName);
        return TextUtils.join(", ", names);
    }

    // ---------------------------------------------------------------- data

    private static final Map<String, List<Game>> CACHE = new HashMap<>();
    private static final Map<String, Long> FETCHED = new HashMap<>();
    /** Each team's next game (by league and id) and when it was looked up. */
    private static final Map<String, Game> NEXT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_AT = new java.util.concurrent.ConcurrentHashMap<>();
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
        List<MyTeam> teams = myTeams(app);
        Set<String> teamLeagues = new HashSet<>();
        for (MyTeam t : teams) teamLeagues.add(t.league);
        setNation(myTeam(app, "selecciones"));
        new Thread(() -> {
            List<Game> all = new ArrayList<>();
            for (League l : LEAGUES) {
                if (!leagues.contains(l.key) && !teamLeagues.contains(l.key)) continue;
                all.addAll(today(l));
            }
            // Your teams' cup games (Real Madrid in the Champions League), unless that competition is already in.
            for (MyTeam t : teams) {
                League l = league(t.league);
                if (l == null || l.cups.length == 0) continue;
                for (Game g : cups(l)) if (plays(g, t) && !has(all, g)) all.add(g);
            }
            // Your teams' games today; Home follows one: live first, then the next to start, then a finished one.
            List<Game> ours = new ArrayList<>();
            Game mine = null;
            MyTeam follow = null;
            for (MyTeam t : teams) {
                for (Game g : all) {
                    if (!plays(g, t)) continue;
                    ours.add(g);
                    if (mine == null || rank(g) < rank(mine) || (rank(g) == rank(mine) && g.start < mine.start)) {
                        mine = g;
                        follow = t;
                    }
                }
            }
            // No game today: the team that plays soonest.
            Game next = mine;
            if (next == null) {
                for (MyTeam t : teams) {
                    Game n = nextGame(t.league, t.id);
                    if (n != null && (next == null || n.start < next.start)) {
                        next = n;
                        follow = t;
                    }
                }
            }
            focusLeague = follow == null ? "" : follow.league;
            // Your teams' games first (the followed one at the very front), then live, upcoming and finished.
            final Game myGame = mine;
            all.sort((a, b) -> {
                if (a == myGame) return -1;
                if (b == myGame) return 1;
                boolean oa = ours.contains(a), ob = ours.contains(b);
                if (oa != ob) return oa ? -1 : 1;
                int ra = rank(a), rb = rank(b);
                return ra != rb ? Integer.compare(ra, rb) : Long.compare(a.start, b.start);
            });
            List<Game> shown = new ArrayList<>();
            for (Game g : all) if (leagues.contains(g.leagueKey) || ours.contains(g)) shown.add(g);
            final Game n = next;
            MAIN.post(() -> cb.onGames(shown, myGame, n));
        }, "sports").start();
    }

    /** True when something worth a minute-by-minute refresh is happening. */
    static boolean anyLive(List<Game> games) {
        for (Game g : games) if (g.live()) return true;
        return false;
    }

    /** Your team plays this game: the same sport and its ESPN id (an id holds across a club's competitions). */
    static boolean plays(Game g, MyTeam t) {
        League l = league(t.league);
        return l != null && l.sport.equals(g.sport) && !t.id.isEmpty() && (t.id.equals(g.home.id) || t.id.equals(g.away.id));
    }

    private static boolean has(List<Game> games, Game g) {
        for (Game x : games) if (x.id.equals(g.id) && x.sport.equals(g.sport)) return true;
        return false;
    }

    /** Remembers your national team; another one means today's Selecciones have to be filtered again. */
    private static void setNation(MyTeam t) {
        String abbr = t == null ? "" : t.abbr, name = t == null ? "" : t.shortName.isEmpty() ? t.name : t.shortName;
        if (abbr.equals(nationAbbr)) return;
        nationAbbr = abbr;
        nationName = name;
        synchronized (CACHE) {
            CACHE.remove("selecciones");
        }
    }

    /** Today's games (local date) of one league, cached. Never throws: an error just means no games. */
    static List<Game> today(League l) {
        return fetch(l.key, l, l.paths);
    }

    /** Today's games in a league's cups (all of them; load keeps your team's), cached apart from the league's. */
    static List<Game> cups(League l) {
        return fetch(l.key + "+cups", l, l.cups);
    }

    /** A league's games today and your team's cup games: what game alerts watch. */
    static List<Game> withCups(League l, MyTeam t) {
        List<Game> out = new ArrayList<>(today(l));
        for (Game g : cups(l)) if (plays(g, t) && !has(out, g)) out.add(g);
        return out;
    }

    private static List<Game> fetch(String key, League l, String[] paths) {
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            List<Game> cached = CACHE.get(key);
            Long at = FETCHED.get(key);
            if (cached != null && at != null && now - at < (anyLive(cached) ? 25_000 : 15 * 60_000)) return cached;
        }
        List<Game> games = new ArrayList<>();
        String date = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        for (String path : paths) {
            try {
                JSONObject d = getSaved("https://site.api.espn.com/apis/site/v2/sports/" + path + "/scoreboard?dates=" + date,
                        "espn-" + path.replace('/', '_') + ".json");
                JSONArray events = d.optJSONArray("events");
                if (events == null) continue;
                for (int i = 0; i < events.length(); i++) {
                    Game g = parse(events.getJSONObject(i), l);
                    if (g == null || !sameDay(g.start, now)) continue;
                    g.path = path;
                    // National teams: the whole World Cup, and elsewhere (friendlies, qualifiers…) only yours.
                    if (l.key.equals("selecciones") && !path.equals("soccer/fifa.world")
                            && (nationAbbr.isEmpty() || g.side(nationAbbr) == null)) continue;
                    games.add(g);
                }
            } catch (Exception ignored) {
                // No network or a changed answer: this competition shows nothing this time.
            }
        }
        synchronized (CACHE) {
            CACHE.put(key, games);
            FETCHED.put(key, now);
        }
        return games;
    }

    /** 0 live, 1 still to come, 2 finished: the order games are shown and followed in. */
    private static int rank(Game g) {
        return g.live() ? 0 : g.done() ? 2 : 1;
    }

    /** A team's next game (or the one on now) from its schedule, cached for an hour per team. */
    static Game nextGame(String leagueKey, String teamId) {
        long now = System.currentTimeMillis();
        String key = leagueKey + ":" + teamId;
        Game cached = NEXT.get(key);
        Long at = NEXT_AT.get(key);
        if (cached != null && at != null && now - at < 60 * 60_000 && cached.start > now - 4 * 3_600_000L) return cached;
        League l = league(leagueKey);
        if (l == null) return null;
        try {
            // Soccer schedules list only played games unless asked for the fixtures.
            JSONObject d = get("https://site.api.espn.com/apis/site/v2/sports/" + l.paths[0] + "/teams/" + teamId + "/schedule"
                    + ("soccer".equals(l.sport) ? "?fixture=true" : ""));
            JSONArray events = d.optJSONArray("events");
            Game best = null;
            for (int i = 0; events != null && i < events.length(); i++) {
                Game g = parse(events.getJSONObject(i), l);
                if (g == null || g.done()) continue;
                if (best == null || g.start < best.start) best = g;
            }
            if (best != null) {
                NEXT.put(key, best);
                NEXT_AT.put(key, now);
            }
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
            g.path = l.paths[0];
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
            for (int i = 0; bc != null && i < bc.length(); i++) {
                JSONArray names = bc.getJSONObject(i).optJSONArray("names");
                for (int k = 0; names != null && k < names.length(); k++) {
                    String n = names.getString(k);
                    if (!g.channels.contains(n)) g.channels.add(n);
                }
            }
            if (!g.channels.isEmpty()) g.broadcast = g.channels.get(0);
            return g;
        } catch (Exception ex) {
            return null;
        }
    }

    /** The game's state in Spanish: kickoff time, period and clock, or how it ended. */
    static String detail(String sport, Game g, JSONObject status, JSONObject type) {
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
            case "hockey":   // three periods, then overtime
                return (period > 3 ? "TE" : period + "º") + (clock.isEmpty() ? "" : "  " + clock);
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

    /** Channel or service names (lower case, contained in ESPN's) and the TV app that carries them. */
    private static final String[][] APPS = {
            {"prime", "com.amazon.amazonvideo.livingroom"},
            {"espn", "com.disney.disneyplus"},
            {"disney", "com.disney.disneyplus"},
            {"star+", "com.disney.disneyplus"},
            {"vix", "com.univision.prendetv"},
            {"tudn", "com.univision.prendetv"},
            {"univision", "com.univision.prendetv"},
            {"youtube", "com.google.android.youtube.tv"},
            {"netflix", "com.netflix.ninja"},
            {"peacock", "com.peacocktv.peacockandroid"},
            {"paramount", "com.cbs.ca"},
            {"max", "com.wbd.stream"},
            {"hbo", "com.wbd.stream"},
            {"tnt", "com.wbd.stream"},
            {"apple", "com.apple.atve.androidtv.appletv"},
            {"dazn", "com.dazn"},
    };

    /** Where a league's games usually air in Mexico; ESPN lists the US channels, and none for Liga MX. */
    static String[] mexico(String leagueKey) {
        switch (leagueKey) {
            case "ligamx": return new String[]{"ViX", "TUDN", "Azteca 7", "Canal 5", "Fox"};
            case "selecciones": return new String[]{"TUDN", "ViX", "Azteca 7", "Canal 5"};
            case "nfl": return new String[]{"ESPN", "Disney+", "Fox", "Canal 5", "Azteca 7", "Prime Video"};
            case "nba": return new String[]{"ESPN", "Disney+", "NBA League Pass"};
            case "mlb": return new String[]{"ESPN", "Disney+", "Fox"};
            case "nhl": return new String[]{"ESPN", "Disney+"};
            case "mls": return new String[]{"Apple TV"};
            case "laliga": case "seriea": case "bundesliga": return new String[]{"ESPN", "Disney+", "Sky Sports"};
            case "premier": return new String[]{"Sky Sports", "ESPN", "Disney+"};
            case "champions": return new String[]{"Max", "TNT Sports"};
            default: return new String[0];
        }
    }

    /** The TV app that carries a broadcast ("Prime Video" → Prime Video), if it is installed; else null. */
    static Intent broadcastApp(Context c, String broadcast) {
        List<Intent> apps = broadcastApps(c, Collections.singletonList(broadcast == null ? "" : broadcast));
        return apps.isEmpty() ? null : apps.get(0);
    }

    /** The installed TV apps that carry any of these channels, once each, in their order. */
    static List<Intent> broadcastApps(Context c, List<String> names) {
        List<Intent> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : names) {
            String b = name.toLowerCase(Locale.ROOT);
            for (String[] m : APPS) {
                if (!b.contains(m[0]) || seen.contains(m[1])) continue;
                Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(m[1]);
                if (i == null) continue;
                seen.add(m[1]);
                out.add(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
        return out;
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

    /** Where today's answers are kept, so Home still has the day's games while the TV has no internet. */
    private static java.io.File dir;

    static void init(Context c) {
        if (dir == null) dir = c.getApplicationContext().getFilesDir();
    }

    /** get, keeping a copy of the answer; without network, the copy if it is from today. */
    private static JSONObject getSaved(String url, String name) throws Exception {
        java.io.File f = dir == null ? null : new java.io.File(dir, name);
        try {
            JSONObject d = get(url);
            if (f != null) {
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    out.write(d.toString().getBytes("UTF-8"));
                }
            }
            return d;
        } catch (Exception e) {
            if (f == null || !f.exists() || !sameDay(f.lastModified(), System.currentTimeMillis())) throw e;
            try (InputStream in = new java.io.FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return new JSONObject(out.toString("UTF-8"));
            }
        }
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

    /** Countries as ESPN writes them, in Spanish. */
    private static final String[][] COUNTRIES = {{"Mexico", "México"}, {"Spain", "España"}, {"Germany", "Alemania"},
            {"France", "Francia"}, {"England", "Inglaterra"}, {"Brazil", "Brasil"}, {"United States", "Estados Unidos"},
            {"USA", "Estados Unidos"}, {"Netherlands", "Países Bajos"}, {"Japan", "Japón"}, {"South Korea", "Corea del Sur"},
            {"Belgium", "Bélgica"}, {"Switzerland", "Suiza"}, {"Croatia", "Croacia"}, {"Morocco", "Marruecos"},
            {"Canada", "Canadá"}, {"Italy", "Italia"}, {"Scotland", "Escocia"}, {"Norway", "Noruega"},
            {"Egypt", "Egipto"}, {"Turkey", "Turquía"}, {"Panama", "Panamá"}};

    private static String spanish(String country) {
        for (String[] c : COUNTRIES) if (c[0].equals(country)) return c[1];
        return country;
    }

    /** All teams of a league, for the team picker (Selecciones: the World Cup's, or two of ours without network). */
    static List<Team> teams(League l) {
        List<Team> out = new ArrayList<>();
        if (l.key.equals("selecciones")) {
            out = fetchTeams("soccer/fifa.world");
            for (Team t : out) {
                t.name = spanish(t.name);
                t.shortName = spanish(t.shortName);
            }
            out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            if (!out.isEmpty()) return out;
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
        return fetchTeams(l.paths[0]);
    }

    /** A competition's teams from ESPN, by name; empty without network. */
    private static List<Team> fetchTeams(String path) {
        List<Team> out = new ArrayList<>();
        try {
            JSONObject d = get("https://site.api.espn.com/apis/site/v2/sports/" + path + "/teams");
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
