package com.xeleo.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The En vivo tab: Pluto TV's channels in rows, and on top a big preview of what the focused one shows now
 * (its picture, title, description and time left). OK opens the channel in the Pluto TV app.
 */
public class LiveActivity extends Activity {

    private static final int BG = 0xFF0B0F17;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xB3FFFFFF;
    private static final int RED = 0xFFE53935;

    private float density;
    private Typeface regular, medium;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat hm = new SimpleDateFormat("h:mm", Locale.getDefault());

    private ImageView heroImage;
    private TextView badge, channelName, title, meta, description, next, status;
    private View progressFill;
    private LinearLayout rowsBox;
    private Live.Channel shownChannel;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            if (shownChannel != null) showHero(shownChannel, false);
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = getResources().getFont(R.font.outfit_regular);
        medium = getResources().getFont(R.font.outfit_medium);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(heroImage, new FrameLayout.LayoutParams(-1, -1));
        // Dark from the left for the text, and from the bottom for the rows.
        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{BG, 0xF20B0F17, 0x990B0F17, 0x000B0F17}));
        root.addView(left, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0F17, 0x330B0F17, 0xE60B0F17, BG, BG}));
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -1));

        root.addView(topBar(), new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        root.addView(hero(), new FrameLayout.LayoutParams(dp(540), -2, Gravity.TOP | Gravity.START));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);   // a row scrolled up fades instead of being cut
        scroll.setFadingEdgeLength(dp(28));
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        rowsBox.setClipChildren(false);
        rowsBox.setPadding(0, dp(6), 0, dp(24));
        scroll.addView(rowsBox, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, dp(262), Gravity.BOTTOM));

        status = text(16, TEXT_DIM, regular);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        root.addView(status, new FrameLayout.LayoutParams(-1, dp(262), Gravity.BOTTOM));

        setContentView(root);
        placeholders();
        Live.load(this, this::onGuide);
    }

    /** Back (or Home) leaves; it fades out the way it faded in. */
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** Dim empty cards, gently pulsing, for the moment the guide takes when Home couldn't fetch it ahead. */
    private void placeholders() {
        for (int r = 0; r < 2; r++) {
            View label = new View(this);
            label.setBackground(round(0x1FFFFFFF, dp(4)));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(90), dp(14));
            llp.setMargins(dp(40), dp(12), 0, dp(10));
            rowsBox.addView(label, llp);
            LinearLayout line = new LinearLayout(this);
            line.setPadding(dp(40), dp(4), 0, dp(6));
            for (int i = 0; i < 5; i++) {
                View card = new View(this);
                card.setBackground(round(0x14FFFFFF, dp(12)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(96));
                lp.rightMargin = dp(12);
                line.addView(card, lp);
            }
            rowsBox.addView(line);
        }
        android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(rowsBox, "alpha", 1f, 0.45f);
        pulse.setDuration(700);
        pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        pulse.start();
        rowsBox.setTag(pulse);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, 60_000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
    }

    // ---------------------------------------------------------------- top

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(40), dp(22), dp(40), 0);
        TextView name = text(19, TEXT, medium);
        name.setText("En vivo");
        name.setShadowLayer(dp(6), 0, 0, 0x80000000);
        bar.addView(name);
        TextView source = text(13, TEXT_DIM, regular);
        source.setText(Live.plutoInstalled(this) ? "Pluto TV  ·  OK abre el canal"
                : "Pluto TV  ·  Instala la app Pluto TV para ver los canales");
        source.setPadding(dp(14), dp(3), 0, 0);
        bar.addView(source, new LinearLayout.LayoutParams(0, -2, 1f));
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("H:mm");
        clock.setTextSize(26);
        clock.setTypeface(regular);
        clock.setTextColor(TEXT);
        clock.setShadowLayer(dp(8), 0, 0, 0x80000000);
        bar.addView(clock);
        return bar;
    }

    private View hero() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(40), dp(76), 0, 0);

        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        badge = text(11, TEXT, medium);
        badge.setLetterSpacing(0.08f);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        line.addView(badge);
        channelName = text(14, TEXT_DIM, medium);
        channelName.setPadding(dp(10), 0, 0, 0);
        channelName.setSingleLine(true);
        line.addView(channelName);
        col.addView(line);

        title = text(32, TEXT, medium);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setShadowLayer(dp(8), 0, 0, 0x80000000);
        title.setPadding(0, dp(10), 0, 0);
        col.addView(title);
        meta = text(14, TEXT_DIM, regular);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, dp(8), 0, 0);
        col.addView(meta);
        description = text(13, TEXT_DIM, regular);
        description.setMaxLines(2);
        description.setEllipsize(TextUtils.TruncateAt.END);
        description.setLineSpacing(dp(2), 1f);
        description.setPadding(0, dp(8), dp(20), 0);
        col.addView(description);

        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(0x40FFFFFF, dp(2)));
        progressFill = new View(this);
        progressFill.setBackground(round(TEXT, dp(2)));
        track.addView(progressFill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(220), dp(3));
        tlp.topMargin = dp(12);
        col.addView(track, tlp);
        next = text(12, TEXT_DIM, regular);
        next.setSingleLine(true);
        next.setEllipsize(TextUtils.TruncateAt.END);
        next.setPadding(0, dp(8), 0, 0);
        col.addView(next);
        return col;
    }

    /** Fills the preview with what the channel shows now. */
    private void showHero(Live.Channel ch, boolean newPicture) {
        shownChannel = ch;
        long now = System.currentTimeMillis();
        Live.Program p = ch.at(now);
        channelName.setText(ch.name);
        boolean live = p != null && p.live;
        badge.setText(live ? "EN VIVO" : "AHORA");
        badge.setBackground(round(live ? RED : 0x33FFFFFF, dp(10)));
        if (p == null) {
            title.setText(ch.name);
            meta.setText("");
            description.setText("");
            next.setText("");
            setProgress(0);
        } else {
            title.setText(p.title);
            int left = (int) Math.max(1, (p.stop - now) / 60_000);
            StringBuilder m = new StringBuilder();
            if (!p.episode.isEmpty()) m.append(p.episode).append("  ·  ");
            m.append(hm.format(new Date(p.start))).append("–").append(hm.format(new Date(p.stop)))
                    .append("  ·  ").append(left < 60 ? "quedan " + left + " min" : "queda " + left / 60 + " h " + left % 60 + " min");
            meta.setText(m);
            description.setText(p.description);
            Live.Program n = ch.after(p);
            next.setText(n == null ? "" : "Sigue a las " + hm.format(new Date(n.start)) + ":  " + n.title);
            setProgress((float) (now - p.start) / (p.stop - p.start));
        }
        if (!newPicture) return;
        String img = p != null && !p.image.isEmpty() ? p.image : ch.image;
        if (img.equals(heroImage.getTag())) return;
        heroImage.animate().cancel();
        heroImage.setAlpha(0.35f);
        Logos.photo(heroImage, img, ch.image, () -> heroImage.animate().alpha(1f).setDuration(260).start());
    }

    private void setProgress(float f) {
        View track = (View) progressFill.getParent();
        track.post(() -> {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            lp.width = Math.round(track.getWidth() * Math.max(0, Math.min(1, f)));
            progressFill.setLayoutParams(lp);
        });
    }

    // ---------------------------------------------------------------- rows

    private void onGuide(List<Live.Row> rows, String error) {
        if (isFinishing() || isDestroyed()) return;
        if (rowsBox.getTag() instanceof android.animation.Animator) ((android.animation.Animator) rowsBox.getTag()).cancel();
        rowsBox.setTag(null);
        rowsBox.removeAllViews();
        if (error != null || rows.isEmpty()) {
            rowsBox.setAlpha(1f);
            status.setText(error != null ? error : "Pluto TV no tiene canales para esta región.");
            status.setVisibility(View.VISIBLE);
            return;
        }
        rowsBox.setAlpha(0f);
        rowsBox.animate().alpha(1f).setDuration(220).start();
        View first = null;
        for (Live.Row r : rows) {
            TextView label = text(15, TEXT, medium);
            label.setText(r.title);
            label.setPadding(dp(40), dp(10), 0, dp(8));
            rowsBox.addView(label);
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            LinearLayout line = new LinearLayout(this);
            line.setClipChildren(false);
            line.setClipToPadding(false);   // the focused card grows into the padding; don't cut its frame
            line.setPadding(dp(40), dp(4), dp(40), dp(6));
            for (Live.Channel ch : r.channels) {
                View card = card(ch);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(184), dp(96));
                lp.rightMargin = dp(12);
                line.addView(card, lp);
                if (first == null) first = card;
            }
            hs.addView(line);
            rowsBox.addView(hs);
        }
        if (first != null) {
            View f = first;
            f.post(f::requestFocus);
            showHero((Live.Channel) f.getTag(), true);
        }
    }

    private View card(Live.Channel ch) {
        FrameLayout card = new FrameLayout(this);
        card.setTag(ch);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(round(0x26FFFFFF, dp(12)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(10), dp(12), dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        top.addView(logo, new LinearLayout.LayoutParams(0, dp(22), 1f));
        Logos.into(logo, ch.logo, 0);
        long now = System.currentTimeMillis();
        Live.Program p = ch.at(now);
        if (p != null) {
            TextView on = text(9, p.live ? 0xFFFF8A80 : TEXT_DIM, medium);
            on.setLetterSpacing(0.08f);
            on.setText(p.live ? "EN VIVO" : "AHORA");
            top.addView(on);
        }
        col.addView(top);

        TextView name = text(13, TEXT, medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setText(p != null ? p.title : ch.name);
        name.setPadding(0, dp(8), 0, 0);
        col.addView(name);
        FrameLayout track = new FrameLayout(this);
        track.setBackground(round(0x33FFFFFF, dp(1)));
        View fill = new View(this);
        fill.setBackground(round(0xE6FFFFFF, dp(1)));
        track.addView(fill, new FrameLayout.LayoutParams(0, -1));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, dp(2));
        tlp.topMargin = dp(7);
        col.addView(track, tlp);
        if (p != null) {
            float f = (float) (now - p.start) / (p.stop - p.start);
            track.post(() -> {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) fill.getLayoutParams();
                flp.width = Math.round(track.getWidth() * Math.max(0, Math.min(1, f)));
                fill.setLayoutParams(flp);
            });
        }
        TextView when = text(10, TEXT_DIM, regular);
        when.setSingleLine(true);
        when.setEllipsize(TextUtils.TruncateAt.END);
        when.setPadding(0, dp(6), 0, 0);
        if (p != null) {
            Live.Program n = ch.after(p);
            when.setText(hm.format(new Date(p.start)) + "–" + hm.format(new Date(p.stop))
                    + (n != null ? "  ·  Sigue: " + n.title : ""));
        } else {
            when.setText(ch.name);
        }
        col.addView(when);
        card.addView(col, new FrameLayout.LayoutParams(-1, -1));

        View ring = new View(this);
        card.addView(ring, new FrameLayout.LayoutParams(-1, -1));
        card.setOnFocusChangeListener((v, f) -> {
            GradientDrawable b = new GradientDrawable();
            b.setCornerRadius(dp(12));
            b.setStroke(dp(3), Color.WHITE);
            ring.setBackground(f ? b : null);
            v.setBackground(round(f ? 0x40FFFFFF : 0x26FFFFFF, dp(12)));
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).translationZ(f ? dp(8) : 0).setDuration(140).start();
            if (f) {
                handler.removeCallbacks(pendingHero);
                pendingChannel = ch;
                handler.postDelayed(pendingHero, 160);   // not on every card while scrolling fast
            }
        });
        card.setOnClickListener(v -> Live.open(this, ch));
        return card;
    }

    private Live.Channel pendingChannel;
    private final Runnable pendingHero = () -> {
        if (pendingChannel != null) showHero(pendingChannel, true);
    };

    // ---------------------------------------------------------------- helpers

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
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
