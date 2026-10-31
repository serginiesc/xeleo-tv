package com.xeleo.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Commands from the computer over adb, for setting up and testing the TV. Exported, but only senders holding
 * WRITE_SECURE_SETTINGS (the adb shell, not ordinary apps) may call it:
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.SLEEP_TIMER --ei minutes 30
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.WEATHER_PREVIEW --ei code 61
 * (code -1 goes back to the real weather; WMO codes: 0 clear, 2 clouds, 3 overcast, 45 fog, 61 rain, 71 snow, 95 storm)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.BACKUP    (writes the settings file, see Backup)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.RESTORE   (puts them back from it)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.REMINDER_TEST   (a reminder card in a few seconds)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.OPEN_NOTIFICATION --es pkg com.google.android.tv.dfuservice
 * (opens that app's notification, as tapping it would: here the remote's firmware update)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.LIGHT_MODE --es mode on
 * (light mode for small boxes: on, off or auto; it takes effect the next time XELEO starts)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.SEASON_PREVIEW --es season halloween
 * (a season's theme on Home now: halloween or muertos; off goes back to the date's)
 *   am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.GAMEDAY_PREVIEW --es moment live
 * (game day on Home with your team: days, morning, soon, live or won; off goes back to the real one; --ez photo false
 * shows only the colours, --ez poster true the poster instead of the news photo, --es league ligamx your team
 * of that league)
 */
public class ControlReceiver extends BroadcastReceiver {

    static final String REFRESH = "com.xeleo.tv.REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.xeleo.tv.SLEEP_TIMER".equals(action)) {
            boolean ok = XeleoService.setSleepTimer(intent.getIntExtra("minutes", 0));
            setResultData(ok ? "sleep timer set" : "XeleoService is not running");
        } else if ("com.xeleo.tv.SPORTS_TEST".equals(action)) {
            setResultData(XeleoService.testAlert() ? "sample alert shown" : "XeleoService is not running");
        } else if ("com.xeleo.tv.WEATHER_PREVIEW".equals(action)) {
            Weather.previewCode = intent.getIntExtra("code", -1);
            context.sendBroadcast(new Intent(REFRESH).setPackage(context.getPackageName()));
            setResultData("weather preview " + Weather.previewCode);
        } else if ("com.xeleo.tv.BACKUP".equals(action)) {
            try {
                setResultData(Backup.save(context));
            } catch (Exception e) {
                setResultData("backup failed: " + e);
            }
        } else if ("com.xeleo.tv.RESTORE".equals(action)) {
            try {
                boolean ok = Backup.restore(context);
                context.sendBroadcast(new Intent(REFRESH).setPackage(context.getPackageName()).putExtra("all", true));
                setResultData(ok ? "restored" : "no backup at " + Backup.file(context));
            } catch (Exception e) {
                setResultData("restore failed: " + e);
            }
        } else if ("com.xeleo.tv.REMINDER_TEST".equals(action)) {
            Reminders.add(context, "test-" + System.currentTimeMillis(), "Programa de prueba", "XELEO TV", "",
                    System.currentTimeMillis() + Reminders.AHEAD_MS + 5_000);
            setResultData("reminder in a few seconds");
        } else if ("com.xeleo.tv.OPEN_NOTIFICATION".equals(action)) {
            String pkg = intent.getStringExtra("pkg");
            setResultData(pkg != null && MediaListener.openApp(context, pkg) ? "opened" : "no notification from " + pkg);
        } else if ("com.xeleo.tv.LIGHT_MODE".equals(action)) {
            String m = intent.getStringExtra("mode");
            if (m == null || !(m.equals("on") || m.equals("off"))) m = "auto";
            context.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putString("light", m).commit();
            setResultData("light mode " + m + " (restart XELEO to apply)");
        } else if ("com.xeleo.tv.SEASON_PREVIEW".equals(action)) {
            String s = intent.getStringExtra("season");
            SeasonView.preview = s == null || s.equals("off") ? null : s;
            context.sendBroadcast(new Intent(REFRESH).setPackage(context.getPackageName()));
            setResultData("season preview " + SeasonView.preview);
        } else if ("com.xeleo.tv.GAMEDAY_PREVIEW".equals(action)) {
            String m = intent.getStringExtra("moment");
            GameDay.preview = m == null || m.equals("off") ? null : m;
            GameDay.previewNoPhoto = !intent.getBooleanExtra("photo", true);
            GameDay.previewPoster = intent.getBooleanExtra("poster", false);
            GameDay.previewLeague = intent.getStringExtra("league");
            context.sendBroadcast(new Intent(REFRESH).setPackage(context.getPackageName()));
            setResultData("game day preview " + GameDay.preview);
        }
    }
}
