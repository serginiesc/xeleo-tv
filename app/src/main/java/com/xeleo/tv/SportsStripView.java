package com.xeleo.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Home's "Hoy" row: a glass chip per game today, with both logos, the score (or kickoff time) and the state. Your
 * team's game comes first with a frame in its colour; on days it doesn't play, a chip for it leads the row. OK on
 * a chip opens the game panel (GameActivity), which has the button to watch it in its app.
 */
final class SportsStripView extends HorizontalScrollView {

    private final float density;
    private final Typeface regular, medium;
    private final LinearLayout row;

    SportsStripView(Context context, Typeface regular, Typeface medium) {
        super(context);
        this.regular = regular;
        this.medium = medium;
        density = context.getResources().getDisplayMetrics().density;
        setHorizontalScrollBarEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
        row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(40), dp(6), dp(40), dp(6));
        addView(row, new LayoutParams(-2, -2));
        setVisibility(GONE);
    }

    /**
     * ◀ ▶ go exactly to the chip beside, and stop at the ends. Android's own focus search, in a row that scrolls,
     * sometimes jumped from a chip up to the tabs.
     */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        int k = e.getKeyCode();
        View focused = findFocus();
        if ((k == android.view.KeyEvent.KEYCODE_DPAD_LEFT || k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
                && focused != null && focused.getParent() == row) {
            if (e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                int step = k == android.view.KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                for (int i = row.indexOfChild(focused) + step; i >= 0 && i < row.getChildCount(); i += step) {
                    View next = row.getChildAt(i);
                    if (!next.isFocusable()) continue;
                    next.requestFocus();
                    int margin = dp(40);
                    boolean firstChip = true;   // back at the start: show the "Hoy" label too
                    for (int j = 0; j < i; j++) if (row.getChildAt(j).isFocusable()) firstChip = false;
                    if (firstChip) smoothScrollTo(0, 0);
                    else if (next.getRight() + margin > getScrollX() + getWidth()) smoothScrollTo(next.getRight() + margin - getWidth(), 0);
                    else if (next.getLeft() - margin < getScrollX()) smoothScrollTo(Math.max(0, next.getLeft() - margin), 0);
                    break;
                }
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    /** Your team in this game, if any. */
    private static Sports.MyTeam ours(Sports.Game g, List<Sports.MyTeam> teams) {
        for (Sports.MyTeam t : teams) if (g.leagueKey.equals(t.league) && g.side(t.abbr) != null) return t;
        return null;
    }

    void show(List<Sports.Game> games, List<Sports.MyTeam> teams) {
        row.removeAllViews();
        if (games.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        TextView title = new TextView(getContext());
        title.setText("Hoy");
        title.setTextSize(13);
        title.setTypeface(medium);
        title.setTextColor(0xCCFFFFFF);
        title.setLetterSpacing(0.08f);
        title.setShadowLayer(dp(6), 0, 0, 0x80000000);
        title.setPadding(0, 0, dp(10), 0);
        row.addView(title);
        // What the game panel can page through with ◀ ▶: a chip for each of your teams that doesn't play today, then
        // the games (your teams' first).
        List<Sports.MyTeam> resting = new java.util.ArrayList<>();
        for (Sports.MyTeam t : teams) {
            boolean plays = false;
            for (Sports.Game g : games) if (g.leagueKey.equals(t.league) && g.side(t.abbr) != null) plays = true;
            if (!plays) resting.add(t);
        }
        int count = Math.min(games.size(), 12) + resting.size();
        String[] ids = new String[count], paths = new String[count], leagues = new String[count], casts = new String[count];
        int k = 0;
        for (Sports.MyTeam t : resting) {
            ids[k] = "";
            paths[k] = "";
            leagues[k] = t.league;
            casts[k] = "";
            row.addView(teamChip(t, ids, paths, leagues, casts, k));
            k++;
        }
        for (Sports.Game g : games) {
            if (k == count) break;
            ids[k] = g.id;
            paths[k] = g.path;
            leagues[k] = g.leagueKey;
            casts[k] = TextUtils.join(", ", g.channels);
            int at = k++;
            Sports.MyTeam mine = ours(g, teams);
            View chip = chip(g, mine != null, mine != null ? mine.color : 0);
            chip.setOnClickListener(v -> GameActivity.open(getContext(), ids, paths, leagues, casts, at));
            row.addView(chip);
        }
        Rows.fence(row);
        // Centre the row when it is narrower than the screen, with padding: moving it (setTranslationX) would put
        // the chips where the remote's focus search doesn't expect them, and ▶ would jump to another row.
        row.setPadding(dp(40), dp(6), dp(40), dp(6));
        post(() -> {
            int extra = getWidth() - row.getWidth();
            if (extra > 0) row.setPadding(dp(40) + extra / 2, dp(6), dp(40), dp(6));
        });
    }

    private View chip(Sports.Game g, boolean mine, int myColor) {
        LinearLayout c = new LinearLayout(getContext());
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(12), 0);
        c.setFocusable(true);
        c.setClickable(true);
        c.addView(logo(g.away));
        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.setPadding(dp(8), 0, dp(8), 0);
        TextView score = text(14, 0xFFFFFFFF, medium);
        boolean started = g.live() || g.done();
        score.setText(started ? g.away.abbr + "  " + g.away.score + " – " + g.home.score + "  " + g.home.abbr
                : g.away.abbr + "  @  " + g.home.abbr);
        mid.addView(score);
        TextView state = text(11, g.live() ? 0xFFFF8A80 : 0xB3FFFFFF, regular);
        state.setText(g.live() ? "● " + g.detail : g.done() ? g.detail : Sports.when(getContext(), g.start)
                + (TextUtils.isEmpty(g.broadcast) ? "" : " · " + g.broadcast));
        state.setPadding(0, dp(2), 0, 0);
        mid.addView(state);
        c.addView(mid);
        c.addView(logo(g.home));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(5), 0, dp(5), 0);
        c.setLayoutParams(lp);
        style(c, score, state, mine, myColor, false, g.live());
        c.setOnFocusChangeListener((v, f) -> {
            style(c, score, state, mine, myColor, f, g.live());
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
        });
        return c;
    }

    /** Your team when it doesn't play today: opens the panel on its last game and its standings. */
    private View teamChip(Sports.MyTeam t, String[] ids, String[] paths, String[] leagues, String[] casts, int at) {
        int myColor = t.color;
        LinearLayout c = new LinearLayout(getContext());
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(14), 0);
        c.setFocusable(true);
        c.setClickable(true);
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Logos.into(iv, t.logo, 64);
        c.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(8), 0, 0, 0);
        TextView name = text(14, 0xFFFFFFFF, medium);
        name.setText(t.shortName.isEmpty() ? t.name : t.shortName);
        mid.addView(name);
        TextView what = text(11, 0xB3FFFFFF, regular);
        what.setText("Último partido y tabla");
        what.setPadding(0, dp(2), 0, 0);
        mid.addView(what);
        c.addView(mid);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.setMargins(dp(5), 0, dp(5), 0);
        c.setLayoutParams(lp);
        style(c, name, what, true, myColor, false, false);
        c.setOnFocusChangeListener((v, f) -> {
            style(c, name, what, true, myColor, f, false);
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
        });
        c.setOnClickListener(v -> GameActivity.open(getContext(), ids, paths, leagues, casts, at));
        return c;
    }

    private void style(View c, TextView score, TextView state, boolean mine, int myColor, boolean focused, boolean live) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(focused ? 0xF2FFFFFF : 0x33FFFFFF);
        if (!focused) bg.setStroke(mine ? dp(2) : 1, mine ? (myColor | 0xFF000000) : 0x40FFFFFF);
        c.setBackground(bg);
        score.setTextColor(focused ? 0xFF15171C : 0xFFFFFFFF);
        state.setTextColor(focused ? 0xFF3C4043 : live ? 0xFFFF8A80 : 0xB3FFFFFF);
    }

    private ImageView logo(Sports.Team t) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Logos.into(iv, t.logo, 64);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        return iv;
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
