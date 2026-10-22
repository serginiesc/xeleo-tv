package com.xeleo.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

/**
 * Quick settings > Deportes: Sports mode on or off, the leagues shown in Home's "Hoy" row, your team (picked from
 * the league's teams, with logos) and the alerts over other apps.
 */
public class SportsSettingsActivity extends Activity {

    private static final int CARD = 0xFF131D23;
    private static final int TILE = 0xFF1C262C;
    private static final int FOCUS = 0xFFE3E3E3;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;
    private static final int DARK = 0xFF1F1F1F;
    private static final int ON = 0xFF8AB4F8;

    private float density;
    private Typeface regular, medium;
    private LinearLayout card;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));
        FrameLayout root = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        scroll.addView(card);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(520), -2, Gravity.CENTER);
        lp.setMargins(0, dp(20), 0, dp(20));
        root.addView(scroll, lp);
        setContentView(root);
        showSettings();
    }

    // ---------------------------------------------------------------- main screen

    private void showSettings() {
        card.removeAllViews();
        title("Deportes", "Los partidos de hoy en el inicio y tu equipo en grande el día que juega.");

        View first = row("Modo Sports", Sports.enabled(this) ? "Encendido" : "Apagado", v -> {
            Sports.set(this, "on", !Sports.enabled(this));
            refresh(0);
        });

        label("Ligas en la fila \"Hoy\"");
        GridLayout leagues = new GridLayout(this);
        leagues.setColumnCount(3);
        card.addView(leagues);
        Set<String> chosen = Sports.leagues(this);
        for (Sports.League l : Sports.LEAGUES) {
            boolean on = chosen.contains(l.key);
            TextView chip = chip(l.label + (l.key.equals("selecciones") ? " (POR, MEX)" : ""), on);
            chip.setOnClickListener(v -> {
                Set<String> s = Sports.leagues(this);
                if (!s.remove(l.key)) s.add(l.key);
                Sports.set(this, "leagues", s);
                refresh(card.indexOfChild(leagues));
            });
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.setMargins(0, 0, dp(8), dp(8));
            leagues.addView(chip, glp);
        }

        // Your teams, one per league: Home follows the one that plays soonest.
        label("Tus equipos (uno por liga)");
        for (Sports.League l : Sports.LEAGUES) {
            Sports.MyTeam t = Sports.myTeam(this, l.key);
            row(l.label, t == null ? "Elegir" : t.name, v -> showTeams(l));
        }
        row("Avisos sobre otras apps", Sports.alerts(this) ? "Encendidos" : "Apagados", v -> {
            Sports.set(this, "alerts", !Sports.alerts(this));
            refresh(0);
        });
        TextView note = text(11, TEXT_DIM, regular);
        note.setText("Avisan 15 minutos antes, al empezar, cuando anotan y al final, para todos tus equipos. "
                + "Apágalos si lo ves por una señal con retraso y no quieres enterarte antes.");
        note.setPadding(dp(2), dp(4), 0, dp(10));
        card.addView(note);
        TextView source = text(10, 0x99C4C7C5, regular);
        source.setText("Datos: ESPN");
        card.addView(source);
        first.post(first::requestFocus);
    }

    /** Rebuilds the screen and puts focus back near where it was. */
    private void refresh(int keepIndex) {
        View f = getCurrentFocus();
        int idx = f == null ? -1 : indexPath(f);
        showSettings();
        if (idx >= 0) card.post(() -> {
            View v = findFocusableByPath(idx);
            if (v != null) v.requestFocus();
        });
    }

    private int indexPath(View v) {
        View child = v;
        while (child.getParent() != card && child.getParent() instanceof View) {
            View parent = (View) child.getParent();
            if (parent == card) break;
            if (parent instanceof GridLayout) return 1000 + card.indexOfChild(parent) * 100 + ((GridLayout) parent).indexOfChild(child);
            child = parent;
        }
        return card.indexOfChild(child);
    }

    private View findFocusableByPath(int idx) {
        if (idx >= 1000) {
            View g = card.getChildAt((idx - 1000) / 100);
            return g instanceof GridLayout ? ((GridLayout) g).getChildAt((idx - 1000) % 100) : null;
        }
        return card.getChildAt(idx);
    }

    // ---------------------------------------------------------------- team picker

    private void showTeams(Sports.League l) {
        card.removeAllViews();
        title(l.label, "Cargando equipos…");
        TextView hint = (TextView) card.getChildAt(1);
        new Thread(() -> {
            List<Sports.Team> teams = Sports.teams(l);
            main.post(() -> {
                if (teams.isEmpty()) {
                    hint.setText("No se pudieron cargar los equipos. Revisa la conexión.");
                    return;
                }
                hint.setText("Elige tu equipo de " + l.label + ".");
                GridLayout grid = new GridLayout(this);
                grid.setColumnCount(3);
                card.addView(grid);
                Sports.MyTeam current = Sports.myTeam(this, l.key);
                String mine = current == null ? "" : current.id;
                View focus = null;
                if (current != null) {
                    // First tile: no team in this league.
                    LinearLayout none = new LinearLayout(this);
                    none.setGravity(Gravity.CENTER_VERTICAL);
                    none.setPadding(dp(12), 0, dp(8), 0);
                    none.setFocusable(true);
                    none.setClickable(true);
                    TextView nn = text(12, TEXT, regular);
                    nn.setText("Ninguno");
                    none.addView(nn);
                    styleTile(none, nn, false, false);
                    none.setOnFocusChangeListener((v, f) -> styleTile(none, nn, f, false));
                    none.setOnClickListener(v -> {
                        Sports.setTeam(this, l.key, new Sports.Team());
                        showSettings();
                    });
                    GridLayout.LayoutParams nlp = new GridLayout.LayoutParams();
                    nlp.width = dp(152);
                    nlp.height = dp(40);
                    nlp.setMargins(0, 0, dp(8), dp(8));
                    grid.addView(none, nlp);
                }
                for (Sports.Team t : teams) {
                    LinearLayout b = new LinearLayout(this);
                    b.setGravity(Gravity.CENTER_VERTICAL);
                    b.setPadding(dp(8), 0, dp(8), 0);
                    b.setFocusable(true);
                    b.setClickable(true);
                    ImageView iv = new ImageView(this);
                    Logos.into(iv, t.logo, 64);
                    b.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
                    TextView n = text(12, TEXT, regular);
                    n.setText(t.shortName);
                    n.setSingleLine(true);
                    n.setPadding(dp(8), 0, 0, 0);
                    b.addView(n);
                    boolean selected = t.id.equals(mine);
                    styleTile(b, n, false, selected);
                    b.setOnFocusChangeListener((v, f) -> styleTile(b, n, f, selected));
                    b.setOnClickListener(v -> {
                        Sports.setTeam(this, l.key, t);
                        if (!Sports.enabled(this)) Sports.set(this, "on", true);
                        showSettings();
                    });
                    GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                    glp.width = dp(152);
                    glp.height = dp(40);
                    glp.setMargins(0, 0, dp(8), dp(8));
                    grid.addView(b, glp);
                    if (focus == null || selected) focus = b;
                }
                View f = focus;
                f.post(f::requestFocus);
            });
        }, "teams").start();
    }

    @Override
    public void onBackPressed() {
        if (card.getChildCount() > 0 && card.getChildAt(0) instanceof TextView
                && !"Deportes".contentEquals(((TextView) card.getChildAt(0)).getText())) {
            showSettings();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- pieces

    private void title(String t, String hint) {
        TextView title = text(20, Color.WHITE, medium);
        title.setText(t);
        card.addView(title);
        TextView h = text(12, TEXT_DIM, regular);
        h.setText(hint);
        h.setPadding(0, dp(6), 0, dp(14));
        card.addView(h);
    }

    private void label(String s) {
        TextView l = text(12, TEXT_DIM, regular);
        l.setText(s);
        l.setPadding(dp(2), dp(10), 0, dp(8));
        card.addView(l);
    }

    private View row(String name, String value, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), 0, dp(16), 0);
        r.setFocusable(true);
        r.setClickable(true);
        TextView n = text(15, TEXT, medium);
        n.setText(name);
        r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = text(13, TEXT_DIM, regular);
        v.setText(value);
        r.addView(v);
        styleRow(r, n, v, false);
        r.setOnFocusChangeListener((view, f) -> styleRow(r, n, v, f));
        r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(8);
        card.addView(r, lp);
        return r;
    }

    private void styleRow(View r, TextView n, TextView v, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(focused ? FOCUS : TILE);
        r.setBackground(bg);
        n.setTextColor(focused ? DARK : TEXT);
        v.setTextColor(focused ? 0xFF444746 : TEXT_DIM);
    }

    private TextView chip(String label, boolean on) {
        TextView c = text(13, TEXT, medium);
        c.setText((on ? "✓  " : "") + label);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(14), dp(9), dp(14), dp(9));
        c.setFocusable(true);
        c.setClickable(true);
        styleChip(c, false, on);
        c.setOnFocusChangeListener((v, f) -> styleChip(c, f, on));
        return c;
    }

    private void styleChip(TextView c, boolean focused, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(focused ? FOCUS : TILE);
        bg.setStroke(on && !focused ? dp(2) : 0, ON);
        c.setBackground(bg);
        c.setTextColor(focused ? DARK : on ? ON : TEXT_DIM);
    }

    private void styleTile(View b, TextView n, boolean focused, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(focused ? FOCUS : TILE);
        bg.setStroke(selected && !focused ? dp(2) : 0, ON);
        b.setBackground(bg);
        n.setTextColor(focused ? DARK : TEXT);
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
