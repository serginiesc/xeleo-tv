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

    void show(List<Sports.Game> games, String myAbbr, int myColor) {
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
        // What the game panel can page through with ◀ ▶: your team's chip (when it doesn't play today) and the games.
        boolean myTeamPlays = false;
        for (Sports.Game g : games) if (!myAbbr.isEmpty() && g.side(myAbbr) != null) myTeamPlays = true;
        boolean teamChip = !myAbbr.isEmpty() && !myTeamPlays;
        int count = Math.min(games.size(), 12) + (teamChip ? 1 : 0);
        String[] ids = new String[count], paths = new String[count], leagues = new String[count], casts = new String[count];
        int k = 0;
        if (teamChip) {
            ids[0] = "";
            paths[0] = "";
            leagues[0] = Sports.teamLeague(getContext());
            casts[0] = "";
            k = 1;
            row.addView(teamChip(myColor, ids, paths, leagues, casts));
        }
        for (Sports.Game g : games) {
            if (k == count) break;
            ids[k] = g.id;
            paths[k] = g.path;
            leagues[k] = g.leagueKey;
            casts[k] = g.broadcast;
            int at = k++;
            View chip = chip(g, !myAbbr.isEmpty() && g.side(myAbbr) != null, myColor);
            chip.setOnClickListener(v -> GameActivity.open(getContext(), ids, paths, leagues, casts, at));
            row.addView(chip);
        }
        // Centre the row when it is narrower than the screen.
        post(() -> {
            int extra = getWidth() - row.getWidth();
            row.setTranslationX(extra > 0 ? extra / 2f : 0);
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
    private View teamChip(int myColor, String[] ids, String[] paths, String[] leagues, String[] casts) {
        LinearLayout c = new LinearLayout(getContext());
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(14), 0);
        c.setFocusable(true);
        c.setClickable(true);
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Logos.into(iv, Sports.teamLogo(getContext()), 64);
        c.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(8), 0, 0, 0);
        TextView name = text(14, 0xFFFFFFFF, medium);
        name.setText(Sports.teamShort(getContext()));
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
        c.setOnClickListener(v -> GameActivity.open(getContext(), ids, paths, leagues, casts, 0));
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
