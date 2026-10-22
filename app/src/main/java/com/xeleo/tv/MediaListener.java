package com.xeleo.tv;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Android only lets an app read the active media sessions (what Spotify or YouTube is playing) when it holds
 * notification access, and this service is what that access is granted to. It also lists the notifications for
 * XELEO's own panel (NotificationsActivity): without Google TV's launcher there is no other place to see them, and
 * some things are only offered there (the remote's firmware update, for one).
 */
public class MediaListener extends NotificationListenerService {

    private static volatile MediaListener running;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Told when notifications come or go (the panel, the dot on Home's Ajustes). */
    static volatile Runnable onChange;

    /** A notification as the panel shows it. */
    static final class Item {
        String key, pkg, app, title, text;
        long time;
        boolean clearable;
        Drawable icon;
    }

    @Override
    public void onListenerConnected() {
        running = this;
        changed();
    }

    @Override
    public void onListenerDisconnected() {
        running = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        changed();
    }

    private static void changed() {
        Runnable r = onChange;
        if (r != null) MAIN.post(r);
    }

    /**
     * The notifications worth showing, newest first: not ours, not ongoing (a song playing, a download), not a group's
     * summary, and with something to read. Empty without notification access.
     */
    static List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        MediaListener s = running;
        if (s == null) return out;
        PackageManager pm = c.getPackageManager();
        try {
            for (StatusBarNotification sbn : s.getActiveNotifications()) {
                Notification n = sbn.getNotification();
                Bundle x = n.extras;
                String template = x.getString(Notification.EXTRA_TEMPLATE, "");
                if (sbn.getPackageName().equals(c.getPackageName()) || sbn.isOngoing()
                        || (n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_FOREGROUND_SERVICE)) != 0
                        || template.contains("MediaStyle")) continue;   // a song playing isn't news
                CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_TEXT);
                if ((title == null || title.length() == 0) && (text == null || text.length() == 0)) continue;
                Item i = new Item();
                i.key = sbn.getKey();
                i.pkg = sbn.getPackageName();
                i.title = title == null ? "" : title.toString();
                i.text = text == null ? "" : text.toString();
                i.time = sbn.getPostTime();
                i.clearable = sbn.isClearable();
                // The app's name and icon. Android hides apps without a TV launcher entry from us (the remote's
                // updater), but the notification carries its app's details.
                android.content.pm.ApplicationInfo ai = x.getParcelable("android.appInfo");
                try {
                    if (ai == null) ai = pm.getApplicationInfo(i.pkg, 0);
                    i.app = pm.getApplicationLabel(ai).toString();
                    i.icon = pm.getApplicationIcon(ai);
                } catch (PackageManager.NameNotFoundException | RuntimeException e) {
                    i.app = "Sistema";
                }
                if (i.icon == null && n.getSmallIcon() != null) {
                    try {
                        i.icon = n.getSmallIcon().loadDrawable(c);
                    } catch (RuntimeException ignored) {
                    }
                }
                out.add(i);
            }
        } catch (RuntimeException ignored) {
            // The service went away meanwhile: nothing to show.
        }
        out.sort((a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** Opens a notification, as tapping it would (and takes it away if it says so); false if it can't. */
    static boolean open(String key) {
        MediaListener s = running;
        StatusBarNotification sbn = s == null ? null : s.find(key);
        if (sbn == null || sbn.getNotification().contentIntent == null) return false;
        Notification n = sbn.getNotification();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14 lets a sender start the activity only when it says so (Home may start activities).
                ActivityOptions o = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                n.contentIntent.send(s, 0, null, null, null, null, o.toBundle());
            } else {
                n.contentIntent.send();
            }
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            return false;
        }
        if ((n.flags & Notification.FLAG_AUTO_CANCEL) != 0) s.cancelNotification(key);
        return true;
    }

    /** Opens the newest notification of that app (ControlReceiver OPEN_NOTIFICATION); false if there is none. */
    static boolean openApp(Context c, String pkg) {
        for (Item i : list(c)) if (i.pkg.equals(pkg)) return open(i.key);
        return false;
    }

    /** Takes a notification away, as swiping it would. */
    static void dismiss(String key) {
        MediaListener s = running;
        if (s != null) s.cancelNotification(key);
    }

    private StatusBarNotification find(String key) {
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) if (sbn.getKey().equals(key)) return sbn;
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
