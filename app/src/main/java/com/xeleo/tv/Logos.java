package com.xeleo.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Team logos, downloaded small (ESPN's resizer) on two background threads and kept in a little memory cache. */
final class Logos {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Logos() {}

    /** Shows the logo in view when ready; the view's tag guards against a recycled view getting the wrong one. */
    static void into(ImageView view, String url, int px) {
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }
        String small = Sports.smallLogo(url, px);
        view.setTag(small);
        Bitmap hit = CACHE.get(small);
        if (hit != null) {
            view.setImageBitmap(hit);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap b = fetch(small);
            if (b == null) return;
            CACHE.put(small, b);
            MAIN.post(() -> {
                if (small.equals(view.getTag())) view.setImageBitmap(b);
            });
        });
    }

    /** A few programme pictures for En vivo's preview, without alpha (half the memory), apart from the logos. */
    private static final LruCache<String, Bitmap> PHOTOS = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    /**
     * Like into, for a big picture, trying fallback when url doesn't load; shown runs once it is on screen (at once
     * when cached or when there is none).
     */
    static void photo(ImageView view, String url, String fallback, Runnable shown) {
        view.setTag(url);
        Bitmap hit = url.isEmpty() ? null : PHOTOS.get(url);
        if (hit != null || url.isEmpty()) {
            view.setImageBitmap(hit);
            shown.run();
            return;
        }
        POOL.execute(() -> {
            Bitmap b = fetchPhoto(url);
            if (b == null && !fallback.isEmpty()) b = fetchPhoto(fallback);
            if (b == null) {
                MAIN.post(() -> {
                    if (url.equals(view.getTag())) {
                        view.setImageBitmap(null);
                        shown.run();
                    }
                });
                return;
            }
            PHOTOS.put(url, b);
            Bitmap done = b;
            MAIN.post(() -> {
                if (url.equals(view.getTag())) {
                    view.setImageBitmap(done);
                    shown.run();
                }
            });
        });
    }

    private static Bitmap fetchPhoto(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in, null, o);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Blocking fetch for background callers (the alert card). */
    static Bitmap get(String url, int px) {
        String small = Sports.smallLogo(url, px);
        Bitmap hit = CACHE.get(small);
        if (hit != null) return hit;
        Bitmap b = fetch(small);
        if (b != null) CACHE.put(small, b);
        return b;
    }

    private static Bitmap fetch(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
