package com.xeleo.tv;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Android only lets an app read the active media sessions (what Spotify or YouTube is playing) when it holds
 * notification access, and this service is what that access is granted to. It also opens a notification for you:
 * without Google TV's launcher there is no panel to open them from (the remote's firmware update, for one).
 */
public class MediaListener extends NotificationListenerService {

    private static volatile MediaListener running;

    @Override
    public void onListenerConnected() {
        running = this;
    }

    @Override
    public void onListenerDisconnected() {
        running = null;
    }

    /** Opens the newest notification of that app, as tapping it would; false if there is none or no access. */
    static boolean open(String pkg) {
        MediaListener s = running;
        if (s == null) return false;
        StatusBarNotification newest = null;
        try {
            for (StatusBarNotification n : s.getActiveNotifications()) {
                if (!n.getPackageName().equals(pkg) || n.getNotification().contentIntent == null) continue;
                if (newest == null || n.getPostTime() > newest.getPostTime()) newest = n;
            }
            if (newest == null) return false;
            Notification n = newest.getNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                // Android 14 lets a sender start the activity only when it says so (Home may start activities).
                ActivityOptions o = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                n.contentIntent.send(s, 0, null, null, null, null, o.toBundle());
            } else {
                n.contentIntent.send();
            }
            return true;
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            return false;
        }
    }
}
