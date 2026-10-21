package com.xeleo.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Moving weather over the landscape: falling rain, drifting snow, and on storms an occasional lightning flash.
 * Draws nothing for the other kinds, and stops animating while hidden. A few hundred lines and dots per frame,
 * no allocations in onDraw.
 */
final class PrecipView extends View {

    private static final int DROPS = 140;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] x = new float[DROPS], y = new float[DROPS], speed = new float[DROPS], size = new float[DROPS];
    private final Random random = new Random();
    private Scenery.Kind kind = Scenery.Kind.CLEAR;
    private long lastFrame;
    private long nextFlash;
    private float flash;

    PrecipView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setKind(Scenery.Kind k) {
        if (k == kind) return;
        kind = k;
        seeded = false;
        nextFlash = SystemClock.uptimeMillis() + 4000;
        invalidate();
    }

    private boolean seeded;

    private boolean moving() {
        return kind == Scenery.Kind.RAIN || kind == Scenery.Kind.STORM || kind == Scenery.Kind.SNOW;
    }

    private void seed(int w, int h) {
        boolean snow = kind == Scenery.Kind.SNOW;
        for (int i = 0; i < DROPS; i++) {
            x[i] = random.nextFloat() * w;
            y[i] = random.nextFloat() * h;
            speed[i] = snow ? h * (0.04f + random.nextFloat() * 0.05f) : h * (0.9f + random.nextFloat() * 0.6f);
            size[i] = snow ? 1.5f + random.nextFloat() * 2.5f : h * (0.018f + random.nextFloat() * 0.02f);
        }
        seeded = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!moving()) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (!seeded) seed(w, h);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        boolean snow = kind == Scenery.Kind.SNOW;

        if (snow) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCCFFFFFF);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, h * 0.0012f));
            paint.setColor(kind == Scenery.Kind.STORM ? 0x66C8D2E0 : 0x55D0DAE6);
        }
        float drift = snow ? 0 : 0.18f;   // rain slants a little
        for (int i = 0; i < DROPS; i++) {
            y[i] += speed[i] * dt;
            x[i] += (snow ? (float) Math.sin((y[i] + i * 37) / 60f) * 0.6f : speed[i] * drift * dt);
            if (y[i] > h) {
                y[i] = -size[i];
                x[i] = random.nextFloat() * w;
            }
            if (x[i] > w) x[i] -= w;
            if (snow) canvas.drawCircle(x[i], y[i], size[i], paint);
            else canvas.drawLine(x[i], y[i], x[i] - size[i] * drift, y[i] - size[i], paint);
        }

        if (kind == Scenery.Kind.STORM) {
            if (now >= nextFlash) {
                flash = 1f;
                nextFlash = now + 9000 + random.nextInt(14000);
            }
            if (flash > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(((int) (flash * 90) << 24) | 0xE8EEFF);
                canvas.drawRect(0, 0, w, h, paint);
                flash = Math.max(0, flash - dt * 3.5f);
            }
        }
        if (getVisibility() == VISIBLE && isAttachedToWindow()) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lastFrame = 0;
    }
}
