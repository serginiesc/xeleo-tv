package com.xeleo.tv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

/**
 * A painted mountain landscape for the home background, one per time of day: dawn, day, sunset and night.
 * The phase follows the real sunrise and sunset for the configured coordinates, so the picture changes with
 * the light outside. Drawn once per phase into a bitmap; nothing is downloaded.
 */
final class Scenery {

    enum Phase { DAWN, DAY, SUNSET, NIGHT }

    /** The weather drawn into the landscape. Rain, snow and lightning also move (PrecipView). */
    enum Kind { CLEAR, CLOUDY, OVERCAST, FOG, RAIN, STORM, SNOW }

    /** WMO weather code (Open-Meteo) to what the landscape shows; unknown means clear. */
    static Kind kindOf(int code) {
        if (code < 0 || code == 0) return Kind.CLEAR;
        if (code <= 2) return Kind.CLOUDY;
        if (code == 3) return Kind.OVERCAST;
        if (code <= 48) return Kind.FOG;
        if (code <= 67 || (code >= 80 && code <= 82)) return Kind.RAIN;
        if (code <= 77 || code <= 86) return Kind.SNOW;
        return Kind.STORM;
    }

    static String label(Kind k) {
        switch (k) {
            case CLOUDY: return "Nubes";
            case OVERCAST: return "Nublado";
            case FOG: return "Neblina";
            case RAIN: return "Lluvia";
            case STORM: return "Tormenta";
            case SNOW: return "Nieve";
            default: return "Despejado";
        }
    }

    /** Mixes each colour toward grey: a covered sky loses its colour. */
    private static int[] dull(int[] colors, int grey, float amount) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            int c = colors[i];
            out[i] = Color.argb(Color.alpha(c),
                    (int) (Color.red(c) + (Color.red(grey) - Color.red(c)) * amount),
                    (int) (Color.green(c) + (Color.green(grey) - Color.green(c)) * amount),
                    (int) (Color.blue(c) + (Color.blue(grey) - Color.blue(c)) * amount));
        }
        return out;
    }

    private Scenery() {}

    /** The phase for now at latitude/longitude, from an approximate sunrise and sunset (NOAA formulas). */
    static Phase phaseNow(double lat, double lon) {
        Calendar now = Calendar.getInstance();
        double[] sun = sunTimes(now, lat, lon);
        double h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        if (Double.isNaN(sun[0])) return h >= 6 && h < 19 ? Phase.DAY : Phase.NIGHT;
        double rise = sun[0], set = sun[1];
        if (h >= rise - 0.5 && h < rise + 1.0) return Phase.DAWN;
        if (h >= rise + 1.0 && h < set - 1.0) return Phase.DAY;
        if (h >= set - 1.0 && h < set + 0.5) return Phase.SUNSET;
        return Phase.NIGHT;
    }

    /** Local sunrise and sunset in decimal hours, or NaN in polar day or night. */
    static double[] sunTimes(Calendar day, double lat, double lon) {
        int n = day.get(Calendar.DAY_OF_YEAR);
        double decl = Math.toRadians(23.44) * Math.sin(2 * Math.PI * (284 + n) / 365.0);
        double latR = Math.toRadians(lat);
        // -0.833° accounts for refraction and the sun's radius.
        double cosH = (Math.sin(Math.toRadians(-0.833)) - Math.sin(latR) * Math.sin(decl))
                / (Math.cos(latR) * Math.cos(decl));
        if (cosH < -1 || cosH > 1) return new double[]{Double.NaN, Double.NaN};
        double hours = Math.toDegrees(Math.acos(cosH)) / 15.0;
        double b = 2 * Math.PI * (n - 81) / 364.0;
        double eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b);   // minutes
        double tz = TimeZone.getDefault().getOffset(day.getTimeInMillis()) / 3_600_000.0;
        double noon = 12 - lon / 15.0 - eot / 60.0 + tz;
        return new double[]{noon - hours, noon + hours};
    }

    static String label(Phase p) {
        switch (p) {
            case DAWN: return "Amanecer";
            case DAY: return "Día";
            case SUNSET: return "Atardecer";
            default: return "Noche";
        }
    }

    static Bitmap paint(Phase phase, int w, int h) {
        return paint(phase, Kind.CLEAR, w, h);
    }

    static Bitmap paint(Phase phase, Kind kind, int w, int h) {
        return paint(phase, kind, w, h, true);
    }

    // ---- The moving sky (AmbientView) shares these with the still picture.

    /** How many clouds the sky has for this light and weather. */
    static int cloudCount(Phase phase, Kind kind) {
        if (kind == Kind.CLEAR) return phase == Phase.DAY || phase == Phase.DAWN ? 5 : 0;
        return kind == Kind.CLOUDY ? 8 : 12;
    }

    static boolean covered(Kind kind) {
        return kind != Kind.CLEAR && kind != Kind.CLOUDY;
    }

    static int cloudColor(Phase phase, Kind kind) {
        if (phase == Phase.NIGHT) return 0xFF3A4458;
        if (covered(kind)) return kind == Kind.STORM ? 0xFF4A505A : 0xFFB8C0CA;
        return phase == Phase.DAY ? 0xFFFFFFFF : 0xFFFFE6D6;
    }

    static int cloudAlpha(Phase phase, Kind kind) {
        return phase == Phase.NIGHT ? 70 : covered(kind) ? 150 : phase == Phase.DAY ? 120 : 80;
    }

    /** One cloud: a flat base with three puffs, cw wide, its base line at y = cy. */
    static void drawCloud(Canvas c, Paint p, float cx, float cy, float cw) {
        c.drawRoundRect(cx, cy - cw * 0.02f, cx + cw * 1.15f, cy + cw * 0.11f, cw * 0.07f, cw * 0.07f, p);
        c.drawCircle(cx + cw * 0.30f, cy, cw * 0.13f, p);
        c.drawCircle(cx + cw * 0.55f, cy - cw * 0.05f, cw * 0.19f, p);
        c.drawCircle(cx + cw * 0.82f, cy + cw * 0.01f, cw * 0.12f, p);
    }

    /** staticSky false leaves out the stars and clouds, which AmbientView draws moving. */
    static Bitmap paint(Phase phase, Kind kind, int w, int h, boolean staticSky) {
        return paint(phase, kind, w, h, staticSky, false);
    }

    /** noSun leaves out the sun or moon (on game day, so it doesn't show through the team's photo). */
    static Bitmap paint(Phase phase, Kind kind, int w, int h, boolean staticSky, boolean noSun) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random rnd = new Random(7);
        float horizon = h * 0.62f;

        int[] sky;
        int[] hills;
        int sunColor, glow;
        float sunX, sunY, sunR;
        switch (phase) {
            case DAWN:
                sky = new int[]{0xFF2A2F5E, 0xFF6B5B95, 0xFFE59A84, 0xFFF7D3A1};
                hills = new int[]{0xFF8A7BA8, 0xFF64588A, 0xFF433A68, 0xFF2A2446, 0xFF181429};
                sunColor = 0xFFFFE2B8; glow = 0x88FFC48A; sunX = w * 0.72f; sunY = horizon - h * 0.04f; sunR = h * 0.045f;
                break;
            case DAY:
                sky = new int[]{0xFF2F7FD6, 0xFF5DA4E6, 0xFF9FCDF2, 0xFFDDEFFA};
                hills = new int[]{0xFF8FB3D1, 0xFF5F8DB0, 0xFF3F6F7E, 0xFF2D5547, 0xFF1C3A2F};
                sunColor = 0xFFFFFBE8; glow = 0x66FFF3C4; sunX = w * 0.62f; sunY = h * 0.2f; sunR = h * 0.04f;
                break;
            case SUNSET:
                sky = new int[]{0xFF2B1A4C, 0xFF7A3A6E, 0xFFD9606A, 0xFFF79A54, 0xFFFCD38A};
                hills = new int[]{0xFFB0647A, 0xFF7E3F60, 0xFF552A4A, 0xFF351A33, 0xFF1E0F1F};
                sunColor = 0xFFFFD27A; glow = 0xAAFF9A4D; sunX = w * 0.30f; sunY = horizon - h * 0.02f; sunR = h * 0.07f;
                break;
            default:
                sky = new int[]{0xFF050814, 0xFF0C1430, 0xFF1B2A52, 0xFF2C3E6B};
                hills = new int[]{0xFF2A3A62, 0xFF1D2A4C, 0xFF141E38, 0xFF0D1428, 0xFF080C1A};
                sunColor = 0xFFF1F4FF; glow = 0x55B8C8FF; sunX = w * 0.64f; sunY = h * 0.2f; sunR = h * 0.035f;
                break;
        }

        boolean night = phase == Phase.NIGHT;
        boolean covered = kind != Kind.CLEAR && kind != Kind.CLOUDY;
        if (covered) {
            float amount = kind == Kind.STORM ? 0.75f : kind == Kind.OVERCAST || kind == Kind.SNOW ? 0.55f : 0.6f;
            int grey = night ? 0xFF161A22 : kind == Kind.SNOW ? 0xFF9AA4B0 : 0xFF5E6874;
            sky = dull(sky, grey, amount);
            hills = dull(hills, night ? 0xFF0E1118 : 0xFF3A4048, amount * 0.6f);
        }

        p.setShader(new LinearGradient(0, 0, 0, horizon + h * 0.1f, sky, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        if (staticSky && night && !covered) {
            for (int i = 0; i < 260; i++) {
                float x = rnd.nextFloat() * w;
                float y = rnd.nextFloat() * horizon * 0.9f;
                float r = 0.6f + rnd.nextFloat() * (rnd.nextInt(12) == 0 ? 2.2f : 1.1f);
                p.setColor(Color.argb(90 + rnd.nextInt(165), 255, 255, 255));
                c.drawCircle(x, y, r, p);
            }
        }

        // Sun or moon with a soft glow; behind a covered sky only a faint glow is left.
        if (!noSun) {
            p.setShader(new RadialGradient(sunX, sunY, sunR * 7, covered ? (glow & 0x00FFFFFF) | 0x22000000 : glow,
                    0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(sunX, sunY, sunR * 7, p);
            p.setShader(null);
        }
        p.setColor(covered || noSun ? 0x00000000 : sunColor);
        if (phase == Phase.NIGHT) {   // crescent: the moon minus an offset disc, so the sky shows through
            Path moon = new Path();
            moon.addCircle(sunX, sunY, sunR, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(sunX + sunR * 0.45f, sunY - sunR * 0.25f, sunR * 0.92f, Path.Direction.CW);
            moon.op(bite, Path.Op.DIFFERENCE);
            c.drawPath(moon, p);
        } else {
            c.drawCircle(sunX, sunY, sunR, p);
        }

        int clouds = staticSky ? cloudCount(phase, kind) : 0;
        if (clouds > 0) {
            // Each cloud is drawn opaque into its own layer and then faded as one piece, so the puffs
            // merge into a single soft shape instead of showing every overlap.
            int alpha = cloudAlpha(phase, kind);
            p.setColor(cloudColor(phase, kind));
            p.setMaskFilter(new android.graphics.BlurMaskFilter(h * 0.012f, android.graphics.BlurMaskFilter.Blur.NORMAL));
            for (int i = 0; i < clouds; i++) {
                float cx = w * (-0.05f + (1.0f / clouds) * i * 1.05f + rnd.nextFloat() * 0.06f);
                float cy = h * (0.08f + rnd.nextFloat() * (covered ? 0.3f : 0.22f));
                float cw = w * (0.10f + rnd.nextFloat() * 0.08f);
                c.saveLayerAlpha(cx - cw * 0.2f, cy - cw * 0.4f, cx + cw * 1.4f, cy + cw * 0.3f, alpha);
                drawCloud(c, p, cx, cy, cw);
                c.restore();
            }
            p.setMaskFilter(null);
        }

        // Mountain ranges, far to near; each nearer one darker and taller, with a little mist between.
        for (int layer = 0; layer < hills.length; layer++) {
            float base = horizon + layer * h * 0.075f;
            float amp = h * (0.10f + layer * 0.035f);
            Path path = new Path();
            path.moveTo(0, h);
            double s1 = rnd.nextDouble() * 10, s2 = rnd.nextDouble() * 10, s3 = rnd.nextDouble() * 10;
            double f = 1.6 + layer * 0.5;
            for (int x = 0; x <= w; x += 6) {
                double t = (double) x / w;
                double y = Math.sin(t * Math.PI * f + s1) * 0.55
                        + Math.sin(t * Math.PI * f * 2.3 + s2) * 0.28
                        + Math.abs(Math.sin(t * Math.PI * f * 5.1 + s3)) * 0.17;
                path.lineTo(x, (float) (base - amp * (0.35 + y * 0.5)));
            }
            path.lineTo(w, h);
            path.close();
            p.setShader(null);
            p.setColor(hills[layer]);
            c.drawPath(path, p);
            if (layer < hills.length - 1) {
                int mist = phase == Phase.NIGHT ? 0x22A0B4E0 : 0x33FFFFFF;
                // Soft on both edges, so the mist reads as haze rather than a band.
                float top = base - amp * 0.45f, bottom = base + h * 0.05f;
                p.setShader(new LinearGradient(0, top, 0, bottom, new int[]{mist & 0x00FFFFFF, mist, mist & 0x00FFFFFF},
                        new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, top, w, bottom, p);
            }
        }

        if (kind == Kind.FOG) {   // a thick haze over the valleys
            p.setShader(new LinearGradient(0, horizon - h * 0.2f, 0, h, new int[]{0x00FFFFFF,
                    night ? 0x88505C70 : 0xAAD8DDE2, night ? 0x66404A5C : 0x88C8CED4}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, horizon - h * 0.2f, w, h, p);
            p.setShader(null);
        }

        // Shade the top and bottom so text and cards stay readable on any phase.
        p.setShader(new LinearGradient(0, 0, 0, h * 0.3f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.3f, p);
        p.setShader(new LinearGradient(0, h * 0.55f, 0, h, 0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.55f, w, h, p);
        return bmp;
    }
}
