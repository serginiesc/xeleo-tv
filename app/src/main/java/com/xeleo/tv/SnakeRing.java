package com.xeleo.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The focus frame of an app card: a soft ring with a bright stretch of light running around it, like a snake
 * chasing its tail (white, or your team's colour on game day). While a card is being moved it is a plain blue
 * frame instead. Runs only while shown, one turn every 2.4 s.
 */
final class SnakeRing extends View {

    private static final int MOVING = 0xFF8AB4F8;
    private static final int SLICES = 14;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), piece = new Path();
    private PathMeasure measure;
    private float length, phase;
    private final float radius, stroke;
    private int color = 0xFFFFFFFF;
    private boolean on, moving;
    private ValueAnimator spin;

    SnakeRing(Context c, float radiusPx, float strokePx) {
        super(c);
        radius = radiusPx;
        stroke = strokePx;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(stroke);
        setWillNotDraw(false);
    }

    void setColor(int c) {
        color = c | 0xFF000000;
        invalidate();
    }

    /** Shown while the card has the focus. */
    void setOn(boolean show) {
        on = show;
        update();
    }

    /** The plain blue frame of a card being moved. */
    void setMoving(boolean m) {
        moving = m;
        update();
    }

    /** While the focus is moving along the row the frame stays still; the light starts once it settles. */
    private static final long SETTLE_MS = 450;
    private final Runnable start = this::startSpin;

    private void update() {
        boolean run = on && !moving && isAttachedToWindow();
        removeCallbacks(start);
        if (run && spin == null) postDelayed(start, SETTLE_MS);
        else if (!run && spin != null) {
            spin.cancel();
            spin = null;
        }
        invalidate();
    }

    private long lastDraw;

    private void startSpin() {
        if (!on || moving || !isAttachedToWindow() || spin != null) return;
        spin = ValueAnimator.ofFloat(0f, 1f);
        spin.setDuration(2400);
        // In light mode two turns, then it rests lit; otherwise it keeps going.
        spin.setRepeatCount(Perf.light(getContext()) ? 1 : ValueAnimator.INFINITE);
        spin.setInterpolator(new LinearInterpolator());
        spin.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            // A light this slow looks the same at 30 frames a second, at half the drawing.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastDraw < 30) return;
            lastDraw = now;
            invalidate();
        });
        spin.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(start);
        if (spin != null) spin.cancel();
        spin = null;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float in = stroke / 2f;
        path.reset();
        path.addRoundRect(new RectF(in, in, w - in, h - in), radius - in, radius - in, Path.Direction.CW);
        measure = new PathMeasure(path, true);
        length = measure.getLength();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (measure == null || (!on && !moving)) return;
        if (moving || spin == null) {
            // Being moved: blue. Just focused: a plain frame until the light starts.
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(moving ? MOVING : color);
            paint.setStrokeWidth(moving ? stroke * 1.3f : stroke);
            canvas.drawPath(path, paint);
            return;
        }
        // The ring underneath, soft.
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(stroke);
        paint.setColor((color & 0x00FFFFFF) | 0x4D000000);
        canvas.drawPath(path, paint);
        // The snake: a thicker stretch a third of the way round, fading towards its tail (flat ends, so the
        // slices join without beads), with a round head.
        paint.setStrokeWidth(stroke * 1.5f);
        float snake = length * 0.34f, head = phase * length, slice = snake / SLICES;
        for (int i = 0; i < SLICES; i++) {
            float from = head - snake + i * slice;
            int alpha = (int) (255 * Math.pow((i + 1f) / SLICES, 1.6));
            paint.setColor((color & 0x00FFFFFF) | (alpha << 24));
            segment(canvas, from, from + slice + 0.5f);
        }
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(color);
        segment(canvas, head - 0.5f, head);
    }

    /** Draws the path between two distances, wrapping round the start. */
    private void segment(Canvas canvas, float from, float to) {
        float span = to - from;
        from = ((from % length) + length) % length;
        float end = from + span;
        piece.reset();
        if (end <= length) {
            measure.getSegment(from, end, piece, true);
        } else {
            measure.getSegment(from, length, piece, true);
            measure.getSegment(0, end - length, piece, true);
        }
        canvas.drawPath(piece, paint);
    }
}
