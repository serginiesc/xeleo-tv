package com.xeleo.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/** A few seconds of confetti in two colours (your team's), falling and spinning, then it stops drawing. */
final class ConfettiView extends View {

    private static final int N = 120;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final float[] x = new float[N], y = new float[N], vx = new float[N], vy = new float[N];
    private final float[] rot = new float[N], vr = new float[N], size = new float[N];
    private final int[] color = new int[N];
    private long start, last;
    private static final long DURATION = 7000;

    ConfettiView(Context c) {
        super(c);
    }

    void burst(int c1, int c2) {
        int w = getWidth(), h = getHeight();
        if (w == 0) {
            post(() -> burst(c1, c2));
            return;
        }
        int[] palette = {c1 | 0xFF000000, c2 | 0xFF000000, 0xFFFFFFFF};
        for (int i = 0; i < N; i++) {
            x[i] = random.nextFloat() * w;
            y[i] = -random.nextFloat() * h * 0.6f;
            vx[i] = (random.nextFloat() - 0.5f) * w * 0.05f;
            vy[i] = h * (0.18f + random.nextFloat() * 0.22f);
            rot[i] = random.nextFloat() * 360;
            vr[i] = (random.nextFloat() - 0.5f) * 540;
            size[i] = h * (0.008f + random.nextFloat() * 0.008f);
            color[i] = palette[random.nextInt(palette.length)];
        }
        start = SystemClock.uptimeMillis();
        last = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (start == 0) return;
        long now = SystemClock.uptimeMillis();
        long t = now - start;
        if (t > DURATION) {
            start = 0;
            return;
        }
        float dt = last == 0 ? 0 : (now - last) / 1000f;
        last = now;
        float fade = t > DURATION - 1500 ? (DURATION - t) / 1500f : 1f;
        for (int i = 0; i < N; i++) {
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            rot[i] += vr[i] * dt;
            paint.setColor(color[i]);
            paint.setAlpha((int) (230 * fade));
            canvas.save();
            canvas.translate(x[i], y[i]);
            canvas.rotate(rot[i]);
            canvas.drawRect(-size[i], -size[i] * 0.45f, size[i], size[i] * 0.45f, paint);
            canvas.restore();
        }
        postInvalidateOnAnimation();
    }
}
