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
 * team's game comes first with a frame in its colour. A chip opens the app that carries the game, when it's one
 * of the TV's apps (Prime Video, Disney+ for ESPN, YouTube...).
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
        int n = 0;
        for (Sports.Game g : games) {
            if (n++ == 12) break;
            row.addView(chip(g, !myAbbr.isEmpty() && g.side(myAbbr) != null, myColor));
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
        c.setOnClickListener(v -> openBroadcast(g.broadcast));
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

    /** Opens the app that carries the game, if the TV has it. */
    private void openBroadcast(String broadcast) {
        String b = broadcast == null ? "" : broadcast.toLowerCase(java.util.Locale.ROOT);
        String[][] map = {
                {"prime", "com.amazon.amazonvideo.livingroom"},
                {"espn", "com.disney.disneyplus"},
                {"disney", "com.disney.disneyplus"},
                {"youtube", "com.google.android.youtube.tv"},
                {"netflix", "com.netflix.ninja"},
                {"peacock", "com.peacocktv.peacockandroid"},
                {"paramount", "com.cbs.ca"},
                {"max", "com.wbd.stream"},
        };
        for (String[] m : map) {
            if (!b.contains(m[0])) continue;
            Intent i = getContext().getPackageManager().getLeanbackLaunchIntentForPackage(m[1]);
            if (i != null) {
                getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
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
