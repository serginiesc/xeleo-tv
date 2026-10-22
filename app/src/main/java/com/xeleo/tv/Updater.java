package com.xeleo.tv;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates over the air from the repo's GitHub Releases: GitHub publishes XELEO-TV.apk as the release "ultima"
 * ("XELEO TV 1.<number>") on every change. Home looks every six hours; when there is a newer one, the Ajustes tab
 * shows a dot and Acerca de offers it. Installing goes through Android's installer, which asks for one OK on the
 * TV (an app can't update itself silently), and only takes an APK signed with the same key.
 */
final class Updater {

    private static final String RELEASE = "https://api.github.com/repos/serginiesc/xeleo-tv/releases/tags/ultima";
    private static final long EVERY_MS = 6 * 3600_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** The newest version on GitHub (versionCode), its file, and when we last looked. */
    private static volatile int latest;
    private static volatile String apkUrl = "";
    private static volatile long checkedAt;
    private static volatile boolean checking;

    interface Listener {
        void onState(String message, boolean busy);
    }

    private Updater() {}

    static int current(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        } catch (Exception e) {
            return 0;
        }
    }

    /** True once a check found a newer version than this one. */
    static boolean available(Context c) {
        return latest > current(c) && !apkUrl.isEmpty();
    }

    static String latestName() {
        return "1." + latest;
    }

    /** Looks at GitHub if it's been a while (or force); done runs on the main thread. */
    static void check(Context context, boolean force, Runnable done) {
        if (checking || (!force && System.currentTimeMillis() - checkedAt < EVERY_MS)) {
            if (done != null) MAIN.post(done);
            return;
        }
        checking = true;
        new Thread(() -> {
            try {
                JSONObject r = Sports.get(RELEASE);
                // "XELEO TV 1.18" → 18, the commit count the APK was built at (its versionCode).
                String name = r.optString("name");
                int dot = name.lastIndexOf("1.");
                int code = dot < 0 ? 0 : Integer.parseInt(name.substring(dot + 2).replaceAll("[^0-9].*$", ""));
                String url = "";
                JSONArray assets = r.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url");
                }
                latest = code;
                apkUrl = url;
                checkedAt = System.currentTimeMillis();
            } catch (Exception ignored) {
                // No network or GitHub said no: try again on the next check.
            }
            checking = false;
            if (done != null) MAIN.post(done);
        }, "updater").start();
    }

    /** Whether Android lets XELEO install APKs ("Instalar apps desconocidas" for XELEO TV). */
    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    /** The settings screen where that permission is given, for when it's off. */
    static Intent permissionScreen(Context c) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Downloads the newer APK and hands it to Android's installer, which asks for the OK on screen. */
    static void install(Context context, Listener l) {
        Context app = context.getApplicationContext();
        String url = apkUrl;
        l.onState("Descargando XELEO TV " + latestName() + "…", true);
        new Thread(() -> {
            File apk = new File(app.getCacheDir(), "xeleo-update.apk");
            try {
                download(url, apk);
                PackageInstaller pi = app.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.getPackageName());
                int id = pi.createSession(params);
                try (PackageInstaller.Session s = pi.openSession(id);
                     OutputStream out = s.openWrite("xeleo.apk", 0, apk.length());
                     InputStream in = new FileInputStream(apk)) {
                    byte[] buf = new byte[65536];
                    for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                    s.fsync(out);
                    out.close();
                    Intent done = new Intent(app, Result.class);
                    PendingIntent pending = PendingIntent.getBroadcast(app, id, done,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    s.commit(pending.getIntentSender());
                }
                MAIN.post(() -> l.onState("Confirma en la pantalla para actualizar.", false));
            } catch (Exception e) {
                MAIN.post(() -> l.onState("No se pudo descargar la actualización. Revisa la conexión.", false));
            } finally {
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
            }
        }, "updater-install").start();
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);   // GitHub sends the file from another host
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        if (to.length() < 50_000) throw new Exception("too small");
    }

    /** Android's answer about the install: it needs the OK on screen, or it failed. */
    public static class Result extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }
    }
}
