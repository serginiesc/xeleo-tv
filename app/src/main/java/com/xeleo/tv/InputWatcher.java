package com.xeleo.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * On a TV with inputs, goes to the one where something was just connected (a console, say), which Google TV's home
 * screen does and XELEO in its place has to. From Home or another input it switches; inside an app it only shows a
 * card, so nobody is taken out of a movie. A box without inputs (onn, Chromecast) never starts it. What a TV reports
 * varies by brand: a device with HDMI-CEC appears as a new input, a plain cable as a state change; both count.
 */
final class InputWatcher {

    private static final String TAG = "XeleoInputs";
    /** After the service starts or the TV wakes, inputs come up by themselves: those aren't someone connecting. */
    private static final long SETTLE_MS = 15_000;
    private static final long AGAIN_MS = 30_000;
    private static final long CARD_MS = 7_000;

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("input_auto", true);
    }

    static void setEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean("input_auto", on).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    private final XeleoService service;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> states = new HashMap<>();
    private final Map<String, Long> lastAt = new HashMap<>();
    private TvInputManager tv;
    private long quietUntil;
    private View card;

    private final TvInputManager.TvInputCallback callback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            Integer before = states.put(id, state);
            Log.w(TAG, "state " + id + ": " + before + " -> " + state);
            if (state == TvInputManager.INPUT_STATE_CONNECTED && (before == null || before != state)) arrived(id);
        }

        @Override
        public void onInputAdded(String id) {
            Log.w(TAG, "added " + id);
            TvInputInfo i = tv.getTvInputInfo(id);
            if (i == null || !i.isPassthroughInput()) return;
            Integer state = tv.getInputState(id);
            states.put(id, state);
            if (state == TvInputManager.INPUT_STATE_CONNECTED) arrived(id);
        }

        @Override
        public void onInputRemoved(String id) {
            Log.w(TAG, "removed " + id);
            states.remove(id);
        }
    };

    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            quietUntil = SystemClock.uptimeMillis() + SETTLE_MS;
        }
    };

    InputWatcher(XeleoService service) {
        this.service = service;
    }

    void start() {
        try {
            tv = service.getSystemService(TvInputManager.class);
            if (tv == null) return;
            List<TvInputInfo> inputs = tv.getTvInputList();
            boolean any = false;
            for (TvInputInfo i : inputs) {
                if (!i.isPassthroughInput()) continue;
                any = true;
                states.put(i.getId(), tv.getInputState(i.getId()));
            }
            if (!any) {
                tv = null;
                return;
            }
            quietUntil = SystemClock.uptimeMillis() + SETTLE_MS;
            tv.registerCallback(callback, main);
            service.registerReceiver(wake, new IntentFilter(Intent.ACTION_SCREEN_ON));
            Log.w(TAG, "watching " + states);
        } catch (RuntimeException e) {
            tv = null;
        }
    }

    void stop() {
        if (tv == null) return;
        try {
            tv.unregisterCallback(callback);
            service.unregisterReceiver(wake);
        } catch (RuntimeException ignored) {
        }
        tv = null;
        hide();
    }

    private void arrived(String id) {
        long now = SystemClock.uptimeMillis();
        Long last = lastAt.get(id);
        PowerManager power = service.getSystemService(PowerManager.class);
        if (!enabled(service) || now < quietUntil || (last != null && now - last < AGAIN_MS) || !power.isInteractive()) return;
        TvInputInfo info = tv.getTvInputInfo(id);
        if (info == null || !info.isPassthroughInput() || info.isHidden(service)) return;
        lastAt.put(id, now);
        CharSequence custom = info.loadCustomLabel(service);
        String label = String.valueOf(custom != null && custom.length() > 0 ? custom : info.loadLabel(service));
        Intent open = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String on = service.foregroundPackage();
        ResolveInfo tvApp = service.getPackageManager().resolveActivity(open, 0);
        boolean free = on.isEmpty() || on.equals(service.getPackageName())
                || (tvApp != null && tvApp.activityInfo != null && on.equals(tvApp.activityInfo.packageName));
        Log.w(TAG, "arrived " + label + " on " + on + (free ? ": switching" : ": card"));
        if (free) {
            try {
                service.startActivity(open);
                return;
            } catch (RuntimeException e) {
                Log.w(TAG, "no switch", e);
            }
        }
        showCard(label);
    }

    /** A small card over whatever is on screen; it can't take OK, so it says where the input is. */
    void showCard(String label) {
        hide();
        float d = service.getResources().getDisplayMetrics().density;
        LinearLayout c = new LinearLayout(service);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding((int) (18 * d), (int) (12 * d), (int) (18 * d), (int) (12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF131D23);
        bg.setCornerRadius(16 * d);
        c.setBackground(bg);
        TextView t = new TextView(service);
        t.setText("Se conectó algo en " + label);
        t.setTextSize(15);
        t.setTextColor(0xFFFFFFFF);
        c.addView(t);
        TextView s = new TextView(service);
        s.setText("Ajustes → Entradas para verlo");
        s.setTextSize(13);
        s.setTextColor(0xFFC4C7C5);
        s.setPadding(0, (int) (3 * d), 0, 0);
        c.addView(s);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = (int) (24 * d);
        lp.y = (int) (124 * d);   // under the clock and the date
        try {
            service.getSystemService(WindowManager.class).addView(c, lp);
        } catch (RuntimeException e) {
            return;
        }
        card = c;
        main.postDelayed(() -> {
            if (card == c) hide();
        }, CARD_MS);
    }

    private void hide() {
        if (card == null) return;
        try {
            service.getSystemService(WindowManager.class).removeView(card);
        } catch (RuntimeException ignored) {
        }
        card = null;
    }
}
