package com.xeleo.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * XELEO TV's helper service. Accessibility services keep running while apps come and go and may do a few things an
 * ordinary app cannot, so this is where they live:
 * - the remote's Settings key opens the quick settings panel (so does a long press on Home, where the system lets it
 *   through; the onn keeps it);
 * - the system power menu (restart, power off) for the panel's Reiniciar tile;
 * - the sleep timer, which puts the TV to sleep with GLOBAL_ACTION_LOCK_SCREEN;
 * - the recent apps on Home: every app that comes to the screen, however it was opened;
 * - the remote's app buttons the person assigned (RemoteKeys).
 *
 * Enable over adb:
 *   settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
 *   settings put secure accessibility_enabled 1
 */
public class XeleoService extends AccessibilityService {

    private static XeleoService running;
    private GameAlerts alerts;
    private InputWatcher inputs;
    /** The app on screen now, from window changes; Home skips game alerts (it shows the game itself). */
    private volatile String foreground = "";

    static boolean homeOnScreen() {
        XeleoService s = running;
        return s != null && s.getPackageName().equals(s.foreground);
    }

    String foregroundPackage() {
        return foreground;
    }

    /** ControlReceiver INPUT_TEST: the card for an input that was just connected, to see how it looks. */
    static boolean testInputCard() {
        XeleoService s = running;
        if (s == null || s.inputs == null) return false;
        s.inputs.showCard("HDMI 2");
        return true;
    }

    /** ControlReceiver SPORTS_TEST: a sample alert card, to see how it looks. */
    static boolean testAlert() {
        XeleoService s = running;
        if (s == null || s.alerts == null) return false;
        s.alerts.test();
        return true;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** When the TV goes to sleep, in System.currentTimeMillis(); 0 when no timer is set. */
    private static long sleepAt;
    private final Runnable sleep = () -> {
        sleepAt = 0;
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    };

    /** Packages with a TV launch entry, so only real apps (not dialogs or system UI) count as recent. */
    private final Map<String, Boolean> launchable = new HashMap<>();
    private String lastApp;

    /** Opens the system power menu (Restart, Power off). False when the service is not running. */
    static boolean showPowerMenu() {
        return running != null && running.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /** Starts (minutes > 0) or cancels (0) the sleep timer. False when the service is not running. */
    static boolean setSleepTimer(int minutes) {
        XeleoService s = running;
        if (s == null) return false;
        s.handler.removeCallbacks(s.sleep);
        if (minutes <= 0) {
            sleepAt = 0;
        } else {
            sleepAt = System.currentTimeMillis() + minutes * 60_000L;
            s.handler.postDelayed(s.sleep, minutes * 60_000L);
        }
        return true;
    }

    /** Minutes left on the sleep timer, rounded up; 0 when none is set. */
    static int sleepMinutesLeft() {
        if (sleepAt == 0) return 0;
        long left = sleepAt - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999) / 60_000);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        Sports.init(this);
        alerts = new GameAlerts(this);
        inputs = new InputWatcher(this);
        inputs.start();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(sleep);
        sleepAt = 0;
        if (alerts != null) alerts.stop();
        alerts = null;
        if (inputs != null) inputs.stop();
        inputs = null;
        running = null;
        return super.onUnbind(intent);
    }

    /** The settings button on a TCL remote (RC902V): a key of TCL's own, which its system ignores without its launcher. */
    private static final int TCL_SETTINGS = 4077;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // The remote's Settings key: the onn's gear sends KEYCODE_NOTIFICATION, a TCL's its own 4077, others
        // KEYCODE_SETTINGS. Taken here,
        // so Google TV's own panel (which the onn would open, and which isn't there without its launcher) doesn't.
        int key = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        // Personalizar > Botones del control waiting for a button: it goes there, and does nothing else.
        RemoteKeys.Listener learn = RemoteKeys.learning;
        if (learn != null && !RemoteKeys.reserved(key)) {
            if (!down && !event.isCanceled()) handler.post(() -> learn.pressed(key));
            return true;
        }
        if (!RemoteKeys.reserved(key)) {
            String app = RemoteKeys.app(this, key);
            if (app != null) {
                if (!down && !event.isCanceled()) RemoteKeys.open(this, app);
                return true;
            }
        }
        if (key == KeyEvent.KEYCODE_SETTINGS || key == KeyEvent.KEYCODE_NOTIFICATION || key == TCL_SETTINGS) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) HomeActivity.openQuickSettings(this);
            return true;
        }
        // Long press on Home opens quick settings, as on Google TV. The short press passes through untouched;
        // after a long press Android drops the Home release itself, so Home does not cover the panel.
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 1) {
            HomeActivity.openQuickSettings(this);
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (!"com.android.systemui".equals(pkg)) foreground = pkg;
        if (pkg.equals(lastApp) || pkg.equals(getPackageName())) return;
        Boolean app = launchable.get(pkg);
        if (app == null) {
            app = getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null;
            launchable.put(pkg, app);
        }
        if (!app) return;
        lastApp = pkg;
        Recents.record(this, pkg);
    }

    @Override
    public void onInterrupt() {
    }
}
