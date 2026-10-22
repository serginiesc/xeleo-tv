package com.xeleo.tv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Quick settings laid out like Google TV's own panel: a rounded
 * panel on the right with the date and time, a settings button and the profile, then two columns of tiles.
 * Opened by the Ajustes button on Home or a long press on Home (XeleoService).
 */
public class QuickSettingsActivity extends Activity {

    private static final int PANEL = 0xFF131D23;
    private static final int TILE = 0xFF1C262C;
    private static final int TILE_FOCUS = 0xFFE3E3E3;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;
    private static final int DARK = 0xFF1F1F1F;
    private static final int PANEL_W = 272;

    private float density;
    private Typeface regular, medium;
    private LinearLayout panel;
    private boolean closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        FrameLayout root = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(0x8C000000);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(18), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        bg.setCornerRadius(dp(22));
        panel.setBackground(bg);
        panel.setElevation(dp(20));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(dp(PANEL_W), -1, Gravity.END);
        plp.setMargins(0, dp(16), dp(24), dp(16));
        root.addView(panel, plp);

        panel.addView(header());

        NowPlayingView player = new NowPlayingView(this, true, regular, medium);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, -2);
        mlp.topMargin = dp(14);
        panel.addView(player, mlp);

        GridLayout tiles = new GridLayout(this);
        tiles.setColumnCount(2);
        tiles.setClipChildren(false);
        tiles.setPadding(0, dp(12), 0, 0);
        panel.addView(tiles);

        // Starts the screensaver now: Android TV Settings' "sleep" voice action (the onn has no Somnambulator).
        Intent dream = new Intent("com.google.android.pano.action.SLEEP");
        View first = addTile(tiles, R.drawable.ic_moon, "Protector de pantalla", null, dream);
        addTile(tiles, R.drawable.ic_wifi, "Wi‑Fi", wifiName(), new Intent(Settings.ACTION_WIFI_SETTINGS));
        addTile(tiles, R.drawable.ic_picture, "Imagen", null, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        addTile(tiles, R.drawable.ic_bluetooth, "Bluetooth", null, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        addTile(tiles, R.drawable.ic_volume, "Salida de audio", "Sonido", new Intent(Settings.ACTION_SOUND_SETTINGS));
        addTile(tiles, R.drawable.ic_accessibility, "Accesibilidad", null, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        View timer = addTile(tiles, R.drawable.ic_timer, "Temporizador", timerLabel(), null);
        timer.setOnClickListener(v -> cycleSleepTimer(v));
        addTile(tiles, R.drawable.ic_apps, "Apps", null, new Intent(Settings.ACTION_APPLICATION_SETTINGS));
        addTile(tiles, R.drawable.ic_sports, "Deportes", !Sports.enabled(this) ? "Apagado"
                : Sports.teamShort(this).isEmpty() ? "Encendido" : Sports.teamShort(this), new Intent(this, SportsSettingsActivity.class));
        View live = addTile(tiles, R.drawable.ic_live, "En vivo", Live.enabled(this) ? "Encendido" : "Apagado", null);
        live.setOnClickListener(v -> {
            Live.setEnabled(this, !Live.enabled(this));
            Live.prefetch(this);
            TextView sub = (TextView) ((LinearLayout) ((LinearLayout) v).getChildAt(1)).getChildAt(1);
            sub.setText(Live.enabled(this) ? "Encendido" : "Apagado");
        });
        addTile(tiles, R.drawable.ic_person, "Personalizar", Profile.tvName(this), new Intent(this, ProfileActivity.class));
        View restart = addTile(tiles, R.drawable.ic_power, "Reiniciar", "Apagar o reiniciar", null);
        restart.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
            if (!XeleoService.showPowerMenu()) {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(4), 0, dp(4), 0);
        TextView brand = text(13, TEXT, regular);
        brand.setText(Profile.tvName(this));
        footer.addView(brand, new LinearLayout.LayoutParams(0, -2, 1f));
        // Acerca de: the version, the author and the license (AboutActivity).
        TextView about = text(10, TEXT_DIM, regular);
        about.setText("Acerca de");
        about.setPadding(dp(10), dp(5), dp(10), dp(5));
        about.setFocusable(true);
        about.setClickable(true);
        about.setOnFocusChangeListener((v, f) -> {
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(dp(12));
            b.setColor(f ? TILE_FOCUS : 0);
            about.setBackground(b);
            about.setTextColor(f ? DARK : TEXT_DIM);
        });
        about.setOnClickListener(v -> open(new Intent(this, AboutActivity.class)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
        alp.rightMargin = dp(8);
        footer.addView(about, alp);
        TextView weather = text(11, TEXT_DIM, regular);
        weather.setVisibility(View.GONE);
        Weather.update(this, weather, true);
        footer.addView(weather);
        panel.addView(footer);

        setContentView(root);
        panel.setTranslationX(dp(PANEL_W + 24));
        panel.animate().translationX(0).setDuration(240).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(240).start();
        first.post(first::requestFocus);
    }

    /** Date and time on the left; the settings button with its label and the profile on the right. */
    private View header() {
        LinearLayout h = new LinearLayout(this);
        h.setPadding(dp(1), 0, 0, 0);

        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, d 'de' MMM");
        date.setFormat24Hour("EEE, d 'de' MMM");
        date.setTextSize(11);
        date.setTypeface(regular);
        date.setTextColor(TEXT_DIM);
        date.setPadding(0, dp(8), 0, dp(4));
        time.addView(date);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(21);
        clock.setTypeface(regular);
        clock.setTextColor(Color.WHITE);
        time.addView(clock);
        h.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout gearCol = new LinearLayout(this);
        gearCol.setOrientation(LinearLayout.VERTICAL);
        gearCol.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setPadding(dp(6), dp(6), dp(6), dp(6));
        gear.setFocusable(true);
        gear.setClickable(true);
        gear.setContentDescription("Toda la configuración");
        TextView gearLabel = text(8, TEXT_DIM, regular);
        gearLabel.setText("Toda la configuración");
        gearLabel.setSingleLine(true);
        gearLabel.setVisibility(View.INVISIBLE);
        styleRound(gear, false);
        gear.setOnFocusChangeListener((v, f) -> {
            styleRound(v, f);
            gearLabel.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
        });
        gear.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        gearCol.addView(gear, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.topMargin = dp(4);
        gearCol.addView(gearLabel, llp);
        h.addView(gearCol, new LinearLayout.LayoutParams(dp(78), -2));

        TextView avatar = text(13, Color.WHITE, medium);
        String owner = Profile.userName(this);
        avatar.setText(owner.isEmpty() ? "?" : owner.substring(0, 1).toUpperCase());
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFF4E9A2E);
        avatar.setBackground(circle);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(30), dp(30));
        alp.leftMargin = dp(2);
        h.addView(avatar, alp);
        return h;
    }

    private void styleRound(View v, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(focused ? TILE_FOCUS : TILE);
        v.setBackground(bg);
        ((ImageView) v).setColorFilter(focused ? DARK : TEXT);
    }

    private View addTile(GridLayout grid, int icon, String title, String subtitle, Intent intent) {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), 0, dp(6), 0);
        tile.setFocusable(true);
        tile.setClickable(true);

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        tile.addView(iv, new LinearLayout.LayoutParams(dp(13), dp(13)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(8), 0, 0, 0);
        TextView t = text(10.5f, TEXT, regular);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(t);
        TextView s = text(8, TEXT_DIM, regular);
        s.setText(subtitle);
        s.setSingleLine(true);
        s.setPadding(0, dp(3), 0, 0);
        if (subtitle == null || subtitle.isEmpty()) s.setVisibility(View.GONE);
        labels.addView(s);
        tile.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));

        style(tile, iv, t, s, false);
        tile.setOnFocusChangeListener((v, f) -> style(tile, iv, t, s, f));
        tile.setOnClickListener(v -> open(intent));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = dp(119);
        lp.height = dp(48);
        int col = grid.getChildCount() % 2;
        lp.setMargins(col == 0 ? 0 : dp(3), 0, col == 0 ? dp(3) : 0, dp(6));
        grid.addView(tile, lp);
        return tile;
    }

    private static final int[] TIMER_STEPS = {0, 15, 30, 60, 90};

    private String timerLabel() {
        int left = XeleoService.sleepMinutesLeft();
        if (left == 0) return "Apagado";
        java.text.DateFormat f = android.text.format.DateFormat.getTimeFormat(this);
        return left + " min · " + f.format(new java.util.Date(System.currentTimeMillis() + left * 60_000L));
    }

    /** Each press moves to the next step: off, 15, 30, 60, 90 minutes, off. The panel stays open. */
    private void cycleSleepTimer(View tile) {
        int left = XeleoService.sleepMinutesLeft();
        int next = 0;
        for (int step : TIMER_STEPS) {
            if (step > left) { next = step; break; }
        }
        if (left >= TIMER_STEPS[TIMER_STEPS.length - 1]) next = 0;
        TextView sub = (TextView) ((LinearLayout) ((LinearLayout) tile).getChildAt(1)).getChildAt(1);
        if (!XeleoService.setSleepTimer(next)) {
            sub.setText("No disponible");
        } else {
            sub.setText(timerLabel());
        }
        sub.setVisibility(View.VISIBLE);
    }

    private void style(LinearLayout tile, ImageView icon, TextView title, TextView sub, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(focused ? TILE_FOCUS : TILE);
        tile.setBackground(bg);
        icon.setColorFilter(focused ? DARK : TEXT);
        title.setTextColor(focused ? DARK : TEXT);
        sub.setTextColor(focused ? 0xFF444746 : TEXT_DIM);
    }

    private String wifiName() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            String ssid = info != null ? info.getSSID() : null;
            if (ssid == null || ssid.contains("unknown")) return null;
            return ssid.replace("\"", "");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void open(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    private void close() {
        if (closing) return;
        closing = true;
        panel.animate().translationX(dp(PANEL_W + 24)).setDuration(180).withEndAction(() -> {
            finish();
            overridePendingTransition(0, 0);
        }).start();
    }

    @Override
    public void onBackPressed() {
        close();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SETTINGS) {
            close();
            return true;
        }
        return super.onKeyUp(keyCode, event);
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
}
