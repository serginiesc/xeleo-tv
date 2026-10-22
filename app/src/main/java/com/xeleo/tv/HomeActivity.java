package com.xeleo.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * XELEO TV's home screen: a painted landscape that follows the time of day, a pill menu and
 * a large clock at the top, a greeting in the middle, one row of app cards, a bar of glass buttons and a line with
 * the city and the weather. No recommendations: every card is an app already on the TV.
 */
public class HomeActivity extends Activity {

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xCCFFFFFF;
    private static final int GLASS = 0x33FFFFFF;
    private static final int GLASS_EDGE = 0x40FFFFFF;
    private static final int FOCUS_BG = 0xF2FFFFFF;
    private static final int FOCUS_TEXT = 0xFF15171C;

    private float density;
    private Typeface regular, medium, light;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView backA, backB;
    private boolean showingA = true;
    private Scenery.Phase shownPhase;
    private Scenery.Kind shownKind;
    private PrecipView precip;
    private AmbientView ambient;
    /** null follows the clock; otherwise the phase picked with the Fondo button. */
    private Scenery.Phase pinnedPhase;
    private double lat, lon;

    private TextView greeting, weather, phaseLabel, wallpaperButton, brandName;
    private LinearLayout appRow;
    private FrameLayout appsOverlay;
    private GridLayout appsGrid;
    private TextView appsHint;
    /** The En vivo tab, shown only while it is on (Quick settings > En vivo). */
    private TextView livePill;
    private View firstCard;
    private LinearLayout recentsRow;
    private TextView tagline;
    private SportsStripView sports;
    private View teamTint;
    private ImageView teamLogo;
    private ConfettiView confetti;
    /** Your team's game today and its next one (Sports mode). */
    private Sports.Game myGame, nextGame;
    private final List<App> apps = new ArrayList<>();
    /** The apps on Home, in order (AppOrder); the rest are only in Todas las apps. */
    private final List<App> home = new ArrayList<>();
    /** The card being moved (hold OK on a card in the row), or null. */
    private View moving;
    /** False until the release of the long press that started moving, which must not put the card down. */
    private boolean movingArmed;
    /** True from a long press in Todas las apps until OK is released, so that release doesn't open the app. */
    private boolean swallowOk;
    /** The snake's colour on the focused app: white, or your team's on game day. */
    private int ringColor = Color.WHITE;

    /** Fetches the weather as soon as the network is up: at boot Home starts before Wi-Fi connects. */
    private final android.net.ConnectivityManager.NetworkCallback networkUp =
            new android.net.ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    handler.post(() -> {
                        Weather.networkAvailable();
                        Weather.update(HomeActivity.this, weather);
                    });
                }
            };

    /** ControlReceiver's weather preview: repaint now instead of on the next minute. */
    private final BroadcastReceiver refresh = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (i.getBooleanExtra("all", false)) {   // settings restored: build Home again from them
                recreate();
                return;
            }
            shownKind = null;
            updateScenery(true);
        }
    };

    static final class App {
        final String pkg, label;
        final Intent launch;
        final Drawable banner;
        final boolean realBanner;

        App(String pkg, String label, Intent launch, Drawable banner, boolean realBanner) {
            this.pkg = pkg;
            this.label = label;
            this.launch = launch;
            this.banner = banner;
            this.realBanner = realBanner;
        }
    }

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            refreshSports();
            Weather.update(HomeActivity.this, weather);
            updateScenery(true);
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sports.init(this);
        density = getResources().getDisplayMetrics().density;
        regular = getResources().getFont(R.font.outfit_regular);
        medium = getResources().getFont(R.font.outfit_medium);
        light = getResources().getFont(R.font.outfit_light);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        lat = Profile.lat(this);
        lon = Profile.lon(this);

        FrameLayout root = new FrameLayout(this);
        backA = background();
        backB = background();
        backB.setAlpha(0f);
        root.addView(backA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(backB, new FrameLayout.LayoutParams(-1, -1));
        ambient = new AmbientView(this);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        precip = new PrecipView(this);
        root.addView(precip, new FrameLayout.LayoutParams(-1, -1));
        // Game day: a wash of your team's colour over the sky.
        teamTint = new View(this);
        teamTint.setVisibility(View.GONE);
        root.addView(teamTint, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setPadding(0, dp(26), 0, dp(16));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(topBar());

        teamLogo = new ImageView(this);
        teamLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        teamLogo.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlogo = new LinearLayout.LayoutParams(dp(56), dp(56));
        tlogo.gravity = Gravity.CENTER_HORIZONTAL;
        tlogo.topMargin = dp(6);
        tlogo.bottomMargin = -dp(20);
        page.addView(teamLogo, tlogo);

        greeting = text(46, TEXT, medium);
        greeting.setGravity(Gravity.CENTER);
        greeting.setShadowLayer(dp(12), 0, dp(2), 0x80000000);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2);
        glp.topMargin = dp(34);
        page.addView(greeting, glp);
        tagline = text(16, TEXT_DIM, regular);
        tagline.setText("Relájate  ·  Mira  ·  Explora");
        tagline.setGravity(Gravity.CENTER);
        tagline.setLetterSpacing(0.06f);
        tagline.setShadowLayer(dp(8), 0, dp(1), 0x80000000);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = dp(8);
        page.addView(tagline, tlp);

        sports = new SportsStripView(this, regular, medium);
        LinearLayout.LayoutParams sslp = new LinearLayout.LayoutParams(-1, -2);
        sslp.topMargin = dp(10);
        page.addView(sports, sslp);

        recentsRow = new LinearLayout(this);
        recentsRow.setGravity(Gravity.CENTER);
        recentsRow.setClipChildren(false);
        LinearLayout.LayoutParams rclp = new LinearLayout.LayoutParams(-1, -2);
        rclp.topMargin = dp(16);
        page.addView(recentsRow, rclp);

        NowPlayingView player = new NowPlayingView(this, false, regular, medium);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        plp.topMargin = dp(14);
        page.addView(player, plp);

        View spacer = new View(this);
        page.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView rowScroll = new HorizontalScrollView(this);
        rowScroll.setHorizontalScrollBarEnabled(false);
        rowScroll.setClipChildren(false);
        rowScroll.setClipToPadding(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);   // the focused card grows into the padding; don't cut its frame
        appRow.setPadding(dp(36 + 4), dp(12), dp(36 + 4), dp(12));
        rowScroll.addView(appRow);
        // Full width (the page has no side padding), so cards scroll to the screen edge.
        page.addView(rowScroll, new LinearLayout.LayoutParams(-1, -2));

        page.addView(buttonBar());
        page.addView(footer());

        appsOverlay = appsOverlay();
        appsOverlay.setVisibility(View.GONE);
        root.addView(appsOverlay, new FrameLayout.LayoutParams(-1, -1));
        confetti = new ConfettiView(this);
        root.addView(confetti, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
        updateScenery(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(refresh, new IntentFilter(ControlReceiver.REFRESH), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refresh, new IntentFilter(ControlReceiver.REFRESH));
        }
        getSystemService(android.net.ConnectivityManager.class).registerDefaultNetworkCallback(networkUp);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(refresh);
        getSystemService(android.net.ConnectivityManager.class).unregisterNetworkCallback(networkUp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        buildRow();
        buildRecents();
        buildGrid();
        updateGreeting();
        refreshSports();
        brandName.setText(Profile.tvName(this));   // may have changed in Personalizar
        livePill.setVisibility(Live.enabled(this) ? View.VISIBLE : View.GONE);
        Live.prefetch(this);   // so En vivo opens with the guide already there
        lat = Profile.lat(this);
        lon = Profile.lon(this);
        updateScenery(false);
        Weather.update(this, weather);
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, 60_000);
        ambient.setRunning(true);
        if (getCurrentFocus() == null && firstCard != null) {
            View card = firstCard;
            card.post(card::requestFocus);   // after layout, so the card grows from its centre
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        ambient.setRunning(false);
        if (moving != null) endMove();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeApps();
        if (firstCard != null) firstCard.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (moving != null) endMove();
        else if (appsOverlay.getVisibility() == View.VISIBLE) closeApps();
        else if (firstCard != null) firstCard.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS) {
            openQuickSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------------------------------------------------------- top

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.TOP);
        bar.setPadding(dp(36), 0, dp(36), 0);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(new SparkleView(this), new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView name = text(19, TEXT, medium);
        name.setText(Profile.tvName(this));
        brandName = name;
        name.setPadding(dp(8), 0, dp(18), 0);
        name.setShadowLayer(dp(6), 0, 0, 0x80000000);
        brand.addView(name);

        LinearLayout pills = new LinearLayout(this);
        pills.setGravity(Gravity.CENTER_VERTICAL);
        pills.setPadding(dp(4), dp(4), dp(4), dp(4));
        pills.setBackground(glass(dp(22)));
        pills.addView(pill("Inicio", true, v -> { closeApps(); if (firstCard != null) firstCard.requestFocus(); }));
        pills.addView(pill("Apps", false, v -> openApps()));
        livePill = pill("En vivo", false, v -> {
            startActivity(new Intent(this, LiveActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
        pills.addView(livePill);
        pills.addView(pill("Gemini", false, v -> openAssistant()));
        pills.addView(pill("Ajustes", false, v -> openQuickSettings()));
        brand.addView(pills);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1f));

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(58);
        clock.setTypeface(light);
        clock.setTextColor(TEXT);
        clock.setIncludeFontPadding(false);
        clock.setShadowLayer(dp(10), 0, dp(2), 0x80000000);
        time.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, d MMM");
        date.setFormat24Hour("EEE, d MMM");
        date.setTextSize(15);
        date.setTypeface(regular);
        date.setTextColor(TEXT_DIM);
        date.setShadowLayer(dp(6), 0, 0, 0x80000000);
        time.addView(date);
        bar.addView(time);
        return bar;
    }

    private TextView pill(String label, boolean selected, View.OnClickListener click) {
        TextView t = text(15, TEXT, medium);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), 0, dp(16), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(34)));
        stylePill(t, false, selected);
        t.setOnFocusChangeListener((v, f) -> stylePill((TextView) v, f, selected));
        return t;
    }

    private void stylePill(TextView t, boolean focused, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        bg.setColor(focused ? FOCUS_BG : (selected ? 0x40FFFFFF : Color.TRANSPARENT));
        t.setBackground(bg);
        t.setTextColor(focused ? FOCUS_TEXT : TEXT);
    }

    // ---------------------------------------------------------------- bottom

    private View buttonBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(0, dp(8), 0, dp(6));
        bar.addView(glassButton(R.drawable.ic_apps, "Todas las apps", v -> openApps()));
        bar.addView(glassButton(R.drawable.ic_sparkle, "Gemini", v -> openAssistant()));
        wallpaperButton = glassButton(R.drawable.ic_picture, "Fondo: Auto", v -> cycleWallpaper());
        bar.addView(wallpaperButton);
        bar.addView(glassButton(R.drawable.ic_settings, "Ajustes", v -> openQuickSettings()));
        return bar;
    }

    private TextView glassButton(int icon, String label, View.OnClickListener click) {
        TextView b = text(14, TEXT, medium);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(8));
        b.setPadding(dp(20), 0, dp(22), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.setMargins(dp(7), 0, dp(7), 0);
        b.setLayoutParams(lp);
        styleGlassButton(b, false);
        b.setOnFocusChangeListener((v, f) -> {
            styleGlassButton((TextView) v, f);
            v.animate().scaleX(f ? 1.05f : 1f).scaleY(f ? 1.05f : 1f).setDuration(120).start();
        });
        return b;
    }

    private void styleGlassButton(TextView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(focused ? FOCUS_BG : 0x2EFFFFFF);
        bg.setStroke(focused ? 0 : 1, GLASS_EDGE);
        b.setBackground(bg);
        int color = focused ? FOCUS_TEXT : TEXT;
        b.setTextColor(color);
        for (Drawable d : b.getCompoundDrawables()) {
            if (d != null) {
                d.mutate().setTint(color);
                d.setBounds(0, 0, dp(18), dp(18));
            }
        }
    }

    private View footer() {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        f.setPadding(dp(42), dp(8), dp(42), 0);
        weather = text(14, TEXT_DIM, regular);
        weather.setText(Profile.weatherCity(this));
        f.addView(weather, new LinearLayout.LayoutParams(0, -2, 1f));
        phaseLabel = text(13, 0x99FFFFFF, regular);
        f.addView(phaseLabel);
        return f;
    }

    // ---------------------------------------------------------------- scenery and greeting

    private ImageView background() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return iv;
    }

    private void updateScenery(boolean animate) {
        Scenery.Phase phase = pinnedPhase != null ? pinnedPhase : Scenery.phaseNow(lat, lon);
        Scenery.Kind kind = Weather.kind();
        precip.setKind(kind);
        ambient.setScene(phase, kind);
        if (moving == null) {
            phaseLabel.setText("Fondo " + (pinnedPhase == null ? "automático" : "fijo") + "  ·  " + Scenery.label(phase)
                    + (kind == Scenery.Kind.CLEAR ? "" : "  ·  " + Scenery.label(kind)));
        }
        if (wallpaperButton != null) {
            wallpaperButton.setText(pinnedPhase == null ? "Fondo: Auto" : "Fondo: " + Scenery.label(pinnedPhase));
            styleGlassButton(wallpaperButton, wallpaperButton.hasFocus());
        }
        if (phase == shownPhase && kind == shownKind) return;
        shownPhase = phase;
        shownKind = kind;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = Math.max(dm.widthPixels, dm.heightPixels), h = Math.min(dm.widthPixels, dm.heightPixels);
        Bitmap bmp = Scenery.paint(phase, kind, w, h, false);   // stars and clouds move in AmbientView
        ImageView in = showingA ? backB : backA, out = showingA ? backA : backB;
        in.setImageBitmap(bmp);
        if (animate) {
            in.animate().alpha(1f).setDuration(1500).start();
            out.animate().alpha(0f).setDuration(1500).start();
        } else {
            in.setAlpha(1f);
            out.setAlpha(0f);
        }
        showingA = !showingA;
    }

    private void cycleWallpaper() {
        Scenery.Phase[] all = Scenery.Phase.values();
        if (pinnedPhase == null) pinnedPhase = all[0];
        else if (pinnedPhase.ordinal() == all.length - 1) pinnedPhase = null;
        else pinnedPhase = all[pinnedPhase.ordinal() + 1];
        updateScenery(true);
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = h < 5 ? "Buenas noches" : h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
        String owner = Profile.userName(this);
        String hello = owner.isEmpty() ? part : part + ", " + owner;
        String line = "Relájate  ·  Mira  ·  Explora";
        String abbr = Sports.teamAbbr(this), team = Sports.teamShort(this);
        if (myGame != null && myGame.side(abbr) != null) {
            Sports.Team me = myGame.side(abbr), them = myGame.other(abbr);
            if (myGame.done()) {
                boolean won = me.winner;
                hello = won ? "¡Ganaron " + withArticle(team) + "!" : (isTie(me, them) ? "Empate " + withArticle(team)
                        : "Perdieron " + withArticle(team));
                line = me.abbr + "  " + me.score + " – " + them.score + "  " + them.abbr + "  ·  " + myGame.detail;
            } else if (myGame.live()) {
                hello = "¡Juegan " + withArticle(team) + "!";
                line = me.abbr + "  " + me.score + " – " + them.score + "  " + them.abbr + "  ·  " + myGame.detail;
            } else {
                hello = "¡Hoy juegan " + withArticle(team) + "!";
                String cd = Sports.countdown(myGame.start);
                line = "vs " + them.shortName + "  ·  " + Sports.when(this, myGame.start)
                        + (myGame.broadcast.isEmpty() ? "" : "  ·  " + myGame.broadcast) + (cd.isEmpty() ? "" : "  ·  " + cd);
            }
        } else if (nextGame != null && nextGame.side(abbr) != null) {
            line = "Próximo: " + team + " vs " + nextGame.other(abbr).shortName + "  ·  " + Sports.when(this, nextGame.start);
        }
        greeting.setText(hello);
        tagline.setText(line);
    }

    /** "los Chiefs", but "Portugal" or "México" without an article. */
    private static String withArticle(String team) {
        return Sports.NATIONS_NAMES.contains(team) ? team : "los " + team;
    }

    private static boolean isTie(Sports.Team a, Sports.Team b) {
        return !a.winner && !b.winner && a.score.equals(b.score);
    }

    // ---------------------------------------------------------------- sports mode

    private void refreshSports() {
        Sports.load(this, (today, mine, next) -> {
            if (isFinishing()) return;
            myGame = mine;
            ringColor = mine != null ? Sports.teamColor(this) : Color.WHITE;
            nextGame = next;
            sports.show(today, Sports.teamAbbr(this), Sports.teamColor(this));
            // The screen is 540 dp tall: with the "Hoy" row, the team logo and the player there is no room left for
            // Recientes, so they step aside while the row shows (the score matters more on game day).
            recentsRow.setVisibility(sports.getVisibility() == View.VISIBLE ? View.GONE
                    : recentsRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            boolean gameDay = mine != null;
            teamLogo.setVisibility(gameDay ? View.VISIBLE : View.GONE);
            teamTint.setVisibility(gameDay ? View.VISIBLE : View.GONE);
            if (gameDay) {
                Logos.into(teamLogo, Sports.teamLogo(this), 128);
                int c = Sports.teamColor(this);
                teamTint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{(c & 0x00FFFFFF) | 0x59000000, (c & 0x00FFFFFF) | 0x1A000000, 0x00000000}));
                Sports.Team me = mine.side(Sports.teamAbbr(this));
                // A win gets confetti once per game, the first time Home shows the final.
                android.content.SharedPreferences seen = getSharedPreferences("sports_seen", MODE_PRIVATE);
                if (mine.done() && me != null && me.winner && !mine.id.equals(seen.getString("celebrated", ""))) {
                    seen.edit().putString("celebrated", mine.id).apply();
                    confetti.burst(Sports.teamColor(this), Sports.teamAltColor(this));
                }
            }
            updateGreeting();
        });
    }

    // ---------------------------------------------------------------- apps

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        hidden.add("com.android.tv.settings");
        hidden.add("io.freemytv");
        List<App> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (hidden.contains(ai.packageName) || !seen.add(ai.packageName)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            boolean real = banner != null;
            if (!real) banner = ai.loadIcon(pm);
            all.add(new App(ai.packageName, ri.loadLabel(pm).toString(), launch, banner, real));
        }
        Collator collator = Collator.getInstance(new Locale("es"));
        all.sort((a, b) -> collator.compare(a.label, b.label));
        apps.clear();
        for (String pkg : getResources().getStringArray(R.array.dock_apps)) {
            for (App a : all) if (a.pkg.equals(pkg)) apps.add(a);
        }
        for (App a : all) if (!apps.contains(a)) apps.add(a);
        AppOrder.apply(this, apps, a -> a.pkg);
        home.clear();
        List<String> saved = AppOrder.home(this);
        if (saved == null) {
            for (App a : apps) if (home.size() < AppOrder.HOME_DEFAULT) home.add(a);
            saveHome();
        } else {
            for (String pkg : saved) for (App a : apps) if (a.pkg.equals(pkg)) home.add(a);
        }
    }

    private void saveHome() {
        List<String> pkgs = new ArrayList<>();
        for (App a : home) pkgs.add(a.pkg);
        AppOrder.saveHome(this, pkgs);
    }

    private boolean onHome(App app) {
        for (App a : home) if (a.pkg.equals(app.pkg)) return true;
        return false;
    }

    private void buildRow() {
        View focused = getCurrentFocus();
        String keep = focused != null && focused.getParent() == appRow && focused.getTag() instanceof App
                ? ((App) focused.getTag()).pkg : null;
        appRow.removeAllViews();
        firstCard = null;
        View restore = null;
        for (App app : home) {
            View card = card(app, 124, 70);
            appRow.addView(card, rowParams());
            if (firstCard == null) firstCard = card;
            if (app.pkg.equals(keep)) restore = card;
        }
        // Last in the row: opens Todas las apps, where the rest are and where you add them.
        View add = card(new App("", "Agregar", null, getDrawable(R.drawable.ic_add), false), 124, 70);
        add.setBackgroundColor(0x33FFFFFF);
        add.setOnClickListener(v -> openApps());
        add.setOnKeyListener(null);
        appRow.addView(add, rowParams());
        Rows.fence(appRow);
        if (firstCard == null) firstCard = add;
        if ("".equals(keep)) restore = add;
        if (restore != null) restore.requestFocus();
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(124), dp(70));
        lp.rightMargin = dp(12);
        return lp;
    }

    private View card(App app, int wDp, int hDp) {
        FrameLayout card = new FrameLayout(this);
        card.setTag(app);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });
        card.setBackgroundColor(0xE61E2026);
        ImageView img = new ImageView(this);
        img.setImageDrawable(app.banner);
        if (app.realBanner) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(hDp / 2f), dp(hDp / 2f), Gravity.CENTER);
            ilp.bottomMargin = dp(10);
            card.addView(img, ilp);
            TextView n = text(11, TEXT, medium);
            n.setText(app.label);
            n.setSingleLine(true);
            n.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            nlp.bottomMargin = dp(5);
            card.addView(n, nlp);
        }
        SnakeRing ring = new SnakeRing(this, dp(10), dp(3));
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, f) -> {
            if (v == moving) return;   // a card being moved keeps its blue frame while it changes place
            ring.setColor(ringColor);
            ring.setOn(f);
            v.setPivotX(v.getWidth() / 2f);
            v.setPivotY(v.getHeight() / 2f);
            if (f) keepInView(v);
            v.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).translationZ(f ? dp(12) : 0)
                    .setDuration(140).start();
        });
        card.setOnClickListener(v -> { if (moving == null) launch(app); });
        card.setOnKeyListener(this::onCardKey);
        return card;
    }

    // ---------------------------------------------------------------- recents

    private void buildRecents() {
        recentsRow.removeAllViews();
        Set<String> launchable = new HashSet<>();
        for (App a : apps) launchable.add(a.pkg);
        List<String> recent = Recents.list(this, launchable, 4);
        if (recent.isEmpty()) {
            recentsRow.setVisibility(View.GONE);
            return;
        }
        recentsRow.setVisibility(sports.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        TextView label = text(13, TEXT_DIM, medium);
        label.setText("Recientes");
        label.setLetterSpacing(0.08f);
        label.setShadowLayer(dp(6), 0, 0, 0x80000000);
        label.setPadding(0, 0, dp(10), 0);
        recentsRow.addView(label);
        for (String pkg : recent) {
            for (App a : apps) if (a.pkg.equals(pkg)) recentsRow.addView(recentChip(a));
        }
    }

    private View recentChip(App app) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(6), 0, dp(14), 0);
        chip.setFocusable(true);
        chip.setClickable(true);
        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(app.pkg));
        } catch (PackageManager.NameNotFoundException e) {
            icon.setImageDrawable(app.banner);
        }
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        chip.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView name = text(13, TEXT, medium);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setPadding(dp(8), 0, 0, 0);
        chip.addView(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.setMargins(dp(5), 0, dp(5), 0);
        chip.setLayoutParams(lp);
        styleChip(chip, name, false);
        chip.setOnFocusChangeListener((v, f) -> {
            styleChip(chip, name, f);
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
        });
        chip.setOnClickListener(v -> launch(app));
        return chip;
    }

    private void styleChip(View chip, TextView name, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(17));
        bg.setColor(focused ? FOCUS_BG : 0x2EFFFFFF);
        bg.setStroke(focused ? 0 : 1, GLASS_EDGE);
        chip.setBackground(bg);
        name.setTextColor(focused ? FOCUS_TEXT : TEXT);
    }

    // ---------------------------------------------------------------- moving a card

    /**
     * Hold OK on a card in the row to move it: Left and Right move it, Down takes it off Home, OK or Back puts it
     * down. Hold OK on a card in Todas las apps to add it to Home or take it off.
     */
    private boolean onCardKey(View card, int keyCode, KeyEvent event) {
        boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
        if (moving == null) {
            if (swallowOk && ok) {
                if (event.getAction() == KeyEvent.ACTION_UP) swallowOk = false;
                return true;
            }
            boolean hold = ok && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 1;
            if (hold && card.getParent() == appRow) {
                startMove(card);
                return true;
            }
            if (hold && card.getParent() == appsGrid) {
                swallowOk = true;
                toggleHome((App) card.getTag());
                return true;
            }
            return false;
        }
        if (card != moving) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (ok) {
                if (movingArmed) endMove();
                else movingArmed = true;   // the release of the long press that started moving
            }
            return ok;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int i = appRow.indexOfChild(card);
            int j = i + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (j >= 0 && j < home.size()) {   // never past Agregar, the last card
                appRow.removeViewAt(i);
                appRow.addView(card, j);
                home.add(j, home.remove(i));
                firstCard = appRow.getChildAt(0);
                Rows.fence(appRow);
                card.requestFocus();
                keepInView(card);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            int i = appRow.indexOfChild(card);
            moving = null;
            home.remove(i);
            saveHome();
            buildRow();
            appRow.getChildAt(Math.min(i, appRow.getChildCount() - 1)).requestFocus();
            shownPhase = null;
            updateScenery(false);
            buildGrid();
            return true;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || ok;
    }

    /** Adds the app to the end of Home, or takes it off; it stays in Todas las apps either way. */
    private void toggleHome(App app) {
        boolean was = onHome(app);
        if (was) home.removeIf(a -> a.pkg.equals(app.pkg));
        else home.add(app);
        saveHome();
        buildRow();
        buildGrid();
        appsHint.setText((was ? "Se quitó del inicio: " : "Se agregó al inicio: ") + app.label);
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View c = appsGrid.getChildAt(i);
            if (((App) c.getTag()).pkg.equals(app.pkg)) c.requestFocus();
        }
    }

    private void startMove(View card) {
        moving = card;
        movingArmed = false;
        ringOf(card).setMoving(true);
        card.animate().scaleX(1.18f).scaleY(1.18f).setDuration(140).start();
        phaseLabel.setText("Mueve con ◀ ▶  ·  ▼ lo quita del inicio  ·  OK para soltar");
    }

    private void endMove() {
        View card = moving;
        moving = null;
        saveHome();
        if (card != null) {
            boolean f = card.hasFocus();
            ringOf(card).setMoving(false);
            ringOf(card).setOn(f);
            card.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(140).start();
        }
        shownPhase = null;   // repaint puts the phase line back
        updateScenery(false);
        buildGrid();
    }

    /** The focus frame is the last child of a card. */
    private static SnakeRing ringOf(View card) {
        FrameLayout f = (FrameLayout) card;
        return (SnakeRing) f.getChildAt(f.getChildCount() - 1);
    }

    /** Scrolls the row so the focused card keeps a margin from the screen edge, room for its frame. */
    private void keepInView(View card) {
        if (!(card.getParent() instanceof View) || !(((View) card.getParent()).getParent() instanceof HorizontalScrollView)) return;
        HorizontalScrollView hs = (HorizontalScrollView) ((View) card.getParent()).getParent();
        hs.post(() -> {
            int margin = dp(44), x = hs.getScrollX(), w = hs.getWidth();
            if (card.getRight() + margin > x + w) hs.smoothScrollTo(card.getRight() + margin - w, 0);
            else if (card.getLeft() - margin < x) hs.smoothScrollTo(Math.max(0, card.getLeft() - margin), 0);
        });
    }

    private FrameLayout appsOverlay() {
        FrameLayout o = new FrameLayout(this);
        o.setBackgroundColor(0xE60B0C10);
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.setClipChildren(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, dp(34), 0, dp(34));
        col.setClipChildren(false);
        TextView title = text(24, TEXT, medium);
        title.setText("Todas las apps");
        col.addView(title, new LinearLayout.LayoutParams(-2, -2));   // wrap, so the column centres them
        appsHint = text(14, TEXT_DIM, medium);
        appsHint.setPadding(0, dp(8), 0, 0);
        col.addView(appsHint, new LinearLayout.LayoutParams(-2, -2));
        appsGrid = new GridLayout(this);
        appsGrid.setColumnCount(5);
        appsGrid.setClipChildren(false);
        appsGrid.setClipToPadding(false);
        appsGrid.setPadding(0, dp(16), 0, 0);
        col.addView(appsGrid, new LinearLayout.LayoutParams(-2, -2));
        s.addView(col, new FrameLayout.LayoutParams(-1, -2));
        o.addView(s, new FrameLayout.LayoutParams(-1, -1));
        return o;
    }

    private void buildGrid() {
        appsGrid.removeAllViews();
        for (App app : apps) {
            View c = card(app, 150, 84);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(150);
            lp.height = dp(84);
            lp.setMargins(dp(9), dp(10), dp(9), dp(10));
            if (onHome(app)) markOnHome((FrameLayout) c);
            appsGrid.addView(c, lp);
        }
    }

    /** A small check in the corner of the apps that are on Home, under the focus frame. */
    private void markOnHome(FrameLayout card) {
        TextView check = text(13, 0xFF0B1A33, medium);
        check.setText("✓");
        check.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(0xFF8AB4F8);
        check.setBackground(dot);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(6), dp(6), 0);
        card.addView(check, card.getChildCount() - 1, lp);
    }

    private void openApps() {
        appsHint.setText("OK la abre  ·  Mantén OK para ponerla o quitarla del inicio");
        appsOverlay.setAlpha(0f);
        appsOverlay.setVisibility(View.VISIBLE);
        appsOverlay.animate().alpha(1f).setDuration(180).start();
        if (appsGrid.getChildCount() > 0) appsGrid.getChildAt(0).requestFocus();
    }

    private void closeApps() {
        if (appsOverlay.getVisibility() != View.VISIBLE) return;
        appsOverlay.setVisibility(View.GONE);
        if (firstCard != null) firstCard.requestFocus();
    }

    private void launch(App app) {
        Recents.record(this, app.pkg);
        try {
            startActivity(app.launch);
        } catch (ActivityNotFoundException | SecurityException e) {
            Intent fallback = getPackageManager().getLeanbackLaunchIntentForPackage(app.pkg);
            if (fallback != null) startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void openAssistant() {
        Intent[] tries = {
                new Intent(Intent.ACTION_ASSIST),
                new Intent(RecognizerIntent.ACTION_WEB_SEARCH),
                new Intent("android.search.action.GLOBAL_SEARCH"),
        };
        for (Intent i : tries) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }

    private void openQuickSettings() {
        openQuickSettings(this);
    }

    /** The quick settings panel, over whatever is on screen. */
    static void openQuickSettings(Context context) {
        context.startActivity(new Intent(context, QuickSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    // ---------------------------------------------------------------- helpers

    private GradientDrawable glass(int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(GLASS);
        g.setStroke(1, GLASS_EDGE);
        return g;
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** A four-point sparkle in Google's blue to purple to rose, next to the name. */
    private static final class SparkleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        SparkleView(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            float s = Math.min(w, h), cx = w / 2f, cy = h / 2f, r = s / 2f, k = 0.1f * s;
            path.reset();
            path.moveTo(cx, cy - r);
            path.cubicTo(cx + k * 0.6f, cy - r * 0.45f, cx + r * 0.45f, cy - k * 0.6f, cx + r, cy);
            path.cubicTo(cx + r * 0.45f, cy + k * 0.6f, cx + k * 0.6f, cy + r * 0.45f, cx, cy + r);
            path.cubicTo(cx - k * 0.6f, cy + r * 0.45f, cx - r * 0.45f, cy + k * 0.6f, cx - r, cy);
            path.cubicTo(cx - r * 0.45f, cy - k * 0.6f, cx - k * 0.6f, cy - r * 0.45f, cx, cy - r);
            path.close();
            paint.setShader(new android.graphics.LinearGradient(0, h, w, 0,
                    new int[]{0xFF4285F4, 0xFF9B72CB, 0xFFD96570}, new float[]{0.1f, 0.55f, 0.95f},
                    android.graphics.Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }
}
