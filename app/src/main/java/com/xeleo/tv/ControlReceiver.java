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
        }
    }
}
