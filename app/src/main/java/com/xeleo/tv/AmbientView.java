package com.xeleo.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The living part of the sky, over the still landscape: stars that twinkle and drift slowly at night with a shooting
 * star now and then (and, rarely, a slow comet), clouds that sail across, and a few birds at dawn, by day and at
 * sunset. Calm on purpose: about 30 frames a second, nothing allocated while drawing, and it stops whenever
 * the screen it is on is not showing (setRunning).
 */
final class AmbientView extends View {

    private static final int STARS = 220;
    private static final long FRAME_MS = 33;

    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path bird = new Path();

    private Scenery.Phase phase = Scenery.Phase.NIGHT;
    private Scenery.Kind kind = Scenery.Kind.CLEAR;
    private boolean running;
    private boolean built;
    private long lastFrame;
    private float time;

    // Stars: position, size, brightness and twinkle; the whole field drifts slowly to the right.
    private boolean starsOn;
    private final float[] sx = new float[STARS], sy = new float[STARS], sr = new float[STARS];
    private final float[] sa = new float[STARS], sf = new float[STARS], sp = new float[STARS];
    private float starDrift;

    // Clouds: pre-drawn soft bitmaps that slide across.
    private Bitmap[] cloudBmp = new Bitmap[0];
    private float[] cx = new float[0], cy = new float[0], cv = new float[0];
    private int cloudAlpha;

    // Shooting star or comet: start point, direction, progress.
    private float meteorX, meteorY, meteorAngle, meteorLen, meteorT = -1, meteorDur;
    private boolean comet;
    private long nextMeteor;
    private Shader meteorTail;
    private Shader cometHead;

    // A small flock of birds crossing.
    private float birdsX = -1, birdsY, birdsV;
    private long nextBirds;

    /** Light mode (Perf): fewer stars and clouds, no comets, fewer frames. */
    private final boolean light;
    private final long frameMs;
    private final int stars;

    AmbientView(Context context) {
        super(context);
        light = Perf.light(context);
        frameMs = Perf.frameMs(context);
        stars = light ? STARS / 2 : STARS;
    }

    /** The light and weather to show; rebuilds the sky when either changes. */
    void setScene(Scenery.Phase p, Scenery.Kind k) {
        if (p == phase && k == kind && built) return;
        phase = p;
        kind = k;
        built = false;
        invalidate();
    }

    /** Animate only while the screen this is on is showing. */
    void setRunning(boolean run) {
        running = run;
        lastFrame = 0;
        if (run) invalidate();
    }

    private void build(int w, int h) {
        built = true;
        starsOn = phase == Scenery.Phase.NIGHT && !Scenery.covered(kind);
        for (int i = 0; i < stars; i++) {
            sx[i] = random.nextFloat() * w;
            sy[i] = random.nextFloat() * h * 0.5f;   // above the mountain tops
            sr[i] = 0.6f + random.nextFloat() * (random.nextInt(12) == 0 ? 2.2f : 1.1f);
            sa[i] = 90 + random.nextInt(165);
            sf[i] = 0.6f + random.nextFloat() * 2.2f;   // twinkle speed
            sp[i] = random.nextFloat() * 6.28f;
        }

        for (Bitmap b : cloudBmp) if (b != null) b.recycle();
        int n = light ? (Scenery.cloudCount(phase, kind) + 1) / 2 : Scenery.cloudCount(phase, kind);
        cloudBmp = new Bitmap[n];
        cx = new float[n];
        cy = new float[n];
        cv = new float[n];
        cloudAlpha = Scenery.cloudAlpha(phase, kind);
        Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
        cp.setColor(Scenery.cloudColor(phase, kind));
        cp.setMaskFilter(new BlurMaskFilter(h * 0.012f, BlurMaskFilter.Blur.NORMAL));
        boolean covered = Scenery.covered(kind);
        for (int i = 0; i < n; i++) {
            float cw = w * (0.10f + random.nextFloat() * 0.08f);
            int bw = (int) (cw * 1.5f), bh = (int) (cw * 0.62f);
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Scenery.drawCloud(new Canvas(b), cp, cw * 0.15f, cw * 0.38f, cw);
            cloudBmp[i] = b;
            cx[i] = -bw + random.nextFloat() * (w + bw);
            cy[i] = h * (0.08f + random.nextFloat() * (covered ? 0.3f : 0.22f)) - cw * 0.38f;
            // A crossing takes two to five minutes; heavier weather moves a little faster.
            cv[i] = w * (0.0035f + random.nextFloat() * 0.0045f) * (kind == Scenery.Kind.STORM ? 2.2f : covered ? 1.4f : 1f);
        }

        meteorT = -1;
        nextMeteor = SystemClock.uptimeMillis() + 8_000 + random.nextInt(20_000);
        birdsX = -1;
        nextBirds = SystemClock.uptimeMillis() + 15_000 + random.nextInt(40_000);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!built) build(w, h);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        time += dt;

        if (starsOn) drawStars(canvas, w, dt);
        drawClouds(canvas, w, dt);
        if (starsOn) drawMeteor(canvas, w, h, now, dt);
        if (birdsWanted()) drawBirds(canvas, w, h, now, dt);

        if (running && isAttachedToWindow() && getWindowVisibility() == VISIBLE) postInvalidateDelayed(frameMs);
    }

    private void drawStars(Canvas canvas, int w, float dt) {
        starDrift = (starDrift + w * 0.0006f * dt) % w;   // one screen width in about half an hour
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < stars; i++) {
            float x = sx[i] + starDrift;
            if (x > w) x -= w;
            float twinkle = 0.65f + 0.35f * (float) Math.sin(time * sf[i] + sp[i]);
            paint.setColor(((int) (sa[i] * twinkle) << 24) | 0xFFFFFF);
            canvas.drawCircle(x, sy[i], sr[i], paint);
        }
    }

    private void drawClouds(Canvas canvas, int w, float dt) {
        bitmapPaint.setAlpha(cloudAlpha);
        for (int i = 0; i < cloudBmp.length; i++) {
            cx[i] += cv[i] * dt;
            if (cx[i] > w) cx[i] = -cloudBmp[i].getWidth();
            canvas.drawBitmap(cloudBmp[i], cx[i], cy[i], bitmapPaint);
        }
    }

    private void drawMeteor(Canvas canvas, int w, int h, long now, float dt) {
        if (meteorT < 0) {
            if (now < nextMeteor) return;
            comet = !light && random.nextInt(4) == 0;   // now and then a slow comet instead of a quick shooting star
            meteorX = w * (0.15f + random.nextFloat() * 0.7f);
            meteorY = h * (0.04f + random.nextFloat() * 0.2f);
            boolean left = random.nextBoolean();
            meteorAngle = (float) Math.toRadians(left ? 155 + random.nextInt(15) : 15 + random.nextInt(15));
            meteorLen = w * (comet ? 0.09f : 0.13f);
            meteorDur = comet ? 6f : 1.1f;
            meteorTail = new LinearGradient(-meteorLen, 0, 0, 0, 0x00FFFFFF, comet ? 0xAAE6EEFF : 0xEEFFFFFF,
                    Shader.TileMode.CLAMP);
            cometHead = new RadialGradient(0, 0, h * 0.012f, 0xFFFFFFFF, 0x00B8C8FF, Shader.TileMode.CLAMP);
            meteorT = 0;
        }
        meteorT += dt;
        float k = meteorT / meteorDur;
        if (k >= 1) {
            meteorT = -1;
            nextMeteor = now + (comet ? 120_000 : 30_000) + random.nextInt(90_000);
            return;
        }
        float travel = w * (comet ? 0.25f : 0.35f) * k;
        float fade = k < 0.15f ? k / 0.15f : k > 0.75f ? (1 - k) / 0.25f : 1f;
        canvas.save();
        canvas.translate(meteorX + (float) Math.cos(meteorAngle) * travel, meteorY + (float) Math.sin(meteorAngle) * travel);
        canvas.rotate((float) Math.toDegrees(meteorAngle));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(comet ? h * 0.004f : h * 0.0022f);
        paint.setShader(meteorTail);
        paint.setAlpha((int) (255 * fade));
        canvas.drawLine(-meteorLen, 0, 0, 0, paint);
        if (comet) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(cometHead);
            canvas.drawCircle(0, 0, h * 0.012f, paint);
        }
        paint.setShader(null);
        paint.setAlpha(255);
        canvas.restore();
    }

    private boolean birdsWanted() {
        return phase != Scenery.Phase.NIGHT && (kind == Scenery.Kind.CLEAR || kind == Scenery.Kind.CLOUDY);
    }

    private void drawBirds(Canvas canvas, int w, int h, long now, float dt) {
        if (birdsX < 0) {
            if (now < nextBirds) return;
            birdsX = 0.0001f;
            birdsY = h * (0.18f + random.nextFloat() * 0.18f);
            birdsV = w * (0.035f + random.nextFloat() * 0.02f);   // about half a minute across
        }
        birdsX += birdsV * dt;
        if (birdsX > w * 1.15f) {
            birdsX = -1;
            nextBirds = now + 60_000 + random.nextInt(120_000);
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, h * 0.0018f));
        paint.setColor(phase == Scenery.Phase.DAY ? 0x99304050 : 0xAA2A1A30);
        float s = h * 0.009f;
        // A loose V of five, each flapping a little out of step.
        float[][] at = {{0, 0}, {-1.6f, -0.9f}, {-1.6f, 0.9f}, {-3.1f, -1.7f}, {-3.0f, 1.8f}};
        for (int i = 0; i < at.length; i++) {
            float bx = birdsX - w * 0.1f + at[i][0] * s * 4, by = birdsY + at[i][1] * s * 4;
            float flap = (float) Math.sin(time * 9 + i * 1.3f) * s * 0.9f;
            bird.reset();
            bird.moveTo(bx - s * 1.6f, by - flap);
            bird.quadTo(bx - s * 0.7f, by - s * 0.4f, bx, by);
            bird.quadTo(bx + s * 0.7f, by - s * 0.4f, bx + s * 1.6f, by - flap);
            canvas.drawPath(bird, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lastFrame = 0;
    }
}
