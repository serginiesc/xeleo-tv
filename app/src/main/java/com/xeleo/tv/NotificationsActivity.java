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

    private float density;
    private Typeface regular, medium;
    private LinearLayout card;
    /** True from a long press until OK is released, so that release doesn't also open the notification. */
    private boolean swallowOk;

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
        TextView title = text(20, Color.WHITE, medium);
        title.setText("Notificaciones");
        card.addView(title);
        List<MediaListener.Item> items = MediaListener.list(this);
        TextView hint = text(12, TEXT_DIM, regular);
        hint.setText(items.isEmpty() ? "No hay notificaciones." : "OK la abre  ·  Mantén OK para borrarla");
        hint.setPadding(0, dp(6), 0, dp(14));
        card.addView(hint);
        View first = null;
        boolean anyClearable = false;
        for (MediaListener.Item i : items) {
            View r = row(i);
            if (first == null) first = r;
            anyClearable |= i.clearable;
        }
        if (anyClearable) {
            TextView all = text(13, TEXT, medium);
            all.setText("Borrar todas");
            all.setGravity(Gravity.CENTER);
            all.setFocusable(true);
            all.setClickable(true);
            styleButton(all, false);
            all.setOnFocusChangeListener((v, f) -> styleButton(all, f));
            all.setOnClickListener(v -> {
                for (MediaListener.Item i : items) if (i.clearable) MediaListener.dismiss(i.key);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
            lp.gravity = Gravity.END;
            lp.topMargin = dp(6);
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
        r.setPadding(dp(14), dp(10), dp(16), dp(10));
        r.setFocusable(true);
        r.setClickable(true);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(i.icon);
        r.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        TextView t = text(14, TEXT, medium);
        t.setText(i.title.isEmpty() ? i.app : i.title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        TextView body = text(12, TEXT_DIM, regular);
        body.setText(i.text);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(4), 0, 0);
        if (i.text.isEmpty()) body.setVisibility(View.GONE);
        col.addView(body);
        TextView meta = text(10, TEXT_DIM, regular);
        meta.setText(i.app + "  ·  " + DateUtils.getRelativeTimeSpanString(i.time, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS));
        meta.setPadding(0, dp(5), 0, 0);
        col.addView(meta);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        style(r, t, body, meta, false);
        r.setOnFocusChangeListener((v, f) -> style(r, t, body, meta, f));
        r.setOnClickListener(v -> {
            if (MediaListener.open(i.key)) finish();
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
        lp.bottomMargin = dp(8);
        card.addView(r, lp);
        return r;
    }

    private void style(View r, TextView t, TextView body, TextView meta, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(focused ? FOCUS : TILE);
        r.setBackground(bg);
        t.setTextColor(focused ? DARK : TEXT);
        body.setTextColor(focused ? 0xFF444746 : TEXT_DIM);
        meta.setTextColor(focused ? 0xFF5F6368 : 0xB3C4C7C5);
    }

    private void styleButton(TextView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(19));
        bg.setColor(focused ? FOCUS : TILE);
        b.setBackground(bg);
        b.setTextColor(focused ? DARK : TEXT);
        b.setPadding(dp(18), 0, dp(18), 0);
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
