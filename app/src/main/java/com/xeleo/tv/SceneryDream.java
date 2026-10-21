package com.xeleo.tv;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/**
 * Screensaver matching Home: the same time-of-day landscape with the real weather, a large clock with the date
 * and weather, and the song when something is playing. The block drifts to a new spot every minute and the
 * landscape follows the time of day, so nothing stays put long enough to mark the panel.
 */
public class SceneryDream extends DreamService {

    private static final long TICK_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private ImageView scenery;
    private PrecipView precip;
    private AmbientView ambient;
    private LinearLayout block;
    private TextView weather, game;
    private Scenery.Phase phase;
    private Scenery.Kind kind;
    private double lat, lon;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Weather.update(SceneryDream.this, weather);
            showGame();
            paintScenery();
            drift();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);
        float d = getResources().getDisplayMetrics().density;
        Typeface regular = getResources().getFont(R.font.outfit_regular);
        Typeface medium = getResources().getFont(R.font.outfit_medium);
        Typeface light = getResources().getFont(R.font.outfit_light);
        lat = Profile.lat(this);
        lon = Profile.lon(this);

        FrameLayout root = new FrameLayout(this);
        scenery = new ImageView(this);
        scenery.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(scenery, new FrameLayout.LayoutParams(-1, -1));
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        precip = new PrecipView(this);
        root.addView(precip, new FrameLayout.LayoutParams(-1, -1));

        block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(110);
        clock.setTypeface(light);
        clock.setTextColor(0xFFFFFFFF);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(14 * d, 0, 2 * d, 0x99000000);
        block.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setTextSize(20);
        date.setTypeface(regular);
        date.setTextColor(0xE6FFFFFF);
        date.setShadowLayer(8 * d, 0, 1 * d, 0x99000000);
        date.setPadding((int) (6 * d), 0, 0, 0);
        block.addView(date);
        weather = new TextView(this);
        weather.setTextSize(16);
        weather.setTypeface(regular);
        weather.setTextColor(0xCCFFFFFF);
        weather.setShadowLayer(8 * d, 0, 1 * d, 0x99000000);
        weather.setPadding((int) (6 * d), (int) (8 * d), 0, 0);
        weather.setVisibility(View.GONE);
        block.addView(weather);
        game = new TextView(this);
        game.setTextSize(18);
        game.setTypeface(medium);
        game.setTextColor(0xFFFFFFFF);
        game.setShadowLayer(8 * d, 0, 1 * d, 0x99000000);
        game.setPadding((int) (6 * d), (int) (10 * d), 0, 0);
        game.setVisibility(View.GONE);
        block.addView(game);
        NowPlayingView player = new NowPlayingView(this, false, regular, medium);
        player.hideControls();
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = (int) (22 * d);
        block.addView(player, plp);
        root.addView(block, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        Weather.update(this, weather);
        showGame();
        paintScenery();
        block.post(this::drift);
        handler.postDelayed(tick, TICK_MS);
        ambient.setRunning(true);
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        handler.removeCallbacks(tick);
        ambient.setRunning(false);
    }

    private void paintScenery() {
        Scenery.Phase p = Scenery.phaseNow(lat, lon);
        Scenery.Kind k = Weather.kind();
        precip.setKind(k);
        ambient.setScene(p, k);
        if (p == phase && k == kind) return;
        phase = p;
        kind = k;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        Bitmap bmp = Scenery.paint(p, k, w, h, false);
        scenery.setImageBitmap(bmp);
    }

    /** Your team's game today: kickoff time, live score or final. */
    private void showGame() {
        Sports.load(this, (today, mine, next) -> {
            String abbr = Sports.teamAbbr(this);
            if (mine == null || mine.side(abbr) == null) {
                game.setVisibility(View.GONE);
                return;
            }
            Sports.Team me = mine.side(abbr), them = mine.other(abbr);
            game.setText(mine.live() || mine.done()
                    ? me.abbr + "  " + me.score + " – " + them.score + "  " + them.abbr + "  ·  " + mine.detail
                    : "Hoy: " + Sports.teamShort(this) + " vs " + them.shortName + "  ·  " + Sports.when(this, mine.start));
            game.setVisibility(View.VISIBLE);
        });
    }

    /** Moves the clock block to a random spot that keeps it on screen, with a slow glide. */
    private void drift() {
        View parent = (View) block.getParent();
        int maxX = Math.max(0, parent.getWidth() - block.getWidth());
        int maxY = Math.max(0, parent.getHeight() - block.getHeight());
        float margin = 48 * getResources().getDisplayMetrics().density;
        float tx = margin + random.nextFloat() * Math.max(0, maxX - 2 * margin);
        float ty = margin + random.nextFloat() * Math.max(0, maxY - 2 * margin);
        block.animate().translationX(tx).translationY(ty).setDuration(2500).start();
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
