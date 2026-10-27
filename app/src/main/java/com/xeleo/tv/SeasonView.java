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
 * Home's season themes, Mexican and American, drawn over the sky on their days and gone after them. Your team's game
 * day goes first. All drawn, no pictures; like AmbientView: about 30 frames a second, nothing allocated while drawing,
 * stopped when Home isn't showing.
 *   Halloween (29–31 Oct): dusky sky, a full moon, bats, glowing pumpkins on the hills.
 *   Día de Muertos (1–2 Nov): papel picado, cempasúchil petals falling.
 *   Navidad (20–25 Dec): Christmas lights strung across, snow falling.
 *   Año Nuevo (31 Dec – 1 Jan): fireworks, gold confetti.
 *   San Valentín (14 Feb): hearts floating up.
 *   Primavera (20–21 Mar): flowers on the hills, blossom petals in the air.
 *   Día de las Madres (10 May in Mexico, the US's second Sunday in May): roses on the hills, a few hearts.
 *   4 de Julio: red, white and blue fireworks and stars.
 *   Fiestas Patrias (15–16 Sep): green, white and red papel picado and fireworks.
 */
final class SeasonView extends View {

    enum Season { NONE, HALLOWEEN, MUERTOS, NAVIDAD, ANIO_NUEVO, VALENTIN, PRIMAVERA, MADRES, JULIO4, PATRIAS }

    /** A season's name for SEASON_PREVIEW ("halloween", "navidad"…), lower case without the underscore. */
    private static Season named(String p) {
        for (Season s : Season.values()) if (s.name().replace("_", "").equalsIgnoreCase(p)) return s;
        return Season.NONE;
    }

    /** A season's name to see it now (ControlReceiver SEASON_PREVIEW); null for the date's. */
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
        if (p != null) return named(p);
        if (!enabled(c)) return Season.NONE;
        return on(Calendar.getInstance());
    }

    static Season on(Calendar day) {
        int m = day.get(Calendar.MONTH), d = day.get(Calendar.DAY_OF_MONTH);
        switch (m) {
            case Calendar.OCTOBER: if (d >= 29) return Season.HALLOWEEN; break;
            case Calendar.NOVEMBER: if (d <= 2) return Season.MUERTOS; break;
            case Calendar.DECEMBER:
                if (d >= 20 && d <= 25) return Season.NAVIDAD;
                if (d == 31) return Season.ANIO_NUEVO;
                break;
            case Calendar.JANUARY: if (d == 1) return Season.ANIO_NUEVO; break;
            case Calendar.FEBRUARY: if (d == 14) return Season.VALENTIN; break;
            case Calendar.MARCH: if (d == 20 || d == 21) return Season.PRIMAVERA; break;
            case Calendar.MAY:
                // Mexico on the 10th; the US on the second Sunday.
                if (d == 10) return Season.MADRES;
                if (day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && d >= 8 && d <= 14) return Season.MADRES;
                break;
            case Calendar.JULY: if (d == 4) return Season.JULIO4; break;
            case Calendar.SEPTEMBER: if (d == 15 || d == 16) return Season.PATRIAS; break;
        }
        return Season.NONE;
    }

    /** What Home's greeting says on the theme's days, in place of "Buenas noches". */
    static String greeting(Season s) {
        switch (s) {
            case HALLOWEEN: return "Feliz Halloween";
            case MUERTOS: return "Feliz Día de Muertos";
            case NAVIDAD: return "Feliz Navidad";
            case ANIO_NUEVO: {
                Calendar now = Calendar.getInstance();
                int year = now.get(Calendar.YEAR) + (now.get(Calendar.MONTH) == Calendar.DECEMBER ? 1 : 0);
                return "Feliz " + year;
            }
            case VALENTIN: return "Feliz San Valentín";
            case PRIMAVERA: return "Feliz primavera";
            case MADRES: return "Feliz Día de las Madres";
            case JULIO4: return "Feliz 4 de Julio";
            case PATRIAS: return "¡Viva México";   // the greeting adds ", Sergio"; the "!" goes after (see suffix)
            default: return null;
        }
    }

    /** What goes after the name, for greetings that open with "¡". */
    static String suffix(Season s) {
        return s == Season.PATRIAS ? "!" : "";
    }

    private static final int BATS = 7, BITS = 60, FLAGS = 11, BURSTS = 4, SPARKS = 36, BULBS = 26;
    private static final int[] PICADO = {0xFFE91E63, 0xFFFF9800, 0xFF8E24AA, 0xFF43A047, 0xFFFFD600, 0xFF1E88E5};
    private static final int[] TRICOLOR = {0xFF006847, 0xFFF5F5F5, 0xFFCE1126};
    private static final int[] FLOWER = {0xFFFF9800, 0xFFFFB300, 0xFFFF6F00, 0xFFFFA726};
    private static final int[] BLOSSOM = {0xFFF8BBD0, 0xFFFCE4EC, 0xFFF48FB1, 0xFFFFFFFF};
    private static final int[] HEART = {0xFFE91E63, 0xFFF06292, 0xFFD81B60, 0xFFFF8A80};
    private static final int[] LIGHTS = {0xFFFF5252, 0xFF69F0AE, 0xFFFFD740, 0xFF40C4FF, 0xFFFF80AB};
    private static final int[] USA = {0xFFE53935, 0xFFFFFFFF, 0xFF1E88E5};
    private static final int[] GOLD = {0xFFFFD54F, 0xFFFFECB3, 0xFFFFC107, 0xFFFFFFFF};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random(31);
    private Season season = Season.NONE;
    private boolean running, built;
    private long lastFrame;
    private float time;
    private Shader sky, moonGlow;

    // Bats (Halloween).
    private final float[] bx = new float[BATS], by = new float[BATS], bv = new float[BATS], bs = new float[BATS],
            bp = new float[BATS];
    // Bits that fall or rise: petals, snow, hearts, confetti.
    private final float[] px = new float[BITS], py = new float[BITS], pv = new float[BITS], pr = new float[BITS],
            pa = new float[BITS], pw = new float[BITS];
    private final int[] pc = new int[BITS];
    private int bits;
    private int[] bitColors = FLOWER;
    /** 0 oval petal, 1 snowflake dot, 2 heart, 3 confetti strip; rising bits go up. */
    private int bitShape;
    private boolean rising;
    // Fireworks: bursts of sparks.
    private final float[] fx = new float[BURSTS * SPARKS], fy = new float[BURSTS * SPARKS], fvx = new float[BURSTS * SPARKS],
            fvy = new float[BURSTS * SPARKS];
    private final float[] burstAge = new float[BURSTS];
    private final int[] burstColor = new int[BURSTS];
    private int[] fireColors;
    private float nextBurst;

    private final boolean light;
    private final long frameMs;

    SeasonView(Context c) {
        super(c);
        light = Perf.light(c);
        frameMs = Perf.frameMs(c);
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

    private static Shader tint(int h, int top, int mid, int low) {
        return new LinearGradient(0, 0, 0, h * 0.66f, new int[]{top, mid, low, low & 0x00FFFFFF},
                new float[]{0f, 0.45f, 0.85f, 1f}, Shader.TileMode.CLAMP);
    }

    private void build(int w, int h) {
        built = true;
        bits = 0;
        fireColors = null;
        rising = false;
        switch (season) {
            case HALLOWEEN:
                sky = tint(h, 0xAA2A0E45, 0x88601A6E, 0x66E8641E);
                moonGlow = new RadialGradient(w * 0.6f, h * 0.17f, h * 0.26f, 0x66FFB74D, 0x00FFB74D, Shader.TileMode.CLAMP);
                for (int i = 0; i < BATS; i++) newBat(i, w, h, true);
                break;
            case MUERTOS:
                sky = tint(h, 0x66601050, 0x44C2185B, 0x33FF8F00);
                bits(46, FLOWER, 0, false);
                break;
            case NAVIDAD:
                sky = tint(h, 0x880A1A3A, 0x55123266, 0x33335C99);
                bits(60, new int[]{0xFFFFFFFF, 0xEEFFFFFF, 0xCCE3F2FD}, 1, false);
                break;
            case ANIO_NUEVO:
                sky = tint(h, 0x99050A1E, 0x66101838, 0x33332255);
                fireColors = GOLD;
                bits(30, GOLD, 3, false);
                break;
            case VALENTIN:
                sky = tint(h, 0x66880E4F, 0x44D81B60, 0x22F48FB1);
                bits(26, HEART, 2, true);
                break;
            case PRIMAVERA:
                sky = tint(h, 0x334FC3F7, 0x22AED581, 0x22F8BBD0);
                bits(40, BLOSSOM, 0, false);
                break;
            case MADRES:
                sky = tint(h, 0x44AD1457, 0x33F06292, 0x22FFCCBC);
                bits(14, HEART, 2, true);
                break;
            case JULIO4:
                sky = tint(h, 0x880D1B4C, 0x55283593, 0x33B71C1C);
                fireColors = USA;
                break;
            case PATRIAS:
                sky = tint(h, 0x66003D29, 0x44006847, 0x33CE1126);
                fireColors = TRICOLOR;
                break;
            default:
                sky = null;
        }
        for (int i = 0; i < bits; i++) newBit(i, w, h, true);
        for (int b = 0; b < BURSTS; b++) burstAge[b] = 99;
        nextBurst = 0.5f;
    }

    private void bits(int n, int[] colors, int shape, boolean up) {
        bits = light ? n / 2 : n;
        bitColors = colors;
        bitShape = shape;
        rising = up;
    }

    private void newBat(int i, int w, int h, boolean anywhere) {
        bs[i] = h * (0.018f + random.nextFloat() * 0.02f);
        bx[i] = anywhere ? random.nextFloat() * w : -bs[i] * 4 - random.nextFloat() * w * 0.6f;
        by[i] = h * (0.12f + random.nextFloat() * 0.34f);
        bv[i] = w * (0.03f + random.nextFloat() * 0.03f);
        bp[i] = random.nextFloat() * 6.28f;
    }

    private void newBit(int i, int w, int h, boolean anywhere) {
        px[i] = random.nextFloat() * w;
        if (rising) py[i] = anywhere ? h * (0.1f + random.nextFloat() * 0.52f) : h * (0.64f + random.nextFloat() * 0.05f);
        else py[i] = anywhere ? random.nextFloat() * h * 0.62f : -h * (0.02f + random.nextFloat() * 0.2f);
        pv[i] = h * (0.025f + random.nextFloat() * 0.035f);
        pr[i] = h * (bitShape == 2 ? 0.012f + random.nextFloat() * 0.01f : 0.005f + random.nextFloat() * 0.007f);
        pa[i] = random.nextFloat() * 360;
        pw[i] = random.nextFloat() * 6.28f;
        pc[i] = bitColors[random.nextInt(bitColors.length)];
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

        if (sky != null) {
            paint.setShader(sky);
            c.drawRect(0, 0, w, h * 0.66f, paint);
            paint.setShader(null);
        }
        switch (season) {
            case HALLOWEEN: drawHalloween(c, w, h, dt); break;
            case MUERTOS: garlandFlags(c, w, h, PICADO); break;
            case NAVIDAD: garlandLights(c, w, h); break;
            case PRIMAVERA: flowers(c, w, h, BLOSSOM, false); break;
            case MADRES: flowers(c, w, h, new int[]{0xFFE53935, 0xFFF06292, 0xFFFFFFFF}, true); break;
            case JULIO4: stars(c, w, h); break;
            case PATRIAS: garlandFlags(c, w, h, TRICOLOR); break;
            default: break;
        }
        if (fireColors != null) fireworks(c, w, h, dt);
        if (bits > 0) drawBits(c, w, h, dt);

        if (running) postInvalidateDelayed(frameMs);
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
        paint.setColor(((int) (70 * glow) << 24) | 0xFFA040);
        c.drawOval(x - r * 2.2f, y + r * 0.5f, x + r * 2.2f, y + r * 1.4f, paint);
        paint.setColor(0xFF2E7D32);
        c.drawRect(x - r * 0.12f, y - r * 1.25f, x + r * 0.12f, y - r * 0.8f, paint);
        paint.setColor(0xFFE65100);
        c.drawOval(x - r * 1.15f, y - r * 0.9f, x + r * 1.15f, y + r * 0.9f, paint);
        paint.setColor(0xFFF57C00);
        c.drawOval(x - r * 0.55f, y - r * 0.92f, x + r * 0.55f, y + r * 0.92f, paint);
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

    // ---------------------------------------------------------------- garlands

    /** The string under the tabs, from the left edge to just before the clock; y at x. */
    private float stringY(float x, float span, int h) {
        return h * 0.15f + h * 0.025f * (float) Math.sin(Math.PI * x / span);
    }

    private void drawString(Canvas c, float span, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(h * 0.002f);
        paint.setColor(0xCCFFFFFF);
        path.reset();
        for (int i = 0; i <= 40; i++) {
            float x = span * i / 40f, y = stringY(x, span, h);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Papel picado on the string, flags swaying a little, in these colours. */
    private void garlandFlags(Canvas c, int w, int h, int[] colors) {
        float span = w * 0.79f, fw = span / FLAGS, fh = h * 0.07f;
        drawString(c, span, h);
        for (int i = 0; i < FLAGS; i++) {
            float left = i * fw + fw * 0.08f, right = (i + 1) * fw - fw * 0.08f, mid = (left + right) / 2;
            float top = stringY(mid, span, h);
            c.save();
            c.rotate((float) Math.sin(time * 1.3f + i * 0.8f) * 2.5f, mid, top);
            flag(c, left, top, right, top + fh, colors[i % colors.length]);
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

    /** Christmas lights on the string, each glowing and blinking slowly in its own time. */
    private void garlandLights(Canvas c, int w, int h) {
        float span = w * 0.79f, r = h * 0.009f;
        drawString(c, span, h);
        for (int i = 0; i < BULBS; i++) {
            float x = span * (i + 0.5f) / BULBS, y = stringY(x, span, h) + r * 1.6f;
            float on = 0.55f + 0.45f * (float) Math.sin(time * 2.2f + i * 1.9f);
            int col = LIGHTS[i % LIGHTS.length];
            paint.setColor(((int) (90 * on) << 24) | (col & 0x00FFFFFF));
            c.drawCircle(x, y, r * 2.6f, paint);
            paint.setColor(((int) (140 + 115 * on) << 24) | (col & 0x00FFFFFF));
            c.drawOval(x - r * 0.75f, y - r, x + r * 0.75f, y + r * 1.1f, paint);
            paint.setColor(0xFF37474F);
            c.drawRect(x - r * 0.4f, y - r * 1.5f, x + r * 0.4f, y - r * 0.85f, paint);
        }
    }

    // ---------------------------------------------------------------- flowers, stars, fireworks

    /** Flowers along the hills (or roses, when roses is true). */
    private void flowers(Canvas c, int w, int h, int[] colors, boolean roses) {
        float[] at = {0.04f, 0.12f, 0.21f, 0.3f, 0.7f, 0.79f, 0.88f, 0.96f};
        for (int i = 0; i < at.length; i++) {
            float r = h * (i % 2 == 0 ? 0.012f : 0.009f), x = w * at[i];
            float y = h * 0.625f - r * 2.2f + (float) Math.sin(time * 1.5f + i) * r * 0.15f;
            paint.setColor(0xFF2E7D32);
            c.drawRect(x - r * 0.12f, y, x + r * 0.12f, h * 0.632f, paint);
            c.drawOval(x - r * 0.9f, y + r * 1.2f, x, y + r * 1.6f, paint);
            int col = colors[i % colors.length];
            if (roses) {
                paint.setColor(col);
                c.drawCircle(x, y, r, paint);
                paint.setColor(0x33000000);
                c.drawCircle(x - r * 0.2f, y - r * 0.1f, r * 0.55f, paint);
                paint.setColor(col);
                c.drawCircle(x + r * 0.05f, y + r * 0.05f, r * 0.35f, paint);
            } else {
                paint.setColor(col);
                for (int k = 0; k < 5; k++) {
                    double a = Math.PI * 2 * k / 5 + time * 0.2;
                    c.drawCircle(x + (float) Math.cos(a) * r * 0.7f, y + (float) Math.sin(a) * r * 0.7f, r * 0.55f, paint);
                }
                paint.setColor(0xFFFFD54F);
                c.drawCircle(x, y, r * 0.4f, paint);
            }
        }
    }

    /** A few white stars twinkling high in the sky, as on the flag. */
    private void stars(Canvas c, int w, int h) {
        float[][] at = {{0.08f, 0.2f}, {0.2f, 0.32f}, {0.36f, 0.18f}, {0.62f, 0.3f}, {0.76f, 0.38f}, {0.9f, 0.42f}};
        for (int i = 0; i < at.length; i++) {
            float x = w * at[i][0], y = h * at[i][1], r = h * 0.012f * (0.8f + 0.2f * (float) Math.sin(time * 3 + i));
            paint.setColor(0xDDFFFFFF);
            path.reset();
            for (int k = 0; k < 10; k++) {
                double a = -Math.PI / 2 + Math.PI * k / 5;
                float rr = k % 2 == 0 ? r : r * 0.42f;
                float sx = x + (float) Math.cos(a) * rr, sy = y + (float) Math.sin(a) * rr;
                if (k == 0) path.moveTo(sx, sy);
                else path.lineTo(sx, sy);
            }
            path.close();
            c.drawPath(path, paint);
        }
    }

    /** Fireworks bursting high in the sky in the theme's colours, one about every second. */
    private void fireworks(Canvas c, int w, int h, float dt) {
        nextBurst -= dt;
        if (nextBurst <= 0) {
            for (int b = 0; b < BURSTS; b++) {
                if (burstAge[b] < 1.8f) continue;
                launch(b, w, h);
                break;
            }
            nextBurst = 0.7f + random.nextFloat() * 0.8f;
        }
        for (int b = 0; b < BURSTS; b++) {
            if (burstAge[b] >= 1.8f) continue;
            burstAge[b] += dt;
            float fade = Math.max(0, 1 - burstAge[b] / 1.8f);
            int col = burstColor[b];
            for (int k = b * SPARKS; k < (b + 1) * SPARKS; k++) {
                fvy[k] += h * 0.12f * dt;   // a little gravity
                fvx[k] *= 1 - 0.9f * dt;
                fvy[k] *= 1 - 0.9f * dt;
                fx[k] += fvx[k] * dt;
                fy[k] += fvy[k] * dt;
                paint.setColor(((int) (255 * fade) << 24) | (col & 0x00FFFFFF));
                c.drawCircle(fx[k], fy[k], h * 0.0035f * (0.6f + fade), paint);
            }
        }
    }

    private void launch(int b, int w, int h) {
        burstAge[b] = 0;
        burstColor[b] = fireColors[random.nextInt(fireColors.length)];
        // High in the sky, clear of the greeting in the middle.
        float x = w * (random.nextBoolean() ? 0.08f + random.nextFloat() * 0.25f : 0.67f + random.nextFloat() * 0.25f);
        float y = h * (0.12f + random.nextFloat() * 0.25f), speed = h * (0.16f + random.nextFloat() * 0.08f);
        for (int k = b * SPARKS; k < (b + 1) * SPARKS; k++) {
            double a = Math.PI * 2 * (k - b * SPARKS) / SPARKS + random.nextFloat() * 0.1;
            float s = speed * (0.7f + random.nextFloat() * 0.3f);
            fx[k] = x;
            fy[k] = y;
            fvx[k] = (float) Math.cos(a) * s;
            fvy[k] = (float) Math.sin(a) * s;
        }
    }

    // ---------------------------------------------------------------- falling and rising bits

    private void drawBits(Canvas c, int w, int h, float dt) {
        for (int i = 0; i < bits; i++) {
            py[i] += (rising ? -0.6f : 1f) * pv[i] * dt;
            pa[i] += 40 * dt;
            if (rising ? py[i] < h * 0.08f : py[i] > h * 0.64f) newBit(i, w, h, false);
            float x = px[i] + (float) Math.sin(time * 0.8f + pw[i]) * h * 0.02f;
            float fade;
            if (rising) fade = Math.min(1, (py[i] - h * 0.08f) / (h * 0.12f)) * Math.min(1, (h * 0.66f - py[i]) / (h * 0.06f));
            else fade = py[i] > h * 0.52f ? 1 - (py[i] - h * 0.52f) / (h * 0.12f) : 1;
            fade = Math.max(0, Math.min(1, fade));
            paint.setColor(((int) (230 * fade) << 24) | (pc[i] & 0x00FFFFFF));
            float r = pr[i];
            switch (bitShape) {
                case 1:   // snow
                    c.drawCircle(x, py[i], r * 0.6f, paint);
                    break;
                case 2:   // heart
                    heart(c, x, py[i], r);
                    break;
                case 3:   // confetti strip
                    c.save();
                    c.rotate(pa[i] * 2, x, py[i]);
                    c.drawRect(x - r, py[i] - r * 0.35f, x + r, py[i] + r * 0.35f, paint);
                    c.restore();
                    break;
                default:  // petal
                    c.save();
                    c.rotate(pa[i], x, py[i]);
                    c.drawOval(x - r, py[i] - r * 0.55f, x + r, py[i] + r * 0.55f, paint);
                    c.restore();
            }
        }
    }

    private void heart(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y + r * 0.9f);
        path.cubicTo(x - r * 1.4f, y - r * 0.1f, x - r * 0.6f, y - r * 1.1f, x, y - r * 0.35f);
        path.cubicTo(x + r * 0.6f, y - r * 1.1f, x + r * 1.4f, y - r * 0.1f, x, y + r * 0.9f);
        path.close();
        c.drawPath(path, paint);
    }
}
