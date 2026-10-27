package com.xeleo.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/**
 * Home's season themes, drawn over the sky on their days and gone after them: Halloween (29–31 October) with a
 * dusky orange and purple sky, a big full moon, bats crossing and glowing pumpkins on the hills; Día de Muertos (1–2
 * November) with papel picado along the top and cempasúchil petals drifting down. Your team's game day goes first.
 * Like AmbientView: about 30 frames a second, nothing allocated while drawing, stopped when Home isn't showing.
 */
final class SeasonView extends View {

    enum Season { NONE, HALLOWEEN, MUERTOS }

    /** "halloween" or "muertos" to see that theme now (ControlReceiver SEASON_PREVIEW); null for the date's. */
    static volatile String preview;

    static boolean enabled(Context c) {
        return c.getSharedPreferences("profile", Context.MODE_PRIVATE).getBoolean("season", true);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().putBoolean("season", on).apply();
    }

    /** The theme for today, or a preview; NONE when they are switched off. */
    static Season today(Context c) {
        String p = preview;
        if (p != null) return p.equals("muertos") ? Season.MUERTOS : p.equals("halloween") ? Season.HALLOWEEN : Season.NONE;
        if (!enabled(c)) return Season.NONE;
        Calendar now = Calendar.getInstance();
        int m = now.get(Calendar.MONTH), d = now.get(Calendar.DAY_OF_MONTH);
        if (m == Calendar.OCTOBER && d >= 29) return Season.HALLOWEEN;
        if (m == Calendar.NOVEMBER && d <= 2) return Season.MUERTOS;
        return Season.NONE;
    }

    /** What Home's greeting says on the theme's days, in place of "Buenas noches". */
    static String greeting(Season s) {
        return s == Season.HALLOWEEN ? "Feliz Halloween" : s == Season.MUERTOS ? "Feliz Día de Muertos" : null;
    }

    private static final long FRAME_MS = 33;
    private static final int BATS = 7, PETALS = 46, FLAGS = 11;
    private static final int[] PICADO = {0xFFE91E63, 0xFFFF9800, 0xFF8E24AA, 0xFF43A047, 0xFFFFD600, 0xFF1E88E5};
    private static final int[] FLOWER = {0xFFFF9800, 0xFFFFB300, 0xFFFF6F00, 0xFFFFA726};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(31);
    private Season season = Season.NONE;
    private boolean running, built;
    private long lastFrame;
    private float time;
    private Shader sky, moonGlow;

    private final float[] bx = new float[BATS], by = new float[BATS], bv = new float[BATS], bs = new float[BATS],
            bp = new float[BATS];
    private final float[] px = new float[PETALS], py = new float[PETALS], pv = new float[PETALS], pr = new float[PETALS],
            pa = new float[PETALS], pw = new float[PETALS];
    private final int[] pc = new int[PETALS];

    SeasonView(Context c) {
        super(c);
    }

    Season season() {
        return season;
    }

    void setSeason(Season s) {
        if (s == season) return;
        season = s;
        built = false;
        setVisibility(s == Season.NONE ? GONE : VISIBLE);
        invalidate();
    }

    void setRunning(boolean run) {
        running = run;
        lastFrame = 0;
        if (run) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        built = false;
    }

    private void build(int w, int h) {
        built = true;
        if (season == Season.HALLOWEEN) {
            sky = new LinearGradient(0, 0, 0, h * 0.66f, new int[]{0xAA2A0E45, 0x88601A6E, 0x66E8641E, 0x00E8641E},
                    new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP);
            float mx = w * 0.6f, my = h * 0.17f, mr = h * 0.065f;
            moonGlow = new RadialGradient(mx, my, mr * 4f, 0x66FFB74D, 0x00FFB74D, Shader.TileMode.CLAMP);
            for (int i = 0; i < BATS; i++) newBat(i, w, h, true);
        } else if (season == Season.MUERTOS) {
            sky = new LinearGradient(0, 0, 0, h * 0.66f, new int[]{0x66601050, 0x44C2185B, 0x33FF8F00, 0x00FF8F00},
                    new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP);
            for (int i = 0; i < PETALS; i++) newPetal(i, w, h, true);
        }
    }

    private void newBat(int i, int w, int h, boolean anywhere) {
        bs[i] = h * (0.018f + random.nextFloat() * 0.02f);
        bx[i] = anywhere ? random.nextFloat() * w : -bs[i] * 4 - random.nextFloat() * w * 0.6f;
        by[i] = h * (0.12f + random.nextFloat() * 0.34f);
        bv[i] = w * (0.03f + random.nextFloat() * 0.03f);
        bp[i] = random.nextFloat() * 6.28f;
    }

    private void newPetal(int i, int w, int h, boolean anywhere) {
        px[i] = random.nextFloat() * w;
        py[i] = anywhere ? random.nextFloat() * h * 0.62f : -h * (0.02f + random.nextFloat() * 0.2f);
        pv[i] = h * (0.025f + random.nextFloat() * 0.035f);
        pr[i] = h * (0.006f + random.nextFloat() * 0.007f);
        pa[i] = random.nextFloat() * 360;
        pw[i] = random.nextFloat() * 6.28f;
        pc[i] = FLOWER[random.nextInt(FLOWER.length)];
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (season == Season.NONE || w == 0) return;
        if (!built) build(w, h);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;

        paint.setShader(sky);
        c.drawRect(0, 0, w, h * 0.66f, paint);
        paint.setShader(null);
        if (season == Season.HALLOWEEN) drawHalloween(c, w, h, dt);
        else drawMuertos(c, w, h, dt);

        if (running) postInvalidateDelayed(FRAME_MS);
    }

    // ---------------------------------------------------------------- Halloween

    private void drawHalloween(Canvas c, int w, int h, float dt) {
        // The moon, big and warm, with a glow and a few darker seas.
        float mx = w * 0.6f, my = h * 0.17f, mr = h * 0.065f;
        paint.setShader(moonGlow);
        c.drawCircle(mx, my, mr * 4f, paint);
        paint.setShader(null);
        paint.setColor(0xFFFFE6A8);
        c.drawCircle(mx, my, mr, paint);
        paint.setColor(0x22B0703A);
        c.drawCircle(mx - mr * 0.35f, my - mr * 0.2f, mr * 0.22f, paint);
        c.drawCircle(mx + mr * 0.3f, my + mr * 0.25f, mr * 0.3f, paint);
        c.drawCircle(mx + mr * 0.05f, my - mr * 0.45f, mr * 0.12f, paint);

        // Bats crossing, flapping, now and then in front of the moon.
        paint.setColor(0xFF140A1C);
        for (int i = 0; i < BATS; i++) {
            bx[i] += bv[i] * dt;
            if (bx[i] > w + bs[i] * 4) newBat(i, w, h, false);
            float y = by[i] + (float) Math.sin(time * 0.9f + bp[i]) * h * 0.02f;
            bat(c, bx[i], y, bs[i], (float) Math.sin(time * 9f + bp[i]));
        }

        // Pumpkins on the hills, their faces flickering like candles.
        float[] at = {0.07f, 0.24f, 0.78f, 0.93f};
        for (int i = 0; i < at.length; i++) {
            float r = h * (i % 2 == 0 ? 0.02f : 0.016f);
            pumpkin(c, w * at[i], h * 0.625f - r, r, 0.75f + 0.25f * (float) Math.sin(time * 6f + i * 1.7f));
        }
    }

    /** A bat at (x, y), wingspan about 4 s; flap goes from -1 (wings down) to 1 (up). */
    private void bat(Canvas c, float x, float y, float s, float flap) {
        float lift = flap * s * 0.9f;
        path.reset();
        path.moveTo(x, y - s * 0.25f);
        path.quadTo(x - s * 0.8f, y - s * 0.3f - lift, x - s * 2f, y - lift * 1.2f);
        path.quadTo(x - s * 1.5f, y + s * 0.1f - lift * 0.4f, x - s * 1.2f, y + s * 0.35f);
        path.quadTo(x - s * 0.9f, y + s * 0.05f, x - s * 0.55f, y + s * 0.4f);
        path.quadTo(x - s * 0.3f, y + s * 0.15f, x, y + s * 0.45f);
        path.quadTo(x + s * 0.3f, y + s * 0.15f, x + s * 0.55f, y + s * 0.4f);
        path.quadTo(x + s * 0.9f, y + s * 0.05f, x + s * 1.2f, y + s * 0.35f);
        path.quadTo(x + s * 1.5f, y + s * 0.1f - lift * 0.4f, x + s * 2f, y - lift * 1.2f);
        path.quadTo(x + s * 0.8f, y - s * 0.3f - lift, x, y - s * 0.25f);
        path.close();
        c.drawPath(path, paint);
        // Head and ears.
        c.drawCircle(x, y - s * 0.3f, s * 0.28f, paint);
        path.reset();
        path.moveTo(x - s * 0.25f, y - s * 0.4f);
        path.lineTo(x - s * 0.18f, y - s * 0.75f);
        path.lineTo(x - s * 0.05f, y - s * 0.48f);
        path.lineTo(x + s * 0.05f, y - s * 0.48f);
        path.lineTo(x + s * 0.18f, y - s * 0.75f);
        path.lineTo(x + s * 0.25f, y - s * 0.4f);
        path.close();
        c.drawPath(path, paint);
    }

    /** A jack-o'-lantern of radius r centred at (x, y); glow 0..1 for its candle. */
    private void pumpkin(Canvas c, float x, float y, float r, float glow) {
        // Its light on the ground.
        paint.setColor(((int) (70 * glow) << 24) | 0xFFA040);
        c.drawOval(x - r * 2.2f, y + r * 0.5f, x + r * 2.2f, y + r * 1.4f, paint);
        paint.setColor(0xFF2E7D32);
        c.drawRect(x - r * 0.12f, y - r * 1.25f, x + r * 0.12f, y - r * 0.8f, paint);
        paint.setColor(0xFFE65100);
        c.drawOval(x - r * 1.15f, y - r * 0.9f, x + r * 1.15f, y + r * 0.9f, paint);
        paint.setColor(0xFFF57C00);
        c.drawOval(x - r * 0.55f, y - r * 0.92f, x + r * 0.55f, y + r * 0.92f, paint);
        // The face, lit from inside.
        paint.setColor(((int) (255 * glow) << 24) | 0xFFE082);
        path.reset();
        path.moveTo(x - r * 0.55f, y - r * 0.05f);
        path.lineTo(x - r * 0.35f, y - r * 0.42f);
        path.lineTo(x - r * 0.15f, y - r * 0.05f);
        path.close();
        path.moveTo(x + r * 0.15f, y - r * 0.05f);
        path.lineTo(x + r * 0.35f, y - r * 0.42f);
        path.lineTo(x + r * 0.55f, y - r * 0.05f);
        path.close();
        path.moveTo(x - r * 0.6f, y + r * 0.2f);
        path.lineTo(x - r * 0.4f, y + r * 0.45f);
        path.lineTo(x - r * 0.2f, y + r * 0.3f);
        path.lineTo(x, y + r * 0.5f);
        path.lineTo(x + r * 0.2f, y + r * 0.3f);
        path.lineTo(x + r * 0.4f, y + r * 0.45f);
        path.lineTo(x + r * 0.6f, y + r * 0.2f);
        path.lineTo(x, y + r * 0.32f);
        path.close();
        c.drawPath(path, paint);
    }

    // ---------------------------------------------------------------- Día de Muertos

    private void drawMuertos(Canvas c, int w, int h, float dt) {
        // Papel picado: a string under the tabs, from the left edge to just before the clock, flags swaying a little.
        float span = w * 0.79f, y0 = h * 0.15f, sag = h * 0.025f, fw = span / FLAGS, fh = h * 0.07f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(h * 0.002f);
        paint.setColor(0xCCFFFFFF);
        path.reset();
        for (int i = 0; i <= 40; i++) {
            float x = span * i / 40f, y = y0 + sag * (float) Math.sin(Math.PI * x / span);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < FLAGS; i++) {
            float left = i * fw + fw * 0.08f, right = (i + 1) * fw - fw * 0.08f, mid = (left + right) / 2;
            float top = y0 + sag * (float) Math.sin(Math.PI * mid / span);
            c.save();
            c.rotate((float) Math.sin(time * 1.3f + i * 0.8f) * 2.5f, mid, top);
            flag(c, left, top, right, top + fh, PICADO[i % PICADO.length]);
            c.restore();
        }

        // Cempasúchil petals falling, turning, swaying; they fade before the apps.
        for (int i = 0; i < PETALS; i++) {
            py[i] += pv[i] * dt;
            pa[i] += 40 * dt;
            if (py[i] > h * 0.64f) newPetal(i, w, h, false);
            float x = px[i] + (float) Math.sin(time * 0.8f + pw[i]) * h * 0.02f;
            float fade = py[i] > h * 0.52f ? Math.max(0, 1 - (py[i] - h * 0.52f) / (h * 0.12f)) : 1;
            paint.setColor(((int) (230 * fade) << 24) | (pc[i] & 0x00FFFFFF));
            c.save();
            c.rotate(pa[i], x, py[i]);
            c.drawOval(x - pr[i], py[i] - pr[i] * 0.55f, x + pr[i], py[i] + pr[i] * 0.55f, paint);
            c.restore();
        }
    }

    /** One flag of papel picado, with its cut-outs and a scalloped bottom edge. */
    private void flag(Canvas c, float l, float t, float r, float b, int color) {
        float fw = r - l;
        path.reset();
        path.moveTo(l, t);
        path.lineTo(r, t);
        path.lineTo(r, b - fw * 0.08f);
        int teeth = 6;
        for (int k = teeth; k > 0; k--) {
            float x1 = l + fw * k / teeth, x0 = l + fw * (k - 1) / teeth;
            path.lineTo((x0 + x1) / 2, b);
            path.lineTo(x0, b - fw * 0.08f);
        }
        path.close();
        paint.setColor((0xE6 << 24) | (color & 0x00FFFFFF));
        c.drawPath(path, paint);
        // The cut-outs: a flower in the middle, little diamonds around.
        paint.setColor(0x55000000);
        float cx = (l + r) / 2, cy = t + (b - t) * 0.45f, s = fw * 0.12f;
        c.drawCircle(cx, cy, s * 0.9f, paint);
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6;
            c.drawCircle(cx + (float) Math.cos(a) * s * 1.8f, cy + (float) Math.sin(a) * s * 1.8f, s * 0.55f, paint);
        }
        float[][] dots = {{0.2f, 0.18f}, {0.8f, 0.18f}, {0.2f, 0.75f}, {0.8f, 0.75f}};
        for (float[] d : dots) {
            float dx = l + fw * d[0], dy = t + (b - t) * d[1];
            path.reset();
            path.moveTo(dx, dy - s * 0.7f);
            path.lineTo(dx + s * 0.45f, dy);
            path.lineTo(dx, dy + s * 0.7f);
            path.lineTo(dx - s * 0.45f, dy);
            path.close();
            c.drawPath(path, paint);
        }
    }
}
