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
        }
    }
}
