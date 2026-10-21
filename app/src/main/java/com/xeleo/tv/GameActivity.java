package com.xeleo.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The game panel, over Home: opened with OK on a game in the "Hoy" row (or on your team's chip, for its last
 * game). Score by period, leaders, team stats side by side, the group's standings and what's next. ◀ ▶ go to the
 * other games of the row; while a game is on it refreshes every 30 seconds.
 */
public class GameActivity extends Activity {

    static final String IDS = "ids", PATHS = "paths", LEAGUES = "leagues", BROADCASTS = "broadcasts", INDEX = "index";

    private static final int PANEL = 0xFA111820;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xADFFFFFF;
    private static final int FAINT = 0x78FFFFFF;
    private static final int LINE = 0x1FFFFFFF;
    private static final int OTHER = 0xFF0080C6;

    private float density;
    private Typeface regular, medium, light;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout panel;
    private LinearLayout content;
    private String[] ids, paths, leagues, broadcasts;
    private int index;
    private int generation;

    private final Runnable refresh = () -> load(false);

    /** Opens the panel on games[index] of the row; an empty id stands for your team's last game. */
    static void open(Context c, String[] ids, String[] paths, String[] leagues, String[] broadcasts, int index) {
        c.startActivity(new Intent(c, GameActivity.class).putExtra(IDS, ids).putExtra(PATHS, paths)
                .putExtra(LEAGUES, leagues).putExtra(BROADCASTS, broadcasts).putExtra(INDEX, index));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = getResources().getFont(R.font.outfit_regular);
        medium = getResources().getFont(R.font.outfit_medium);
        light = getResources().getFont(R.font.outfit_light);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xB3060910));
        ids = getIntent().getStringArrayExtra(IDS);
        paths = getIntent().getStringArrayExtra(PATHS);
        leagues = getIntent().getStringArrayExtra(LEAGUES);
        broadcasts = getIntent().getStringArrayExtra(BROADCASTS);
        index = getIntent().getIntExtra(INDEX, 0);
        if (ids == null || ids.length == 0) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        panel = new FrameLayout(this);
        panel.setBackground(round(PANEL, dp(22), 0x28FFFFFF));
        panel.setClipToOutline(true);
        panel.setElevation(dp(24));
        root.addView(panel, new FrameLayout.LayoutParams(dp(810), dp(470), Gravity.CENTER));
        setContentView(root);
        load(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) handler.postDelayed(refresh, 30_000);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if ((k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) && ids.length > 1) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                index = (index + (k == KeyEvent.KEYCODE_DPAD_LEFT ? ids.length - 1 : 1)) % ids.length;
                load(true);
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---------------------------------------------------------------- loading

    private void load(boolean fresh) {
        handler.removeCallbacks(refresh);
        int gen = ++generation;
        if (fresh) showMessage("Cargando el partido…");
        String id = ids[index], path = paths[index], league = leagues[index], bc = broadcasts[index];
        Context app = getApplicationContext();
        new Thread(() -> {
            GameStats g;
            try {
                g = GameStats.load(app, path, league, id, bc);
            } catch (Exception e) {
                g = null;
            }
            GameStats result = g;
            handler.post(() -> {
                if (gen != generation || isFinishing()) return;
                if (result == null) {
                    if (fresh) showMessage("No se pudieron cargar las estadísticas. Revisa la conexión.");
                } else {
                    show(result, fresh);
                    if (result.state.equals("in")) handler.postDelayed(refresh, 30_000);
                }
            });
        }).start();
    }

    private void showMessage(String msg) {
        panel.removeAllViews();
        content = null;
        TextView t = text(16, DIM, regular);
        t.setText(msg);
        t.setGravity(Gravity.CENTER);
        panel.addView(t, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------------------------------------------------------- the panel

    private void show(GameStats g, boolean fade) {
        panel.removeAllViews();
        // A wash of the left team's colour from the top left, like Home on game day.
        View wash = new View(this);
        GradientDrawable w = new GradientDrawable();
        w.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        w.setColors(new int[]{(g.left.color & 0x00FFFFFF) | 0x55000000, 0x00000000});
        w.setGradientRadius(dp(380));
        w.setGradientCenter(0.18f, 0.05f);
        wash.setBackground(w);
        panel.addView(wash, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(30), dp(18), dp(30), dp(14));
        panel.addView(content, new FrameLayout.LayoutParams(-1, -1));

        TextView top = text(11, DIM, medium);
        top.setLetterSpacing(0.06f);
        top.setText(g.topLine);
        top.setGravity(Gravity.CENTER);
        content.addView(top, new LinearLayout.LayoutParams(-1, -2));

        content.addView(header(g), new LinearLayout.LayoutParams(-1, dp(146)));
        content.addView(divider(), new LinearLayout.LayoutParams(-1, 1));

        LinearLayout body = new LinearLayout(this);
        body.setPadding(0, dp(10), 0, 0);
        body.addView(leaders(g), new LinearLayout.LayoutParams(0, -1, 1f));
        body.addView(compare(g), columnParams());
        body.addView(table(g), columnParams());
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        content.addView(divider(), new LinearLayout.LayoutParams(-1, 1));
        content.addView(footer(g), new LinearLayout.LayoutParams(-1, dp(36)));
        if (fade) {
            content.setAlpha(0f);
            content.animate().alpha(1f).setDuration(200).start();
        }
    }

    private LinearLayout.LayoutParams columnParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.leftMargin = dp(26);
        return lp;
    }

    private View header(GameStats g) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.addView(team(g.left), new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        boolean started = !g.state.equals("pre");
        LinearLayout score = new LinearLayout(this);
        score.setGravity(Gravity.CENTER_VERTICAL);
        boolean leftWins = number(g.left.score) >= number(g.right.score);
        TextView a = text(54, TEXT, leftWins ? medium : light);
        a.setText(started ? g.left.score : "");
        TextView dash = text(36, DIM, light);
        dash.setText(started ? "  –  " : "vs");
        TextView b = text(54, leftWins ? 0xC8FFFFFF : TEXT, leftWins ? light : medium);
        b.setText(started ? g.right.score : "");
        score.addView(a);
        score.addView(dash);
        score.addView(b);
        mid.addView(score, new LinearLayout.LayoutParams(-2, -2));
        TextView state = text(10, TEXT, medium);
        state.setLetterSpacing(0.06f);
        state.setText(g.status);
        state.setPadding(dp(12), dp(4), dp(12), dp(4));
        state.setBackground(round(g.state.equals("in") ? 0xFFE53935 : 0x33FFFFFF, dp(10), 0));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = dp(4);
        mid.addView(state, slp);
        if (g.periodHeads.length > 0) mid.addView(lines(g), new LinearLayout.LayoutParams(-2, -2));
        h.addView(mid, new LinearLayout.LayoutParams(dp(320), -1));

        h.addView(team(g.right), new LinearLayout.LayoutParams(0, -1, 1f));
        return h;
    }

    private View team(GameStats.Side s) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Logos.into(logo, s.logo, 160);
        col.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        TextView name = text(17, TEXT, medium);
        name.setText(s.name);
        name.setSingleLine(true);
        name.setPadding(0, dp(8), 0, 0);
        col.addView(name, new LinearLayout.LayoutParams(-2, -2));
        TextView rec = text(12, DIM, regular);
        rec.setText(s.record);
        rec.setPadding(0, dp(4), 0, 0);
        col.addView(rec, new LinearLayout.LayoutParams(-2, -2));
        return col;
    }

    /** Points by period: a header row and one row per team, total in bold. */
    private View lines(GameStats g) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(5), 0, 0);
        int n = g.periodHeads.length;
        int cell = n > 6 ? dp(17) : dp(26);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(10, DIM, medium);
            name.setGravity(Gravity.END);
            name.setText(r == 0 ? "" : r == 1 ? g.left.abbr : g.right.abbr);
            row.addView(name, new LinearLayout.LayoutParams(dp(34), -2));
            GameStats.Side s = r == 1 ? g.left : g.right;
            for (int i = 0; i <= n; i++) {
                boolean total = i == n;
                TextView c = text(r == 0 ? 9 : 11, r == 0 ? FAINT : TEXT, total && r > 0 ? medium : regular);
                c.setGravity(Gravity.CENTER);
                if (r == 0) c.setText(total ? (g.sport.equals("baseball") ? "C" : "T") : g.periodHeads[i]);
                else c.setText(total ? s.score : i < s.periods.size() ? s.periods.get(i) : "");
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(total ? dp(34) : cell, -2);
                row.addView(c, lp);
            }
            row.setPadding(0, dp(r == 0 ? 0 : 2), dp(34), 0);
            box.addView(row);
        }
        return box;
    }

    private View leaders(GameStats g) {
        LinearLayout col = column("Líderes");
        if (g.leaders.isEmpty()) col.addView(note("Aparecen cuando empiece el partido."));
        for (GameStats.Leader l : g.leaders) {
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(4), dp(10), dp(4));
            card.setBackground(round(0x12FFFFFF, dp(10), 0));
            ImageView logo = new ImageView(this);
            Logos.into(logo, l.logo, 64);
            card.addView(logo, new LinearLayout.LayoutParams(dp(22), dp(22)));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(dp(10), 0, 0, 0);
            TextView cat = text(8, FAINT, medium);
            cat.setLetterSpacing(0.06f);
            cat.setText(l.category);
            t.addView(cat);
            TextView name = text(12, TEXT, medium);
            name.setText(l.name);
            name.setPadding(0, dp(1), 0, 0);
            t.addView(name);
            TextView line = text(9.5f, DIM, regular);
            line.setText(l.line);
            line.setPadding(0, dp(2), 0, 0);
            t.addView(line);
            card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dp(4);
            col.addView(card, lp);
        }
        return col;
    }

    private View compare(GameStats g) {
        LinearLayout col = column("Comparativa");
        if (g.compare.isEmpty()) col.addView(note(g.state.equals("pre") ? "Aparece cuando empiece el partido."
                : "Sin estadísticas por equipo para este partido."));
        int a = (g.left.color & 0x00FFFFFF) | 0xFF000000;
        int b = (g.right.color & 0x00FFFFFF) | 0xFF000000;
        if (similar(a, b)) b = similar(a, OTHER) ? 0xFFB0B6BE : OTHER;
        for (GameStats.Compare c : g.compare) {
            TextView label = text(9.5f, DIM, medium);
            label.setText(c.label);
            label.setPadding(0, dp(3), 0, dp(3));
            col.addView(label);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView va = text(11, TEXT, medium);
            va.setText(c.a);
            row.addView(va, new LinearLayout.LayoutParams(dp(38), -2));
            float total = (float) (c.va + c.vb);
            View barA = new View(this);
            barA.setBackground(round(a, dp(3), 0));
            View barB = new View(this);
            barB.setBackground(round(b, dp(3), 0));
            LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, dp(5), (float) c.va / total);
            la.rightMargin = dp(2);
            LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, dp(5), (float) c.vb / total);
            lb.leftMargin = dp(2);
            LinearLayout bars = new LinearLayout(this);
            bars.addView(barA, la);
            bars.addView(barB, lb);
            row.addView(bars, new LinearLayout.LayoutParams(0, dp(5), 1f));
            TextView vb = text(11, TEXT, medium);
            vb.setText(c.b);
            vb.setGravity(Gravity.END);
            row.addView(vb, new LinearLayout.LayoutParams(dp(38), -2));
            col.addView(row);
        }
        return col;
    }

    private View table(GameStats g) {
        LinearLayout col = column(g.groupName.isEmpty() ? "Tabla" : g.groupName);
        if (g.table.isEmpty()) {
            col.addView(note("Sin tabla para esta competencia."));
            return col;
        }
        col.addView(tableRow(null, g.heads, false, 0));
        for (GameStats.Standing s : g.table) {
            col.addView(tableRow(s, new String[]{s.c1, s.c2, s.c3}, s.mine, s.mine ? g.left.color : 0));
        }
        return col;
    }

    private View tableRow(GameStats.Standing s, String[] cells, boolean mine, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(s == null ? 0 : 5), dp(6), dp(s == null ? 4 : 5));
        if (mine) {
            int c = (color & 0x00FFFFFF);
            row.setBackground(round(c | 0x46000000, dp(8), c | 0xC8000000));
        }
        TextView rank = text(10, FAINT, medium);
        rank.setText(s == null ? "" : String.valueOf(s.rank));
        row.addView(rank, new LinearLayout.LayoutParams(dp(16), -2));
        ImageView logo = new ImageView(this);
        if (s != null) Logos.into(logo, s.logo, 64);
        row.addView(logo, new LinearLayout.LayoutParams(dp(s == null ? 0 : 20), dp(20)));
        TextView name = text(12, TEXT, medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(s == null ? "" : s.name);
        name.setPadding(dp(s == null ? 0 : 8), 0, 0, 0);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        for (int i = 0; i < 3; i++) {
            TextView c = text(s == null ? 8.5f : 11, s == null ? FAINT : i == 1 ? DIM : TEXT, medium);
            c.setGravity(Gravity.END);
            c.setText(cells[i]);
            if (s != null && i == 2 && !c.getText().toString().isEmpty() && "GP".indexOf(cells[i].charAt(0)) >= 0) {
                c.setTextColor(cells[i].startsWith("G") ? 0xFF81C995 : 0xFFF28B82);
            }
            row.addView(c, new LinearLayout.LayoutParams(dp(i == 0 ? 40 : 36), -2));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(2);
        row.setLayoutParams(lp);
        return row;
    }

    private View footer(GameStats g) {
        LinearLayout f = new LinearLayout(this);
        f.setGravity(Gravity.CENTER_VERTICAL);
        boolean next = !g.footerLogo.isEmpty();
        if (next) {
            TextView label = text(8.5f, FAINT, medium);
            label.setLetterSpacing(0.06f);
            label.setText("PRÓXIMO");
            f.addView(label);
            ImageView logo = new ImageView(this);
            Logos.into(logo, g.footerLogo, 64);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(22), dp(22));
            llp.setMargins(dp(12), 0, dp(8), 0);
            f.addView(logo, llp);
        }
        TextView t = text(11.5f, next ? TEXT : DIM, medium);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setText(g.footer);
        f.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Intent watch = Sports.broadcastApp(this, g.broadcast);
        if (watch != null && !g.state.equals("post")) {
            TextView btn = text(11, TEXT, medium);
            btn.setText("Ver en " + label(watch));
            btn.setPadding(dp(14), dp(6), dp(14), dp(6));
            btn.setFocusable(true);
            btn.setClickable(true);
            btn.setBackground(round(0x26FFFFFF, dp(14), 0));
            btn.setOnFocusChangeListener((v, focus) -> {
                btn.setBackground(round(focus ? 0xF2FFFFFF : 0x26FFFFFF, dp(14), 0));
                btn.setTextColor(focus ? 0xFF15171C : TEXT);
            });
            btn.setOnClickListener(v -> {
                startActivity(watch);
                finish();
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.rightMargin = dp(14);
            f.addView(btn, blp);
            btn.post(btn::requestFocus);
        }
        TextView hint = text(9.5f, FAINT, medium);
        hint.setText((ids.length > 1 ? "◀ ▶  otros partidos     " : "") + "Atrás  cierra");
        f.addView(hint);
        return f;
    }

    private String label(Intent i) {
        try {
            return getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(i.getComponent().getPackageName(), 0)).toString();
        } catch (Exception e) {
            return "la app";
        }
    }

    // ---------------------------------------------------------------- helpers

    private LinearLayout column(String title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(13, TEXT, medium);
        t.setText(title);
        t.setPadding(0, 0, 0, dp(8));
        col.addView(t);
        return col;
    }

    private TextView note(String s) {
        TextView t = text(11, FAINT, regular);
        t.setText(s);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        return v;
    }

    private static boolean similar(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db < 90 * 90;
    }

    private static double number(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
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
}
