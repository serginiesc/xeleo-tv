package com.xeleo.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Quick settings > the bell: the TV's notifications, newest first (MediaListener). OK opens one as tapping it would,
 * holding OK takes it away. Without Google TV's launcher this is the only place to see them.
 */
public class NotificationsActivity extends Activity {

    private static final int CARD = 0xFF131D23;
    private static final int TILE = 0xFF1C262C;
    private static final int FOCUS = 0xFFE3E3E3;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;
    private static final int DARK = 0xFF1F1F1F;

    private static final int PANEL_W = 272;

    private float density;
    private Typeface regular, medium;
    private ScrollView panel;
    private LinearLayout card;
    private boolean closing;
    /** True from a long press until OK is released, so that release doesn't also open the notification. */
    private boolean swallowOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // The same panel as quick settings, on the right, sliding in.
        FrameLayout root = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(0x8C000000);
        scrim.setOnClickListener(v -> close());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        panel = new ScrollView(this);
        panel.setVerticalScrollBarEnabled(false);
        panel.setClipToPadding(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        panel.setBackground(bg);
        panel.setElevation(dp(20));
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(18), dp(14), dp(14));
        panel.addView(card);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(PANEL_W), -1, Gravity.END);
        lp.setMargins(0, dp(16), dp(24), dp(16));
        root.addView(panel, lp);
        setContentView(root);
        // From the quick settings bell it takes the panel's place as it is; from anywhere else it slides in.
        if (!getIntent().getBooleanExtra("instant", false)) {
            panel.setTranslationX(dp(PANEL_W + 24));
            panel.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f)).start();
            scrim.setAlpha(0f);
            scrim.animate().alpha(1f).setDuration(240).start();
        }
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
    protected void onResume() {
        super.onResume();
        MediaListener.onChange = () -> show(focusedIndex());
        show(0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MediaListener.onChange = null;
    }

    /** Builds the list; focus goes to the row at keep (or the nearest one left). */
    private void show(int keep) {
        card.removeAllViews();
        TextView title = text(16, Color.WHITE, medium);
        title.setText("Notificaciones");
        title.setPadding(dp(2), 0, 0, 0);
        card.addView(title);
        List<MediaListener.Item> items = MediaListener.list(this);
        TextView hint = text(9, TEXT_DIM, regular);
        hint.setText(items.isEmpty() ? "No hay notificaciones." : "OK la abre  ·  Mantén OK para borrarla");
        hint.setPadding(dp(2), dp(5), 0, dp(12));
        card.addView(hint);
        View first = null;
        boolean anyClearable = false;
        for (MediaListener.Item i : items) {
            View r = row(i);
            if (first == null) first = r;
            anyClearable |= i.clearable;
        }
        if (anyClearable) {
            TextView all = text(9.5f, TEXT, medium);
            all.setText("Borrar todas");
            all.setGravity(Gravity.CENTER);
            all.setFocusable(true);
            all.setClickable(true);
            styleButton(all, false);
            all.setOnFocusChangeListener((v, f) -> styleButton(all, f));
            all.setOnClickListener(v -> {
                for (MediaListener.Item i : items) if (i.clearable) MediaListener.dismiss(i.key);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(28));
            lp.gravity = Gravity.END;
            lp.topMargin = dp(2);
            card.addView(all, lp);
            if (first == null) first = all;
        }
        int rows = items.size();
        View target = rows == 0 ? first : card.getChildAt(2 + Math.min(Math.max(keep, 0), rows - 1));
        if (target != null) target.post(target::requestFocus);
    }

    /** The row with focus, counted from the first notification; 0 when none has it. */
    private int focusedIndex() {
        View f = getCurrentFocus();
        int i = f == null ? -1 : card.indexOfChild(f) - 2;
        return Math.max(i, 0);
    }

    private View row(MediaListener.Item i) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(12), dp(9), dp(10), dp(9));
        r.setFocusable(true);
        r.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(i.icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(20), dp(20));
        ilp.gravity = Gravity.TOP;
        ilp.topMargin = dp(1);
        r.addView(icon, ilp);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), 0, 0, 0);
        TextView t = text(10.5f, TEXT, medium);
        t.setText(i.title.isEmpty() ? i.app : i.title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        TextView body = text(9, TEXT_DIM, regular);
        body.setText(i.text);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(3), 0, 0);
        if (i.text.isEmpty()) body.setVisibility(View.GONE);
        col.addView(body);
        TextView meta = text(8, TEXT_DIM, regular);
        meta.setText(i.app + "  ·  " + DateUtils.getRelativeTimeSpanString(i.time, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS));
        meta.setPadding(0, dp(4), 0, 0);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(meta);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        style(r, t, body, meta, false);
        r.setOnFocusChangeListener((v, f) -> style(r, t, body, meta, f));
        r.setOnClickListener(v -> {
            if (MediaListener.open(i.key)) {
                finish();
                overridePendingTransition(0, 0);
            }
        });
        r.setOnKeyListener((v, keyCode, e) -> {
            boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
            if (!ok) return false;
            if (swallowOk) {
                if (e.getAction() == KeyEvent.ACTION_UP) swallowOk = false;
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 1 && i.clearable) {
                swallowOk = true;
                MediaListener.dismiss(i.key);   // the list rebuilds itself (onChange)
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        card.addView(r, lp);
        return r;
    }

    private void style(View r, TextView t, TextView body, TextView meta, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(focused ? FOCUS : TILE);
        r.setBackground(bg);
        t.setTextColor(focused ? DARK : TEXT);
        body.setTextColor(focused ? 0xFF444746 : TEXT_DIM);
        meta.setTextColor(focused ? 0xFF5F6368 : 0xB3C4C7C5);
    }

    private void styleButton(TextView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(focused ? FOCUS : TILE);
        b.setBackground(bg);
        b.setTextColor(focused ? DARK : TEXT);
        b.setPadding(dp(14), 0, dp(14), 0);
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
