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
 */
final class GameAlerts {

    private static final long LIVE_MS = 45_000, IDLE_MS = 15 * 60_000;
    private static final long SHOW_MS = 7_000;

    private final AccessibilityService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("game-alerts");
    private final Handler bg;
    private final float density;

    private String gameId;
    private String lastState;
    private String lastMine, lastTheirs;
    private View card;

    GameAlerts(AccessibilityService service) {
        this.service = service;
        density = service.getResources().getDisplayMetrics().density;
        thread.start();
        bg = new Handler(thread.getLooper());
        bg.post(check);
    }

    void stop() {
        bg.removeCallbacks(check);
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

    /** One look at today's game; returns when to look again. */
    private long poll() {
        if (!Sports.enabled(service) || !Sports.alerts(service) || Sports.teamAbbr(service).isEmpty()) return IDLE_MS;
        Sports.League l = Sports.league(Sports.teamLeague(service));
        if (l == null) return IDLE_MS;
        String abbr = Sports.teamAbbr(service);
        Sports.Game game = null;
        for (Sports.Game g : Sports.today(l)) if (g.side(abbr) != null) game = g;
        if (game == null) return IDLE_MS;
        Sports.Team me = game.side(abbr), them = game.other(abbr);
        boolean first = !game.id.equals(gameId);
        if (!first) {
            String team = Sports.teamShort(service);
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
        gameId = game.id;
        lastState = game.state;
        lastMine = me.score;
        lastTheirs = them.score;
        long soon = game.start - System.currentTimeMillis();
        return game.live() || (soon > 0 && soon < 20 * 60_000) ? LIVE_MS : game.done() ? IDLE_MS : Math.min(IDLE_MS, Math.max(LIVE_MS, soon - 10 * 60_000));
    }

    private static String article(String team) {
        return Sports.NATIONS_NAMES.contains(team) ? team : "los " + team;
    }

    /** The card, unless Home is on screen (it already shows the game). */
    private void show(Sports.Game game, Sports.Team who, String title, String line) {
        if (XeleoService.homeOnScreen()) return;
        Bitmap logo = Logos.get(who.logo, 96);
        int color = who.color;
        main.post(() -> {
            hide();
            WindowManager wm = service.getSystemService(WindowManager.class);
            LinearLayout c = new LinearLayout(service);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setPadding(dp(14), dp(12), dp(20), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(18));
            bg.setColor(0xF0131D23);
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
