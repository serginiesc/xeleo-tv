package com.xeleo.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Personalizar > Botones del control: press a button of the remote (Netflix, YouTube…) and pick the app it opens
 * (RemoteKeys). For remotes whose app buttons only worked with the TV's own launcher, such as a TCL's.
 */
public class ButtonsActivity extends Activity {

    private static final int CARD = 0xFF131D23;
    private static final int FIELD = 0xFF1C262C;
    private static final int TEXT = 0xFFE3E3E3;
    private static final int TEXT_DIM = 0xFFC4C7C5;
    private static final int FOCUS = 0xFFE3E3E3;
    private static final int DARK = 0xFF1F1F1F;

    private float density;
    private Typeface regular, medium;
    private LinearLayout body;
    private TextView hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().setBackgroundDrawable(new ColorDrawable(0x99000000));

        FrameLayout root = new FrameLayout(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(22), dp(24), dp(20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(420), -2, Gravity.CENTER);
        lp.topMargin = lp.bottomMargin = dp(36);
        root.addView(card, lp);

        TextView title = text(20, Color.WHITE, medium);
        title.setText("Botones del control");
        card.addView(title);
        hint = text(12, TEXT_DIM, regular);
        hint.setPadding(0, dp(6), 0, dp(12));
        card.addView(hint);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        card.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        showList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        RemoteKeys.learning = null;
        if (!isFinishing()) showList();
    }

    /** The buttons already assigned (OK removes one) and the one to add another. */
    private void showList() {
        RemoteKeys.learning = null;
        hint.setText("Para los botones de apps (Netflix, YouTube…) que no abren nada. OK en uno lo quita.");
        body.removeAllViews();
        PackageManager pm = getPackageManager();
        for (Map.Entry<String, ?> e : RemoteKeys.prefs(this).getAll().entrySet()) {
            int code;
            try {
                code = Integer.parseInt(e.getKey().substring(1));
            } catch (RuntimeException ex) {
                continue;
            }
            String pkg = String.valueOf(e.getValue());
            String label = pkg;
            try {
                label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            add(RemoteKeys.name(code) + "  →  " + label, v -> {
                RemoteKeys.remove(this, code);
                showList();
            });
        }
        View plus = add("Agregar un botón", v -> waitForButton());
        plus.post(plus::requestFocus);
        add("Listo", v -> finish());
    }

    private void waitForButton() {
        body.removeAllViews();
        hint.setText("Presiona en el control el botón que quieres usar.");
        View cancel = add("Cancelar", v -> showList());
        cancel.post(cancel::requestFocus);
        RemoteKeys.learning = code -> {
            RemoteKeys.learning = null;
            pickApp(code);
        };
    }

    private void pickApp(int code) {
        body.removeAllViews();
        hint.setText("¿Qué app abre el botón " + RemoteKeys.name(code) + "?");
        PackageManager pm = getPackageManager();
        List<ResolveInfo> found = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        Set<String> seen = new HashSet<>();
        List<String[]> apps = new ArrayList<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            apps.add(new String[]{ri.loadLabel(pm).toString(), pkg});
        }
        Collator collator = Collator.getInstance(new Locale("es"));
        apps.sort((a, b) -> collator.compare(a[0], b[0]));
        add("Cancelar", v -> showList());
        View first = null;
        for (String[] a : apps) {
            View b = add(a[0], v -> {
                RemoteKeys.assign(this, code, a[1]);
                showList();
            });
            if (first == null) first = b;
        }
        if (first != null) first.post(first::requestFocus);
    }

    private View add(String label, View.OnClickListener click) {
        TextView b = text(14, TEXT, medium);
        b.setText(label);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), 0, dp(16), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        style(b, false);
        b.setOnFocusChangeListener((v, f) -> style((TextView) v, f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.topMargin = dp(8);
        body.addView(b, lp);
        return b;
    }

    private void style(TextView b, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(focused ? FOCUS : FIELD);
        b.setBackground(bg);
        b.setTextColor(focused ? DARK : TEXT);
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
