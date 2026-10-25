package com.xeleo.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game day on Home: your team's colour over the sky, a recent photo from its ESPN news on the right (Backdrop), and
 * in place of the greeting a block that changes through the day (Hero): in the morning a quiet line, from three
 * hours before a countdown, during the game the big score, and after a win "¡Ganaron!" until the next morning. A loss
 * or a tie puts Home back as usual.
 */
final class GameDay {

    enum Moment { NONE, MORNING, SOON, LIVE, WON }

    /** From this long before kickoff the countdown and the full photo show. */
    static final long SOON_MS = 3 * 3_600_000L;

    private GameDay() {}

    static Moment moment(Sports.Game g, String abbr) {
        if (g == null || g.side(abbr) == null) return Moment.NONE;
        if (g.live()) return Moment.LIVE;
        if (g.done()) return g.side(abbr).winner ? Moment.WON : Moment.NONE;
        return g.start - System.currentTimeMillis() > SOON_MS ? Moment.MORNING : Moment.SOON;
    }

    static boolean photoOn(Context c) {
        return c.getSharedPreferences("sports", Context.MODE_PRIVATE).getBoolean("photo", true);
    }

    /** "los Chiefs", but "Portugal" or "México" without an article. */
    static String withArticle(String team) {
        return Sports.isNation(team) ? team : "los " + team;
    }

    // ---------------------------------------------------------------- the last win, until the next morning

    /** A win kept after midnight, when the scoreboard no longer has it. */
    static final class Win {
        Sports.Game game;
        String abbr, shortName, logo, league, teamId;
        int color, alt;
    }

    private static SharedPreferences seen(Context c) {
        return c.getSharedPreferences("sports_seen", Context.MODE_PRIVATE);
    }

    static void rememberWin(Context c, Sports.Game g, Sports.MyTeam t) {
        if (t == null || g.side(t.abbr) == null) return;
        try {
            Sports.Team me = g.side(t.abbr), them = g.other(t.abbr);
            JSONObject o = new JSONObject().put("id", g.id).put("path", g.path).put("league", g.leagueKey)
                    .put("detail", g.detail).put("at", System.currentTimeMillis())
                    .put("abbr", t.abbr).put("short", t.shortName.isEmpty() ? t.name : t.shortName).put("logo", t.logo)
                    .put("color", t.color).put("alt", t.alt).put("teamId", t.id).put("score", me.score)
                    .put("them", them.abbr).put("themShort", them.shortName).put("themLogo", them.logo).put("themScore", them.score);
            seen(c).edit().putString("win", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /** The last win if it is still yesterday night's (until 10 a.m.), else null. */
    static Win lastWin(Context c) {
        try {
            JSONObject o = new JSONObject(seen(c).getString("win", "{}"));
            if (!o.has("at")) return null;
            Calendar until = Calendar.getInstance();
            until.setTimeInMillis(o.getLong("at"));
            until.add(Calendar.DAY_OF_YEAR, 1);
            until.set(Calendar.HOUR_OF_DAY, 10);
            until.set(Calendar.MINUTE, 0);
            if (System.currentTimeMillis() > until.getTimeInMillis()) return null;
            Win w = new Win();
            Sports.Game g = new Sports.Game();
            g.id = o.getString("id");
            g.path = o.optString("path");
            g.leagueKey = o.optString("league");
            g.state = "post";
            g.detail = o.optString("detail", "Final");
            g.home.abbr = o.getString("abbr");
            g.home.score = o.optString("score");
            g.home.logo = o.optString("logo");
            g.home.winner = true;
            g.away.abbr = o.optString("them");
            g.away.shortName = o.optString("themShort");
            g.away.logo = o.optString("themLogo");
            g.away.score = o.optString("themScore");
            w.game = g;
            w.abbr = g.home.abbr;
            w.shortName = o.optString("short");
            w.logo = g.home.logo;
            w.league = g.leagueKey;
            w.teamId = o.optString("teamId");
            w.color = o.optInt("color", 0xFF5E6874);
            w.alt = o.optInt("alt", 0xFFFFFFFF);
            return w;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- a sample over adb (ControlReceiver)

    /** "morning", "soon", "live" or "won" to see that moment with your team now; null for the real one. */
    static volatile String preview;
    /** The preview without the photo (only the colours), or with the poster instead of the news photo. */
    static volatile boolean previewNoPhoto, previewPoster;
    /** The league of the team to preview ("ligamx"), or null for the one Home follows. */
    static volatile String previewLeague;

    /** A made-up game of your team for the preview, against the rival of its next game when known. */
    static Sports.Game sample(String moment, Sports.MyTeam t, Sports.Game next) {
        Sports.Game g = new Sports.Game();
        g.id = "preview";
        g.leagueKey = t.league;
        g.path = Sports.league(t.league) == null ? "" : Sports.league(t.league).paths[0];
        g.home.abbr = t.abbr;
        g.home.name = t.name;
        g.home.shortName = t.shortName;
        g.home.logo = t.logo;
        Sports.Team rival = next != null && next.side(t.abbr) != null ? next.other(t.abbr) : null;
        g.away.abbr = rival != null ? rival.abbr : "RIV";
        g.away.shortName = rival != null ? rival.shortName : "Rival";
        g.away.logo = rival != null ? rival.logo : "";
        g.broadcast = next != null ? next.broadcast : "";
        long now = System.currentTimeMillis();
        switch (moment) {
            case "morning":
                g.start = now + 8 * 3_600_000L;
                break;
            case "soon":
                g.start = now + 2 * 3_600_000L + 45 * 60_000L;
                break;
            case "live":
                g.start = now - 3_600_000L;
                g.state = "in";
                g.detail = "nfl".equals(t.league) ? "2º  8:41" : "63'";
                g.home.score = "nfl".equals(t.league) ? "14" : "2";
                g.away.score = "nfl".equals(t.league) ? "10" : "1";
                break;
            default:
                g.start = now - 3 * 3_600_000L;
                g.state = "post";
                g.detail = "Final";
                g.home.score = "nfl".equals(t.league) ? "27" : "2";
                g.away.score = "nfl".equals(t.league) ? "20" : "1";
                g.home.winner = true;
        }
        return g;
    }

    // ---------------------------------------------------------------- the photo, from the team's news

    interface PhotoCallback {
        /**
         * big is ESPN's 1296×729 cut of the picture, small the one the article lists (if big doesn't load); both null
         * when no article fits.
         */
        void onPhoto(String big, String small);
    }

    private static final Map<String, String[]> PHOTO = new ConcurrentHashMap<>();
    private static final Map<String, Long> PHOTO_AT = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Headlines that are not for a celebration (ESPN's news are in English). */
    private static final String[] SAD = {"dead", "death", "dies", "died", "mourn", "funeral", "arrest", "charged",
            "suspend", "injur", "surgery", "torn", "out for", "lawsuit", "fired", "accus", "crash", "shooting",
            "told to leave", "kicked out", "stall", " on ir", "benched", "fined", "feud", "dispute",
            // Injury reports: "Evans, Bosa out vs. Atlanta", "exits with Achilles soreness"…
            "out vs", "ruled out", "won't play", "will miss", "to miss", "exits", "sorenes", "questionable",
            "doubtful", "achilles", "acl", "concussion", "hamstring", "ankle", "knee", "carted", "hurt",
            // Not about the team on the field: the league office, owners, betting, fantasy.
            "goodell", "commissioner", "owner", "meeting", "how to bet", "odds", "betting", "fantasy", "trade deadline"};
    private static final long FRESH_DAYS = 21;

    /**
     * A fitting photo of the team's ESPN news, looked up again every three hours: from the last three weeks, about
     * this team (one or two teams tagged, not a league-wide list), and not about deaths, arrests, injuries, fines or
     * a player sent out. On game day the newest story about the matchup (also tagged with the rival) wins, then one
     * about your team alone, then any. Calls back on the main thread, with nulls when no article fits.
     */
    static void photo(String leagueKey, String teamId, String rival, PhotoCallback cb) {
        String key = leagueKey + ":" + teamId + ":" + rival;
        String[] hit = PHOTO.get(key);
        Long at = PHOTO_AT.get(key);
        if (hit != null && at != null && System.currentTimeMillis() - at < 3 * 3_600_000L) {
            if (hit.length == 2) cb.onPhoto(hit[0], hit[1]);
            else cb.onPhoto(null, null);
            return;
        }
        Sports.League l = Sports.league(leagueKey);
        if (l == null || teamId.isEmpty()) return;
        new Thread(() -> {
            String[] found = find(l.paths[0], teamId, rival);
            PHOTO.put(key, found == null ? new String[0] : found);
            PHOTO_AT.put(key, System.currentTimeMillis());
            MAIN.post(() -> cb.onPhoto(found == null ? null : found[0], found == null ? null : found[1]));
        }, "gameday-photo").start();
    }

    // ---------------------------------------------------------------- the poster, when there is no photo

    interface StarCallback {
        /** The star's cut-out headshot (transparent PNG) and jersey number. */
        void onStar(String headshot, String jersey);
    }

    private static final Map<String, String[]> STAR = new ConcurrentHashMap<>();
    private static final Map<String, Long> STAR_AT = new ConcurrentHashMap<>();

    /**
     * The team's star, for the poster when the news have no photo: in the NFL the starting quarterback (ESPN's depth
     * chart), in soccer the number 10, else a forward, among the players ESPN has a headshot of. Other leagues have
     * none. Looked up again every 12 hours; calls back on the main thread only when there is one.
     */
    static void star(String leagueKey, String teamId, StarCallback cb) {
        String key = leagueKey + ":" + teamId;
        String[] hit = STAR.get(key);
        Long at = STAR_AT.get(key);
        if (hit != null && at != null && System.currentTimeMillis() - at < 12 * 3_600_000L) {
            if (hit.length == 2) cb.onStar(hit[0], hit[1]);
            return;
        }
        Sports.League l = Sports.league(leagueKey);
        if (l == null || teamId.isEmpty()) return;
        new Thread(() -> {
            String[] found = findStar(l, teamId);
            STAR.put(key, found == null ? new String[0] : found);
            STAR_AT.put(key, System.currentTimeMillis());
            if (found != null) MAIN.post(() -> cb.onStar(found[0], found[1]));
        }, "gameday-star").start();
    }

    private static String[] findStar(Sports.League l, String teamId) {
        String base = "https://site.api.espn.com/apis/site/v2/sports/" + l.paths[0] + "/teams/" + teamId;
        try {
            // Everyone on the team: the NFL groups them (offense, defense…), soccer lists them.
            JSONArray roster = Sports.get(base + "/roster").optJSONArray("athletes");
            java.util.List<JSONObject> players = new java.util.ArrayList<>();
            for (int i = 0; roster != null && i < roster.length(); i++) {
                JSONObject a = roster.getJSONObject(i);
                JSONArray items = a.optJSONArray("items");
                if (items == null) players.add(a);
                else for (int k = 0; k < items.length(); k++) players.add(items.getJSONObject(k));
            }
            JSONObject pick = null;
            if ("nfl".equals(l.key)) {
                String qb = "";
                JSONArray charts = Sports.get(base + "/depthcharts").optJSONArray("depthchart");
                for (int i = 0; charts != null && i < charts.length() && qb.isEmpty(); i++) {
                    JSONObject pos = charts.getJSONObject(i).optJSONObject("positions");
                    JSONObject q = pos == null ? null : pos.optJSONObject("qb");
                    JSONArray who = q == null ? null : q.optJSONArray("athletes");
                    if (who != null && who.length() > 0) qb = who.getJSONObject(0).optString("id");
                }
                for (JSONObject p : players) if (p.optString("id").equals(qb)) pick = p;
            } else if ("soccer".equals(l.sport)) {
                for (int pass = 0; pass < 3 && pick == null; pass++) {
                    for (JSONObject p : players) {
                        if (headshot(p).isEmpty()) continue;
                        JSONObject pos = p.optJSONObject("position");
                        String abbr = pos == null ? "" : pos.optString("abbreviation");
                        if (pass == 0 && "10".equals(p.optString("jersey"))
                                || pass == 1 && (abbr.equals("F") || abbr.equals("FW"))
                                || pass == 2) {
                            pick = p;
                            break;
                        }
                    }
                }
            }
            if (pick == null || headshot(pick).isEmpty()) return null;
            return new String[]{headshot(pick), pick.optString("jersey")};
        } catch (Exception e) {
            return null;
        }
    }

    private static String headshot(JSONObject player) {
        JSONObject h = player.optJSONObject("headshot");
        return h == null ? "" : h.optString("href");
    }

    private static String[] find(String path, String teamId, String rival) {
        String[] best = null;
        int bestScore = -1;
        try {
            JSONObject d = Sports.get("https://site.api.espn.com/apis/site/v2/sports/" + path + "/news?team=" + teamId + "&limit=25");
            JSONArray articles = d.optJSONArray("articles");
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now = System.currentTimeMillis();
            for (int i = 0; articles != null && i < articles.length(); i++) {
                JSONObject a = articles.getJSONObject(i);
                String published = a.optString("published");
                if (published.length() < 19) continue;
                if (now - iso.parse(published.substring(0, 19)).getTime() > FRESH_DAYS * 86_400_000L) continue;
                int teams = 0;
                boolean matchup = false;
                JSONArray cats = a.optJSONArray("categories");
                for (int k = 0; cats != null && k < cats.length(); k++) {
                    JSONObject cat = cats.getJSONObject(k);
                    if (!"team".equals(cat.optString("type"))) continue;
                    teams++;
                    if (!rival.isEmpty() && rival.equalsIgnoreCase(cat.optString("description"))) matchup = true;
                }
                if (teams < 1 || teams > 2) continue;
                // The matchup's story first, then one about your team alone; among equals, the newest (listed first).
                int score = matchup ? 2 : teams == 1 ? 1 : 0;
                if (score <= bestScore) continue;
                String text = (a.optString("headline") + " " + a.optString("description")).toLowerCase(Locale.ROOT);
                boolean sad = false;
                for (String w : SAD) if (text.contains(w)) sad = true;
                if (sad) continue;
                JSONArray images = a.optJSONArray("images");
                for (int k = 0; images != null && k < images.length(); k++) {
                    JSONObject img = images.getJSONObject(k);
                    String url = img.optString("url");
                    if (!"header".equals(img.optString("type")) || !url.contains("a.espncdn.com/photo/")) continue;
                    // ESPN keeps every cut of a photo; the wide one fits the screen.
                    String big = url.replaceFirst("_\\d+x\\d+_(16-9|3-2|4-3|1-1)\\.", "_1296x729_16-9.");
                    best = new String[]{big, url};
                    bestScore = score;
                    break;
                }
                if (bestScore == 2) break;   // nothing beats the newest matchup story
            }
        } catch (Exception ignored) {
            // No network or a changed answer: what was found so far, or just the colours.
        }
        return best;
    }

    // ---------------------------------------------------------------- what is drawn behind Home

    /**
     * The team's colour washed over the sky, strong on the left where the hero is, and the photo on the right, a bit
     * darker so white text reads over it, fading out to the left, above the app row and behind the tabs.
     */
    static final class Backdrop extends View {
        private int color = 0xFF5E6874;
        private float strength = 1f, photoAlpha = 1f;
        private Bitmap photo;
        /** Without a photo: the star's headshot in the team's colours, its number outlined behind (alt colour). */
        private Bitmap poster;
        private String number = "";
        private int alt = 0xFFFFFFFF;
        private Typeface face;
        private ColorMatrixColorFilter duotone;
        private boolean stadium;
        /** The team's crest for the stadium's facade, and how lit it is: 0 by day, 1 at night. */
        private Bitmap crest;
        private float glow;

        void setCrest(Bitmap b) {
            if (b == crest) return;
            crest = b;
            if (stadium) invalidate();
        }

        void setGlow(float g) {
            if (g == glow) return;
            glow = g;
            if (stadium) invalidate();
        }
        /** Where it fades out: the top of the app row. */
        private int edge;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        /** Fades drawn over a layer; without antialiasing, which would leave a sharp column of photo at its edge. */
        private final Paint mask = new Paint();
        private final ColorMatrixColorFilter grade;

        Backdrop(Context c) {
            super(c);
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(1.1f);
            ColorMatrix dark = new ColorMatrix();
            dark.setScale(0.78f, 0.78f, 0.78f, 1f);
            cm.postConcat(dark);
            grade = new ColorMatrixColorFilter(cm);
        }

        void set(int color, float strength, float photoAlpha) {
            if (color == this.color && strength == this.strength && photoAlpha == this.photoAlpha) return;
            if (color != this.color) duotone = null;
            this.color = color;
            this.strength = strength;
            this.photoAlpha = photoAlpha;
            invalidate();
        }

        void setPhoto(Bitmap b) {
            if (b == photo && poster == null && !stadium) return;
            photo = b;
            poster = null;
            stadium = false;
            invalidate();
        }

        /** Without photo or poster: a stadium drawn in the team's colours (alt for its lights). */
        void setStadium(int alt) {
            if (stadium && photo == null && poster == null && alt == this.alt) return;
            photo = null;
            poster = null;
            stadium = true;
            this.alt = alt;
            invalidate();
        }

        void setPoster(Bitmap b, String number, int alt, Typeface face) {
            if (b == poster && photo == null) return;
            photo = null;
            stadium = false;
            poster = b;
            this.number = number == null ? "" : number;
            this.alt = alt;
            this.face = face;
            invalidate();
        }

        /** Gray turned into the team's colour: shadows deep, light almost white. */
        private ColorMatrixColorFilter duotone() {
            float[] dark = new float[3], span = new float[3];
            int[] c = {(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF};
            for (int i = 0; i < 3; i++) {
                dark[i] = c[i] * 0.27f;
                span[i] = (c[i] + (255 - c[i]) * 0.82f - dark[i]) / 255f;
            }
            return new ColorMatrixColorFilter(new float[]{
                    0.299f * span[0], 0.587f * span[0], 0.114f * span[0], 0, dark[0],
                    0.299f * span[1], 0.587f * span[1], 0.114f * span[1], 0, dark[1],
                    0.299f * span[2], 0.587f * span[2], 0.114f * span[2], 0, dark[2],
                    0, 0, 0, 1, 0});
        }

        void setEdge(int y) {
            if (y == edge) return;
            edge = y;
            invalidate();
        }

        private int tint(float alpha) {
            return ((int) Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;
            float b = edge > 0 ? edge : h * 0.65f;
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

            // The colour: left to right, light behind the tabs and the clock, gone at the app row.
            int layer = c.saveLayer(0, 0, w, b, null);
            paint.setShader(new LinearGradient(0, 0, w, 0, new int[]{tint(215 * strength), tint(64 * strength),
                    tint(64 * strength)}, new float[]{0f, 0.82f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, b, paint);
            mask.setShader(new LinearGradient(0, 0, 0, b, new int[]{0x4D000000, 0x4D000000, 0xFF000000, 0xFF000000, 0},
                    new float[]{0f, 0.2f, 0.34f, 0.86f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, b, mask);
            c.restoreToCount(layer);

            if (photo != null && photoAlpha > 0) {
                float ph = b * 1.08f, pw = photo.getWidth() * ph / photo.getHeight(), x0 = w - pw;
                float k = ph / 760f;   // the mock-up's units
                layer = c.saveLayer(x0, 0, w, ph, null);
                paint.setShader(null);
                paint.setColorFilter(grade);
                paint.setAlpha((int) (255 * photoAlpha));
                c.drawBitmap(photo, null, new RectF(x0, 0, w, ph), paint);
                paint.setColorFilter(null);
                paint.setAlpha(255);
                // Shade under the clock, inside the photo so it fades in from the left with it.
                paint.setShader(new LinearGradient(0, 0, 0, 320 * k, (int) (140 * photoAlpha) << 24, 0, Shader.TileMode.CLAMP));
                c.drawRect(x0, 0, w, 320 * k, paint);
                paint.setShader(null);
                // In from the left, out at the bottom.
                mask.setShader(new LinearGradient(x0 + 40 * k, 0, x0 + 460 * k, 0, 0, 0xFF000000, Shader.TileMode.CLAMP));
                c.drawRect(x0 - 2, -2, w + 2, ph + 2, mask);
                mask.setShader(new LinearGradient(0, ph - 170 * k, 0, ph, 0xFF000000, 0, Shader.TileMode.CLAMP));
                c.drawRect(x0 - 2, -2, w + 2, ph + 2, mask);
                // Its top left corner (stadium ads, the scoreboard) sits behind the tabs: out.
                mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                float cx = x0 + 200 * k, cy = -30 * k;
                RadialGradient corner = new RadialGradient(cx, cy, 560 * k, new int[]{0xFF000000, 0xFF000000, 0},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                Matrix squash = new Matrix();
                squash.setScale(1f, 0.58f, cx, cy);
                corner.setLocalMatrix(squash);
                mask.setShader(corner);
                c.drawRect(x0 - 2, -2, w + 2, ph + 2, mask);
                c.restoreToCount(layer);
                // A veil of the colour where the photo starts.
                paint.setShader(new LinearGradient(x0, 0, x0 + 700 * k, 0, new int[]{tint(0), tint(110 * photoAlpha), tint(0)},
                        new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(x0, 0, x0 + 700 * k, b, paint);
            } else if (poster != null && photoAlpha > 0) {
                drawPoster(c, w, h, b);
            } else if (stadium && photoAlpha > 0) {
                drawStadium(c, w, h, b);
            }
            paint.setShader(null);
            mask.setShader(null);
        }

        /**
         * A stadium drawn in the team's colours on the right, lit from inside: its arched facade with ribs and lit
         * windows, four floodlights whose beams cross the sky, a few sparks over it. Needs no photo.
         */
        private void drawStadium(Canvas c, int w, int h, float b) {
            float s = h / 1080f;
            float x1 = w * 0.5f, x2 = w * 0.985f, cx = (x1 + x2) / 2, top = b - 150 * s, crown = b - 280 * s;
            float ctrl = 2 * crown - top;   // the quadratic's control point that puts the arc's peak at crown
            int light = lum(alt) < 110 ? 0xFFFFFFFF : alt | 0xFF000000;
            // At night the facade goes darker and everything that shines, brighter.
            int facade = mix(color, 0xFF000000, 0.35f + 0.2f * glow), deep = mix(color, 0xFF000000, 0.7f);
            int rim = mix(color, 0xFFFFFFFF, 0.5f);
            int layer = c.saveLayerAlpha(0, 0, w, b, (int) (255 * photoAlpha));

            // The light over the pitch, spilling out of the stadium.
            float halo = 560 * s * (1 + 0.3f * glow);
            paint.setShader(new RadialGradient(cx, top, halo, alpha(light, (int) (130 + 110 * glow)), alpha(light, 0),
                    Shader.TileMode.CLAMP));
            c.drawRect(x1 - 300 * s, top - halo, w, b, paint);

            // Floodlights: a pole on the arc, a lamp, and a soft beam up and outwards.
            float[] at = {0.14f, 0.36f, 0.64f, 0.86f};
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            for (float u : at) {
                float bx = x1 + (x2 - x1) * u, by = top + 2 * u * (1 - u) * (ctrl - top), hy = by - 170 * s;
                // Short enough to fade out before the clock and the tabs.
                double tilt = (u - 0.5f) * 1.1, spread = 0.1, len = 340 * s;
                android.graphics.Path beam = new android.graphics.Path();
                beam.moveTo(bx, hy);
                beam.lineTo(bx + (float) (Math.sin(tilt - spread) * len), hy - (float) (Math.cos(tilt - spread) * len));
                beam.lineTo(bx + (float) (Math.sin(tilt + spread) * len), hy - (float) (Math.cos(tilt + spread) * len));
                beam.close();
                paint.setShader(new LinearGradient(bx, hy, bx + (float) (Math.sin(tilt) * len), hy - (float) (Math.cos(tilt) * len),
                        alpha(light, (int) (80 + 90 * glow)), alpha(light, 0), Shader.TileMode.CLAMP));
                c.drawPath(beam, paint);
                paint.setShader(null);
                line.setStrokeWidth(4 * s);
                line.setColor(deep);
                c.drawLine(bx, by, bx, hy, line);
                paint.setColor(light);
                c.drawRoundRect(new RectF(bx - 20 * s, hy - 9 * s, bx + 20 * s, hy + 7 * s), 3 * s, 3 * s, paint);
            }

            // The facade: an arch, darker at the foot.
            android.graphics.Path body = new android.graphics.Path();
            body.moveTo(x1, b);
            body.lineTo(x1, top);
            body.quadTo(cx, ctrl, x2, top);
            body.lineTo(x2, b);
            body.close();
            paint.setShader(new LinearGradient(0, crown, 0, b, facade, deep, Shader.TileMode.CLAMP));
            c.drawPath(body, paint);
            paint.setShader(null);
            // Ribs, a row of lit windows and the roof's edge.
            java.util.Random rnd = new java.util.Random(7);
            line.setStrokeWidth(3 * s);
            line.setColor(alpha(rim, 70));
            float step = 0.032f;
            for (float u = step; u < 1; u += step) {
                float x = x1 + (x2 - x1) * u, y = top + 2 * u * (1 - u) * (ctrl - top);
                c.drawLine(x, y + 16 * s, x, b, line);
                if (rnd.nextFloat() < 0.75f && u + step < 1) {
                    float wx = x + 5 * s, ww = (x2 - x1) * step - 10 * s;
                    paint.setColor(alpha(light, (int) ((60 + rnd.nextInt(110)) * (1 + 0.6f * glow))));
                    c.drawRect(wx, b - 84 * s, wx + ww, b - 66 * s, paint);
                }
            }
            android.graphics.Path roof = new android.graphics.Path();
            roof.moveTo(x1, top);
            roof.quadTo(cx, ctrl, x2, top);
            line.setStrokeWidth(12 * s);
            line.setColor(glow > 0 ? mix(rim, light, 0.5f * glow) : rim);
            if (glow > 0) line.setShadowLayer(22 * s * glow, 0, 0, alpha(light, 220));   // the roof lit at night
            c.drawPath(roof, line);
            line.clearShadowLayer();
            roof.offset(0, 30 * s);
            line.setStrokeWidth(4 * s);
            line.setColor(alpha(rim, 110));
            c.drawPath(roof, line);

            // The crest in the middle of the facade, on a disc that hides the ribs.
            if (crest != null) {
                float r = 62 * s, ccy = b - 165 * s;
                paint.setColor(deep);
                c.drawCircle(cx, ccy, r + 10 * s, paint);
                line.setStrokeWidth(4 * s);
                line.setColor(rim);
                if (glow > 0) line.setShadowLayer(16 * s * glow, 0, 0, alpha(light, 200));
                c.drawCircle(cx, ccy, r + 10 * s, line);
                line.clearShadowLayer();
                float k = Math.min(2 * r / crest.getWidth(), 2 * r / crest.getHeight());
                float cw = crest.getWidth() * k, ch = crest.getHeight() * k;
                c.drawBitmap(crest, null, new RectF(cx - cw / 2, ccy - ch / 2, cx + cw / 2, ccy + ch / 2), paint);
            }

            // Sparks over the stadium.
            for (int i = 0; i < 16; i++) {
                float x = x1 + rnd.nextFloat() * (x2 - x1), y = crown - (60 + rnd.nextFloat() * 300) * s;
                paint.setColor(alpha(light, 90 + rnd.nextInt(120)));
                c.drawCircle(x, y, (2 + rnd.nextFloat() * 3) * s, paint);
            }
            c.restoreToCount(layer);
        }

        private static int lum(int c) {
            return (299 * ((c >> 16) & 0xFF) + 587 * ((c >> 8) & 0xFF) + 114 * (c & 0xFF)) / 1000;
        }

        private static int alpha(int c, int a) {
            return (Math.max(0, Math.min(255, a)) << 24) | (c & 0x00FFFFFF);
        }

        private static int mix(int a, int b, float t) {
            int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
            int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
            int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
            return 0xFF000000 | (r << 16) | (g << 8) | bl;
        }

        /** The mock-up's poster: a glow, the number outlined, the player standing on the app row. */
        private void drawPoster(Canvas c, int w, int h, float b) {
            float s = h / 1080f, ph = 460 * s, pw = poster.getWidth() * ph / poster.getHeight();
            float cx = w * 0.672f, left = cx - pw / 2;
            int glow = ((int) (80 * photoAlpha) << 24) | (alt & 0x00FFFFFF);
            paint.setShader(new RadialGradient(cx, b - 200 * s, 380 * s, glow, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, b - 200 * s, 380 * s, paint);
            paint.setShader(null);
            if (!number.isEmpty()) {
                Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
                num.setStyle(Paint.Style.STROKE);
                num.setStrokeWidth(5 * s);
                num.setTextSize(520 * s);
                num.setTextAlign(Paint.Align.CENTER);
                if (face != null) num.setTypeface(face);
                num.setFontVariationSettings("'wght' 700");
                // The second colour, unless it is too dark to see over the first (then white).
                int lum = (299 * ((alt >> 16) & 0xFF) + 587 * ((alt >> 8) & 0xFF) + 114 * (alt & 0xFF)) / 1000;
                num.setColor(((int) (140 * photoAlpha) << 24) | (lum < 110 ? 0xFFFFFF : alt & 0x00FFFFFF));
                Paint.FontMetrics fm = num.getFontMetrics();
                c.drawText(number, cx, b - 300 * s - (fm.ascent + fm.descent) / 2, num);
            }
            if (duotone == null) duotone = duotone();
            int layer = c.saveLayer(left, b - ph, left + pw, b, null);
            RectF at = new RectF(left, b - ph, left + pw, b);
            paint.setAlpha((int) (255 * photoAlpha));
            c.drawBitmap(poster, null, at, paint);
            // Almost all duotone: an old headshot may still wear another club's shirt.
            paint.setColorFilter(duotone);
            paint.setAlpha((int) (235 * photoAlpha));
            c.drawBitmap(poster, null, at, paint);
            paint.setColorFilter(null);
            paint.setAlpha(255);
            mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mask.setShader(new LinearGradient(0, b - 180 * s, 0, b, 0xFF000000, 0, Shader.TileMode.CLAMP));
            c.drawRect(at, mask);
            c.restoreToCount(layer);
        }
    }

    // ---------------------------------------------------------------- the block in place of the greeting

    /** Left-aligned, over the colour; OK opens the game panel. */
    static final class Hero extends LinearLayout {
        private static final int GOLD = 0xFFFFB81C;
        private final float density;
        private final Typeface regular, medium, light;
        private TextView note;

        Hero(Context c, Typeface regular, Typeface medium, Typeface light) {
            super(c);
            this.regular = regular;
            this.medium = medium;
            this.light = light;
            density = c.getResources().getDisplayMetrics().density;
            setOrientation(VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(dp(16), dp(10), dp(22), dp(12));
            setOnFocusChangeListener((v, f) -> {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(18));
                bg.setColor(f ? 0x26FFFFFF : 0);
                if (f) bg.setStroke(dp(2), 0x8CFFFFFF);
                setBackground(bg);
            });
        }

        /** Builds the block for this moment; abbr is your team in game. */
        void show(Moment m, Sports.Game g, String abbr, String teamShort, String logo) {
            removeAllViews();
            note = null;
            Sports.Team me = g.side(abbr), them = g.other(abbr);
            String when = Sports.when(getContext(), g.start) + (g.broadcast.isEmpty() ? "" : "  ·  " + g.broadcast);
            boolean nation = Sports.isNation(teamShort);
            switch (m) {
                case MORNING: {
                    LinearLayout row = row();
                    row.addView(logo(logo, 42));
                    TextView t = text(24, 0xFFFFFFFF, medium);
                    t.setText((nation ? "Hoy juega " : "Hoy juegan ") + withArticle(teamShort));
                    t.setPadding(dp(12), 0, 0, 0);
                    row.addView(t);
                    addView(row);
                    TextView line = text(14, 0xE6FFFFFF, regular);
                    // A small countdown, whole hours only ("faltan 9 h"); the big one comes three hours before.
                    long left = (g.start - System.currentTimeMillis()) / 60_000;
                    String cd = left <= 0 ? "" : left >= 60 ? "faltan " + left / 60 + " h" : "faltan " + left + " min";
                    line.setText("vs " + them.shortName + "  ·  " + when + (cd.isEmpty() ? "" : "  ·  " + cd));
                    line.setPadding(dp(54), dp(4), 0, 0);
                    addView(line);
                    break;
                }
                case SOON: {
                    LinearLayout row = row();
                    row.addView(logo(logo, 56));
                    LinearLayout names = new LinearLayout(getContext());
                    names.setOrientation(VERTICAL);
                    names.setPadding(dp(14), 0, 0, 0);
                    TextView over = text(15, 0xDDFFFFFF, medium);
                    over.setText(nation ? "HOY JUEGA" : "HOY JUEGAN LOS");
                    over.setLetterSpacing(0.04f);
                    names.addView(over);
                    TextView name = text(40, 0xFFFFFFFF, medium);
                    name.setText(teamShort);
                    name.setFontVariationSettings("'wght' 700");
                    name.setShadowLayer(dp(10), 0, dp(2), 0x80000000);
                    names.addView(name);
                    row.addView(names);
                    addView(row);
                    TextView line = text(15, 0xE6FFFFFF, medium);
                    line.setText("vs " + them.shortName + "  ·  " + when);
                    line.setPadding(0, dp(8), 0, 0);
                    addView(line);
                    LinearLayout box = new LinearLayout(getContext());
                    box.setOrientation(VERTICAL);
                    box.setPadding(dp(16), dp(6), dp(16), dp(7));
                    box.setMinimumWidth(dp(275));
                    GradientDrawable bg = new GradientDrawable();
                    bg.setCornerRadius(dp(15));
                    bg.setColor(0x5A000000);
                    box.setBackground(bg);
                    TextView label = text(11, GOLD, medium);
                    label.setText("FALTAN");
                    label.setLetterSpacing(0.1f);
                    box.addView(label);
                    TextView left = text(24, 0xFFFFFFFF, medium);
                    String cd = Sports.countdown(g.start);
                    left.setText(cd.isEmpty() ? "Por empezar" : cd.replace("faltan ", ""));
                    left.setPadding(0, dp(4), 0, 0);
                    box.addView(left);
                    LayoutParams blp = new LayoutParams(-2, -2);
                    blp.topMargin = dp(8);
                    addView(box, blp);
                    break;
                }
                default: {   // LIVE and WON: the score
                    boolean live = m == Moment.LIVE;
                    LinearLayout top = row();
                    TextView pill = text(13, live ? 0xFFFFFFFF : 0xFF3C0412, medium);
                    pill.setText(live ? "●  EN VIVO" : "¡GANARON!");
                    pill.setFontVariationSettings(live ? "'wght' 600" : "'wght' 700");
                    pill.setGravity(Gravity.CENTER);
                    pill.setShadowLayer(0, 0, 0, 0);
                    pill.setPadding(dp(14), 0, dp(14), 0);
                    GradientDrawable pbg = new GradientDrawable();
                    pbg.setCornerRadius(dp(12));
                    pbg.setColor(live ? 0xF0E53935 : 0xF5000000 | (GOLD & 0xFFFFFF));
                    pill.setBackground(pbg);
                    top.addView(pill, new LayoutParams(-2, dp(24)));
                    TextView state = text(14, 0xDDFFFFFF, medium);
                    state.setText(g.detail.isEmpty() ? (live ? "En juego" : "Final") : g.detail);
                    state.setPadding(dp(12), 0, 0, 0);
                    top.addView(state);
                    addView(top);
                    LinearLayout score = row();
                    score.addView(side(me, logo, 64));
                    TextView mine = text(72, 0xFFFFFFFF, medium);
                    mine.setText(me.score);
                    mine.setFontVariationSettings("'wght' 700");
                    mine.setShadowLayer(dp(10), 0, dp(2), 0x80000000);
                    mine.setPadding(dp(16), 0, 0, 0);
                    score.addView(mine);
                    TextView dash = text(44, 0xCCFFFFFF, light);
                    dash.setText("–");
                    dash.setPadding(dp(18), 0, dp(18), 0);
                    score.addView(dash);
                    TextView theirs = text(72, 0xD9FFFFFF, light);
                    theirs.setText(them.score);
                    theirs.setShadowLayer(dp(10), 0, dp(2), 0x80000000);
                    theirs.setPadding(0, 0, dp(18), 0);
                    score.addView(theirs);
                    score.addView(side(them, them.logo, 58));
                    LayoutParams slp = new LayoutParams(-2, -2);
                    slp.topMargin = dp(10);
                    addView(score, slp);
                    note = text(13, 0xCCFFFFFF, regular);
                    note.setText(live ? (g.broadcast.isEmpty() ? "" : g.broadcast + "  ·  ") + "OK: panel del partido"
                            : "OK: panel del partido");
                    note.setPadding(0, dp(10), 0, 0);
                    addView(note);
                }
            }
        }

        /** The line under the score (after a win: the next game, once known). */
        void setNote(String s) {
            if (note != null && !s.isEmpty()) note.setText(s);
        }

        private View side(Sports.Team t, String logo, int size) {
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.addView(logo(logo, size));
            TextView abbr = text(13, 0xDDFFFFFF, medium);
            abbr.setText(t.abbr);
            abbr.setGravity(Gravity.CENTER);   // the column gives it its whole width
            abbr.setPadding(0, dp(4), 0, 0);
            col.addView(abbr);
            return col;
        }

        private ImageView logo(String url, int size) {
            ImageView iv = new ImageView(getContext());
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setLayoutParams(new LayoutParams(dp(size), dp(size)));
            Logos.into(iv, url, 128);
            return iv;
        }

        private LinearLayout row() {
            LinearLayout r = new LinearLayout(getContext());
            r.setGravity(Gravity.CENTER_VERTICAL);
            return r;
        }

        private TextView text(float sp, int color, Typeface tf) {
            TextView t = new TextView(getContext());
            t.setTextSize(sp);
            t.setTextColor(color);
            t.setTypeface(tf);
            t.setIncludeFontPadding(false);
            t.setShadowLayer(dp(6), 0, dp(1), 0x80000000);
            return t;
        }

        private int dp(float v) {
            return Math.round(v * density);
        }
    }
}
