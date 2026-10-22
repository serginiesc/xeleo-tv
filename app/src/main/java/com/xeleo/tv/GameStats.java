package com.xeleo.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One game's panel: score by period, leaders, a side-by-side of team stats and the standings of the group, from
 * ESPN's public game summary (the same unofficial service as Sports). Built on a background thread.
 */
final class GameStats {

    static final class Side {
        String id = "", abbr = "", name = "", logo = "", record = "", score = "";
        int color = 0xFF5E6874;
        final List<String> periods = new ArrayList<>();
    }

    static final class Leader {
        String category = "", name = "", line = "", logo = "";
    }

    static final class Compare {
        String label = "", a = "", b = "";
        double va, vb;
    }

    static final class Standing {
        int rank;
        String id = "", name = "", logo = "", c1 = "", c2 = "", c3 = "";
        boolean mine;
    }

    String topLine = "", state = "pre", status = "", sport = "", groupName = "", footer = "", footerLogo = "";
    String broadcast = "";
    /** Dónde verlo: the channels ESPN lists (mostly the US ones) and where the league usually airs in Mexico. */
    final List<String> where = new ArrayList<>();
    String[] mexico = {};
    String[] heads = {"G-P", "Dif", "Racha"};
    String[] periodHeads = {};
    final Side left = new Side(), right = new Side();
    final List<Leader> leaders = new ArrayList<>();
    final List<Compare> compare = new ArrayList<>();
    final List<Standing> table = new ArrayList<>();

    private static final Map<String, Map<String, Sports.Team>> TEAMS = new HashMap<>();
    private static final Locale ES = new Locale("es", "MX");

    /**
     * The panel for a game; with an empty eventId, for your team's last game (or its next one, early in the
     * season). Throws when ESPN doesn't answer.
     */
    static GameStats load(Context c, String path, String leagueKey, String eventId, String broadcast) throws Exception {
        Sports.League league = Sports.league(leagueKey);
        String myId = Sports.teamId(c);
        boolean teamMode = eventId.isEmpty();
        if (teamMode) {
            leagueKey = Sports.teamLeague(c);
            league = Sports.league(leagueKey);
            path = league.paths[0];
            eventId = lastGame(path, league, myId);
        }
        JSONObject s = Sports.get("https://site.api.espn.com/apis/site/v2/sports/" + path + "/summary?event=" + eventId);
        GameStats g = new GameStats();
        g.sport = league != null ? league.sport : "";
        // "ESPN, ABC, Disney+" from the Hoy row, then what the summary adds.
        for (String n : (broadcast == null ? "" : broadcast).split(",\\s*")) if (!n.isEmpty()) g.where.add(n);
        JSONArray casts = s.optJSONArray("broadcasts");
        for (int i = 0; casts != null && i < casts.length(); i++) {
            JSONObject media = casts.optJSONObject(i).optJSONObject("media");
            String n = media == null ? "" : media.optString("shortName");
            if (!n.isEmpty() && !g.where.contains(n)) g.where.add(n);
        }
        g.broadcast = g.where.isEmpty() ? "" : g.where.get(0);
        g.mexico = Sports.mexico(leagueKey);
        JSONObject comp = s.getJSONObject("header").getJSONArray("competitions").getJSONObject(0);
        long start = parseDate(comp.optString("date"));
        JSONObject status = comp.getJSONObject("status");
        JSONObject type = status.getJSONObject("type");
        g.state = type.optString("state", "pre");
        Sports.Game probe = new Sports.Game();
        probe.state = g.state;
        String detail = Sports.detail(g.sport, probe, status, type);
        g.status = g.state.equals("pre") ? Sports.when(c, start) : g.state.equals("post") ? detail.toUpperCase(ES) : detail;
        String label = league != null ? league.label.toUpperCase(ES) : "";
        String date = new SimpleDateFormat("EEE d MMM", ES).format(new Date(start)).toUpperCase(ES).replace(".", "");
        g.topLine = (teamMode && g.state.equals("post") ? "ÚLTIMO PARTIDO  ·  " : g.state.equals("in") ? "EN VIVO  ·  " : "")
                + label + "  ·  " + (Sports.sameDay(start, System.currentTimeMillis()) ? "HOY" : date);

        // Sides: your team on the left when it plays, else away on the left and home on the right, as in "Hoy".
        JSONArray comps = comp.getJSONArray("competitors");
        JSONObject home = null, away = null;
        for (int i = 0; i < comps.length(); i++) {
            JSONObject o = comps.getJSONObject(i);
            if ("home".equals(o.optString("homeAway"))) home = o;
            else away = o;
        }
        if (home == null || away == null) throw new Exception("not a two-team game");
        boolean homeIsMine = home.getJSONObject("team").optString("id").equals(myId);
        side(g.left, homeIsMine ? home : away);
        side(g.right, homeIsMine ? away : home);
        int n = Math.max(g.left.periods.size(), g.right.periods.size());
        g.periodHeads = new String[n];
        for (int i = 0; i < n; i++) g.periodHeads[i] = periodHead(g.sport, i);

        leaders(s, g);
        compare(s, g);
        standings(c, s, g, path, league, myId);
        footer(c, s, g, leagueKey, myId, start);
        return g;
    }

    /** Your team's most recent finished game; its next one when it hasn't played yet. */
    private static String lastGame(String path, Sports.League league, String teamId) throws Exception {
        JSONObject d = Sports.get("https://site.api.espn.com/apis/site/v2/sports/" + path + "/teams/" + teamId + "/schedule");
        JSONArray events = d.getJSONArray("events");
        Sports.Game last = null, next = null;
        for (int i = 0; i < events.length(); i++) {
            Sports.Game e = Sports.parse(events.getJSONObject(i), league);
            if (e == null) continue;
            if (e.done() || e.live()) {
                if (last == null || e.start > last.start) last = e;
            } else if (next == null || e.start < next.start) {
                next = e;
            }
        }
        Sports.Game pick = last != null ? last : next;
        if (pick == null) throw new Exception("no games");
        return pick.id;
    }

    private static void side(Side s, JSONObject o) {
        JSONObject t = o.optJSONObject("team");
        if (t != null) {
            s.id = t.optString("id");
            s.abbr = t.optString("abbreviation");
            // "Chiefs", "Pistons", "Monterrey": the nickname where there is one.
            s.name = t.optString("shortDisplayName", t.optString("name", t.optString("displayName")));
            JSONArray logos = t.optJSONArray("logos");
            if (logos != null && logos.length() > 0) s.logo = logos.optJSONObject(0).optString("href");
            if (s.logo.isEmpty()) s.logo = t.optString("logo");
            try {
                s.color = android.graphics.Color.parseColor("#" + t.optString("color", "5E6874"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        s.score = o.optString("score");
        JSONArray rec = o.optJSONArray("record");
        if (rec != null && rec.length() > 0) s.record = rec.optJSONObject(0).optString("summary");
        JSONArray ls = o.optJSONArray("linescores");
        for (int i = 0; ls != null && i < ls.length(); i++) s.periods.add(ls.optJSONObject(i).optString("displayValue"));
    }

    private static String periodHead(String sport, int i) {
        switch (sport) {
            case "soccer":
                return i < 2 ? (i + 1) + "T" : "TE";
            case "baseball":
                return String.valueOf(i + 1);
            default:
                return i < 4 ? String.valueOf(i + 1) : "TE";
        }
    }

    // ---------------------------------------------------------------- leaders

    private static final String[][] CATEGORIES = {
            {"Passing Yards", "PASES"}, {"Rushing Yards", "CARRERA"}, {"Receiving Yards", "RECEPCIÓN"},
            {"Sacks", "CAPTURAS"}, {"Tackles", "TACLEADAS"}, {"Points", "PUNTOS"}, {"Rebounds", "REBOTES"},
            {"Assists", "ASISTENCIAS"}, {"Goals", "GOLES"}, {"Total Shots", "TIROS"}, {"Saves", "ATAJADAS"},
            {"Accurate Passes", "PASES COMPLETOS"}, {"Defensive Interventions", "DEFENSA"},
    };

    /** Three leaders of the left team and the top one of the right team. */
    private static void leaders(JSONObject s, GameStats g) {
        JSONArray all = s.optJSONArray("leaders");
        for (int pass = 0; pass < 2 && all != null; pass++) {
            Side side = pass == 0 ? g.left : g.right;
            for (int i = 0; i < all.length(); i++) {
                JSONObject t = all.optJSONObject(i);
                JSONObject team = t.optJSONObject("team");
                if (team == null || !side.id.equals(team.optString("id"))) continue;
                JSONArray cats = t.optJSONArray("leaders");
                int taken = 0;
                for (int k = 0; cats != null && k < cats.length() && taken < (pass == 0 ? 3 : 1); k++) {
                    JSONObject cat = cats.optJSONObject(k);
                    JSONArray who = cat.optJSONArray("leaders");
                    if (who == null || who.length() == 0) continue;
                    JSONObject top = who.optJSONObject(0);
                    Leader l = new Leader();
                    l.category = translate(cat.optString("displayName"), CATEGORIES).toUpperCase(ES);
                    l.name = top.optJSONObject("athlete") != null ? top.optJSONObject("athlete").optString("displayName") : "";
                    l.line = line(top.optString("displayValue"), l.category);
                    l.logo = side.logo;
                    g.leaders.add(l);
                    taken++;
                }
            }
        }
    }

    /**
     * "19 CAR, 128 YDS, 2 TD" → "19 acarreos · 128 yds · 2 TD"; "Matches: 3, Goals: 5" (season leaders, before a
     * soccer game) → "3 partidos · 5 goles"; a bare number gets the category: "22 puntos".
     */
    private static String line(String v, String category) {
        if (v.matches("[0-9.]+")) return v + " " + category.toLowerCase(ES);
        String[][] words = {{"Matches", "partidos"}, {"Goals", "goles"}, {"Assists", "asistencias"},
                {"Shots", "tiros"}, {"Saves", "atajadas"}, {"Points", "puntos"}, {"Rebounds", "rebotes"}};
        for (String[] w : words) v = v.replaceAll(w[0] + ":\\s*([0-9.]+)", "$1 " + w[1]);
        return v.replace(" YDS", " yds").replace(" CAR", " acarreos").replace(" REC", " recepciones")
                .replace(", ", "  ·  ");
    }

    // ---------------------------------------------------------------- team comparison

    private static final Map<String, String[][]> PREFERRED = new HashMap<>();

    static {
        PREFERRED.put("football", new String[][]{{"Total Yards", "Yardas totales"}, {"Passing", "Por pase"},
                {"Rushing", "Por tierra"}, {"1st Downs", "Primeros downs"}, {"Possession", "Posesión"}});
        PREFERRED.put("basketball", new String[][]{{"Field Goal %", "% de tiro"}, {"Three Point %", "% de triples"},
                {"Rebounds", "Rebotes"}, {"Assists", "Asistencias"}, {"Turnovers", "Pérdidas"}});
        PREFERRED.put("soccer", new String[][]{{"Possession", "Posesión %"}, {"SHOTS", "Tiros"},
                {"ON GOAL", "A puerta"}, {"Corner Kicks", "Tiros de esquina"}, {"Fouls", "Faltas"}});
        PREFERRED.put("baseball", new String[][]{{"hits", "Hits"}, {"homeRuns", "Jonrones"},
                {"strikeouts", "Ponches"}, {"walks", "Bases por bola"}, {"errors", "Errores"}});
    }

    private static void compare(JSONObject s, GameStats g) {
        JSONObject box = s.optJSONObject("boxscore");
        JSONArray teams = box == null ? null : box.optJSONArray("teams");
        String[][] wanted = PREFERRED.get(g.sport);
        if (teams == null || wanted == null) return;
        Map<String, String> a = null, b = null;
        for (int i = 0; i < teams.length(); i++) {
            JSONObject t = teams.optJSONObject(i);
            String id = t.optJSONObject("team") != null ? t.optJSONObject("team").optString("id") : "";
            Map<String, String> m = new HashMap<>();
            flatten(t.optJSONArray("statistics"), m);
            if (id.equals(g.left.id)) a = m;
            else if (id.equals(g.right.id)) b = m;
        }
        if (a == null || b == null) return;
        for (String[] w : wanted) {
            String va = a.get(w[0]), vb = b.get(w[0]);
            if (va == null || vb == null) continue;
            Compare c = new Compare();
            c.label = w[1];
            c.a = va;
            c.b = vb;
            c.va = number(va);
            c.vb = number(vb);
            if (c.va + c.vb <= 0) continue;
            g.compare.add(c);
        }
    }

    /** Stats by their label (or name, for baseball's groups of stats). */
    private static void flatten(JSONArray stats, Map<String, String> out) {
        for (int i = 0; stats != null && i < stats.length(); i++) {
            JSONObject st = stats.optJSONObject(i);
            if (st == null) continue;
            if (st.has("stats")) {
                flatten(st.optJSONArray("stats"), out);
                continue;
            }
            String v = st.optString("displayValue");
            if (st.has("label")) out.put(st.optString("label"), v);
            if (st.has("name") && !out.containsKey(st.optString("name"))) out.put(st.optString("name"), v);
        }
    }

    /** "30:26" → seconds; "45" → 45; "0.4" → 0.4. */
    private static double number(String v) {
        try {
            if (v.contains(":")) {
                String[] p = v.split(":");
                return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
            }
            return Double.parseDouble(v.replace("%", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- standings

    /** A group of the standings (a division, a conference, a league table) as rows of stats by name. */
    private static final class Group {
        String name = "";
        final List<String> ids = new ArrayList<>(), names = new ArrayList<>(), logos = new ArrayList<>();
        final List<Map<String, String>> stats = new ArrayList<>();
    }

    /**
     * The standings of the group with your team (else the left team's), around it if the group is long. From
     * ESPN's standings by division, which have the streak and point difference; else from the game summary's.
     */
    private static void standings(Context c, JSONObject s, GameStats g, String path, Sports.League league, String myId) {
        List<Group> groups = new ArrayList<>();
        try {
            collect(Sports.get("https://site.api.espn.com/apis/v2/sports/" + path + "/standings?level=3"), groups);
        } catch (Exception ignored) {
        }
        if (groups.isEmpty()) {
            JSONObject st = s.optJSONObject("standings");
            JSONArray list = st == null ? null : st.optJSONArray("groups");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                Group gr = group(o.optJSONObject("standings"), o.optString("header"));
                if (gr != null) groups.add(gr);
            }
        }
        Group group = null;
        String focus = null;
        for (String id : new String[]{myId, g.left.id, g.right.id}) {
            if (id.isEmpty()) continue;
            for (Group gr : groups) {
                if (gr.ids.contains(id)) {
                    group = gr;
                    focus = id;
                    break;
                }
            }
            if (group != null) break;
        }
        if (group == null) return;
        g.groupName = groupName(group.name);
        boolean soccer = g.sport.equals("soccer");
        g.heads = soccer ? new String[]{"PJ", "Dif", "Pts"} : new String[]{"G-P", "Dif", "Racha"};
        Map<String, Sports.Team> teams = teams(path, league);
        int at = group.ids.indexOf(focus), n = group.ids.size();
        int from = Math.max(0, Math.min(at - 2, n - 5));   // five rows fit the panel
        for (int k = from; k < Math.min(n, from + 5); k++) {
            Map<String, String> v = group.stats.get(k);
            Standing r = new Standing();
            r.id = group.ids.get(k);
            String rank = first(v, "rank", "playoffSeed");
            r.rank = soccer && !rank.isEmpty() ? (int) number(rank) : k + 1;
            Sports.Team t = teams.get(r.id);
            r.name = t != null ? t.shortName : group.names.get(k);
            r.logo = !group.logos.get(k).isEmpty() ? group.logos.get(k) : t != null ? t.logo : "";
            r.mine = r.id.equals(myId) || (myId.isEmpty() && r.id.equals(focus));
            String dif = first(v, "pointDifferential", "differential", "runDifferential");
            if (dif.isEmpty() && v.containsKey("pointsFor") && v.containsKey("pointsAgainst")) {
                int d = (int) (number(v.get("pointsFor")) - number(v.get("pointsAgainst")));
                dif = (d > 0 ? "+" : "") + d;
            }
            if (soccer) {
                r.c1 = first(v, "gamesPlayed");
                r.c2 = dif;
                r.c3 = first(v, "points");
            } else {
                String ties = first(v, "ties");
                r.c1 = first(v, "wins") + "-" + first(v, "losses") + (ties.isEmpty() || ties.equals("0") ? "" : "-" + ties);
                r.c2 = dif;
                r.c3 = first(v, "streak").replace("W", "G").replace("L", "P");
            }
            g.table.add(r);
        }
    }

    /** The groups that hold teams, at any depth of ESPN's standings tree. */
    private static void collect(JSONObject node, List<Group> out) {
        Group gr = group(node.optJSONObject("standings"), node.optString("name"));
        if (gr != null) out.add(gr);
        JSONArray kids = node.optJSONArray("children");
        for (int i = 0; kids != null && i < kids.length(); i++) collect(kids.optJSONObject(i), out);
    }

    private static Group group(JSONObject standings, String name) {
        JSONArray entries = standings == null ? null : standings.optJSONArray("entries");
        if (entries == null || entries.length() == 0) return null;
        Group gr = new Group();
        gr.name = name;
        for (int k = 0; k < entries.length(); k++) {
            JSONObject e = entries.optJSONObject(k);
            Object team = e.opt("team");
            JSONObject t = team instanceof JSONObject ? (JSONObject) team : null;
            gr.ids.add(t != null ? t.optString("id") : e.optString("id"));
            gr.names.add(t != null ? t.optString("shortDisplayName", t.optString("displayName")) : e.optString("team"));
            JSONArray logos = t == null ? null : t.optJSONArray("logos");
            gr.logos.add(logos != null && logos.length() > 0 ? logos.optJSONObject(0).optString("href") : "");
            Map<String, String> v = new HashMap<>();
            JSONArray stats = e.optJSONArray("stats");
            for (int i = 0; stats != null && i < stats.length(); i++) {
                JSONObject x = stats.optJSONObject(i);
                v.put(x.optString("name"), x.optString("displayValue"));
            }
            gr.stats.add(v);
        }
        return gr;
    }

    private static String first(Map<String, String> v, String... names) {
        for (String n : names) {
            String x = v.get(n);
            if (x != null && !x.isEmpty()) return x;
        }
        return "";
    }

    /** "2026 AFC West Standings" → "AFC Oeste". */
    private static String groupName(String header) {
        String h = header.replaceAll("^[0-9-]+\\s*", "").replace(" Standings", "").trim();
        return h.replace(" West", " Oeste").replace(" East", " Este").replace(" North", " Norte")
                .replace(" South", " Sur").replace("Mexican Liga BBVA MX", "Liga MX");
    }

    /** The league's teams by id, for short names and logos in the table; fetched once. */
    private static Map<String, Sports.Team> teams(String path, Sports.League league) {
        synchronized (TEAMS) {
            Map<String, Sports.Team> m = TEAMS.get(path);
            if (m != null) return m;
        }
        Map<String, Sports.Team> m = new HashMap<>();
        if (league != null) for (Sports.Team t : Sports.teams(league)) m.put(t.id, t);
        synchronized (TEAMS) {
            if (!m.isEmpty()) TEAMS.put(path, m);
        }
        return m;
    }

    // ---------------------------------------------------------------- footer

    /** Your team's next game when this one is yours; else where it's played and on what channel. */
    private static void footer(Context c, JSONObject s, GameStats g, String leagueKey, String myId, long start) {
        boolean mine = !myId.isEmpty() && (g.left.id.equals(myId) || g.right.id.equals(myId));
        if (mine && !g.state.equals("pre")) {
            Sports.Game next = Sports.nextGame(Sports.teamLeague(c), myId);
            if (next != null && next.start > start) {
                boolean home = next.home.id.equals(myId);
                Sports.Team other = home ? next.away : next.home;
                g.footerLogo = other.logo;
                g.footer = (home ? "vs " : "en ") + other.shortName + "  ·  " + Sports.when(c, next.start)
                        + (next.broadcast.isEmpty() ? "" : "  ·  " + next.broadcast);
                return;
            }
        }
        // The stadium; the channels go in the "Dónde verlo" line (GameActivity).
        JSONObject info = s.optJSONObject("gameInfo");
        JSONObject venue = info == null ? null : info.optJSONObject("venue");
        g.footer = venue == null ? "" : venue.optString("fullName");
    }

    private static String translate(String s, String[][] map) {
        for (String[] m : map) if (m[0].equalsIgnoreCase(s)) return m[1];
        return s;
    }

    private static long parseDate(String iso) {
        try {
            String s = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            SimpleDateFormat f = new SimpleDateFormat(s.length() > 16 ? "yyyy-MM-dd'T'HH:mm:ss" : "yyyy-MM-dd'T'HH:mm", Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(s).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
