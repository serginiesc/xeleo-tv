package com.xeleo.tv;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Sports alerts over any app: kickoff, every score and the final of your team's game, as a small card in the top
 * right corner for a few seconds. Runs inside XeleoService (an accessibility overlay needs no extra permission).
 * Checks every 45 s while your team plays today, every 15 min otherwise; switched off in Quick settings > Deportes.
 * Also a heads-up 15 minutes before kickoff, and the programme reminders set in En vivo (Reminders), every 30 s.
 */
final class GameAlerts {

    private static final long LIVE_MS = 45_000, IDLE_MS = 15 * 60_000;
    private static final long SHOW_MS = 7_000;

    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("game-alerts");
    private final Handler bg;
    private final float density;

    /** What each of your teams' games looked like at the last look (by game id): state, our score, theirs. */
    private final java.util.Map<String, String[]> seen = new java.util.HashMap<>();
    /** The games whose 15-minute heads-up was shown. */
    private final java.util.Set<String> kickoffShown = new java.util.HashSet<>();
    private static final long KICKOFF_MS = 15 * 60_000L;
    private View card;

    GameAlerts(AccessibilityService service) {
        this.service = service;
        density = service.getResources().getDisplayMetrics().density;
        thread.start();
        bg = new Handler(thread.getLooper());
        bg.post(check);
        bg.post(reminders);
    }

    void stop() {
        bg.removeCallbacks(check);
        bg.removeCallbacks(reminders);
        thread.quitSafely();
        main.post(this::hide);
    }

    private final Runnable check = new Runnable() {
        @Override
        public void run() {
            long next = IDLE_MS;
            try {
                next = poll();
            } catch (Exception ignored) {
                // Network trouble: try again later.
            }
            bg.postDelayed(this, next);
        }
    };

    /** One look at your teams' games today; returns when to look again (soonest of them). */
    private long poll() {
        if (!Sports.enabled(service) || !Sports.alerts(service)) return IDLE_MS;
        long wait = IDLE_MS;
        for (Sports.MyTeam t : Sports.myTeams(service)) {
            Sports.League l = Sports.league(t.league);
            if (l == null) continue;
            for (Sports.Game g : Sports.withCups(l, t)) if (Sports.plays(g, t)) wait = Math.min(wait, look(t, g));
        }
        return wait;
    }

    /** One of your teams' games: alerts for what changed since the last look; returns when to look again. */
    private long look(Sports.MyTeam t, Sports.Game game) {
        Sports.Team me = game.side(t.abbr), them = game.other(t.abbr);
        String team = t.shortName.isEmpty() ? t.name : t.shortName;
        me.color = t.color;
        String[] last = seen.get(game.id);
        if (last != null) {
            String lastState = last[0], lastMine = last[1], lastTheirs = last[2];
            String score = me.abbr + "  " + me.score + " – " + them.score + "  " + them.abbr;
            if ("pre".equals(lastState) && game.live()) {
                show(game, me, "Empieza el partido", team + " vs " + them.shortName);
            } else if (game.done() && !"post".equals(lastState)) {
                show(game, me, me.winner ? "¡Ganaron " + article(team) + "!" : "Final", score);
            } else if (game.live() && !me.score.equals(lastMine)) {
                show(game, me, "¡Anotaron " + article(team) + "!", score + "  ·  " + game.detail);
            } else if (game.live() && !them.score.equals(lastTheirs)) {
                show(game, them, "Anotó " + them.shortName, score + "  ·  " + game.detail);
            }
        }
        // A heads-up a quarter of an hour before kickoff, once per game.
        long left = game.start - System.currentTimeMillis();
        if ("pre".equals(game.state) && left > 0 && left <= KICKOFF_MS && kickoffShown.add(game.id)) {
            show(game, me, "En " + Math.max(1, left / 60_000) + " min: " + team + " vs " + them.shortName,
                    game.broadcast.isEmpty() ? "Empieza a las " + Sports.when(service, game.start)
                            : "Por " + game.broadcast + "  ·  " + Sports.when(service, game.start));
        }
        seen.put(game.id, new String[]{game.state, me.score, them.score});
        long soon = game.start - System.currentTimeMillis();
        return game.live() || (soon > 0 && soon < 20 * 60_000) ? LIVE_MS : game.done() ? IDLE_MS : Math.min(IDLE_MS, Math.max(LIVE_MS, soon - 10 * 60_000));
    }

    private static String article(String team) {
        return Sports.isNation(team) ? team : "los " + team;
    }

    /** The card, unless Home is on screen (it already shows the game). */
    private void show(Sports.Game game, Sports.Team who, String title, String line) {
        if (XeleoService.homeOnScreen()) return;
        display(Logos.get(who.logo, 96), who.color, title, line);
    }

    /** A programme reminder from En vivo (Reminders), over any screen; called on the background thread. */
    private void remind(Reminders.Item it) {
        display(it.logo.isEmpty() ? null : Logos.get(it.logo, 96), 0xFF8AB4F8, "En 5 min: " + it.title,
                it.channel + "  ·  Pluto TV  ·  " + android.text.format.DateFormat.getTimeFormat(service)
                        .format(new java.util.Date(it.start)));
    }

    private final Runnable reminders = new Runnable() {
        @Override
        public void run() {
            try {
                for (Reminders.Item it : Reminders.due(service, System.currentTimeMillis())) remind(it);
            } catch (Exception ignored) {
            }
            bg.postDelayed(this, 30_000);
        }
    };

    private void display(Bitmap logo, int color, String title, String line) {
        main.post(() -> {
            hide();
            WindowManager wm = service.getSystemService(WindowManager.class);
            LinearLayout c = new LinearLayout(service);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(dp(14), dp(12), dp(20), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(0xFF131D23);   // opaque: whatever is behind must not show through the text
            bg.setStroke(dp(2), color | 0xFF000000);
            c.setBackground(bg);
            ImageView iv = new ImageView(service);
            iv.setImageBitmap(logo);
            c.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout texts = new LinearLayout(service);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(dp(12), 0, 0, 0);
            TextView t = new TextView(service);
            t.setText(title);
            t.setTextSize(16);
            t.setTextColor(0xFFFFFFFF);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            texts.addView(t);
            TextView s = new TextView(service);
            s.setText(line);
            s.setTextSize(13);
            s.setTextColor(0xFFC4C7C5);
            s.setPadding(0, dp(3), 0, 0);
            texts.addView(s);
            c.addView(texts);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(24);
            lp.y = dp(24);
            try {
                wm.addView(c, lp);
            } catch (RuntimeException e) {
                return;
            }
            card = c;
            c.setAlpha(0f);
            c.setTranslationX(dp(40));
            c.animate().alpha(1f).translationX(0).setDuration(250).start();
            main.postDelayed(() -> {
                if (card == c) c.animate().alpha(0f).setDuration(300).withEndAction(this::hide).start();
            }, SHOW_MS);
        });
    }

    private void hide() {
        if (card == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(card);
        } catch (RuntimeException ignored) {
        }
        card = null;
    }

    /** For a quick look over adb (ControlReceiver SPORTS_TEST): a sample card now. */
    void test() {
        bg.post(() -> {
            Sports.Game g = new Sports.Game();
            Sports.Team me = new Sports.Team();
            me.abbr = Sports.teamAbbr(service).isEmpty() ? "XEL" : Sports.teamAbbr(service);
            me.logo = Sports.teamLogo(service);
            me.color = Sports.teamColor(service);
            String team = Sports.teamShort(service).isEmpty() ? "XELEO" : Sports.teamShort(service);
            show(g, me, "¡Anotaron " + article(team) + "!", me.abbr + "  7 – 3  RIV  ·  2º  8:41");
        });
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
