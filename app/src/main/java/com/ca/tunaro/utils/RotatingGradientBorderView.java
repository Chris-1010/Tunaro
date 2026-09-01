package com.ca.tunaro.utils;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ca.tunaro.R;

/**
 * Wraps a single child (a ranking song card) and, while active, draws a green
 * "comet" — a bright head trailing a darkening green tail — travelling around a
 * rounded-rectangle border. The ring is painted in this view's own padding gutter
 * so it sits just outside the card and never overlaps the art.
 *
 * <p>Being the card's parent (rather than a same-size sibling) keeps its measured
 * height tied to the card: a {@code match_parent} sibling inside a
 * {@code wrap_content} frame would instead inflate to the whole available column.
 */
public class RotatingGradientBorderView extends FrameLayout {

    // Sweep runs clockwise from 3 o'clock (position 0). The bright head sits just
    // before the wrap, so the tail fades out behind it as the sweep rotates.
    private static final int[] SWEEP_COLORS = {
            0x0011351F, // transparent tail
            0xFF1C7A46, // dark green
            0xFF3DDC7A, // green
            0xFFD6FFE1, // bright head
            0x0011351F  // wrap back to transparent
    };
    private static final float[] SWEEP_POSITIONS = {0f, 0.55f, 0.82f, 0.95f, 1f};

    private static final long ROTATION_PERIOD_MS = 2200L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ring = new RectF();
    private final Matrix rotationMatrix = new Matrix();

    private float strokeWidthPx;
    private float cornerRadiusPx;
    private float centreX, centreY;
    private float angle;
    private boolean active;

    private SweepGradient gradient;
    private ValueAnimator animator;

    public RotatingGradientBorderView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public RotatingGradientBorderView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RotatingGradientBorderView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        float density = getResources().getDisplayMetrics().density;
        strokeWidthPx = 5f * density;
        cornerRadiusPx = 12f * density;

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RotatingGradientBorderView);
            strokeWidthPx = a.getDimension(
                    R.styleable.RotatingGradientBorderView_borderStrokeWidth, strokeWidthPx);
            cornerRadiusPx = a.getDimension(
                    R.styleable.RotatingGradientBorderView_borderCornerRadius, cornerRadiusPx);
            a.recycle();
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidthPx);
        paint.setStrokeCap(Paint.Cap.ROUND);
        // The ring is painted in onDraw (before the child card) so the opaque card
        // covers its inner half and only the outer band hugs the card's edge.
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centreX = w / 2f;
        centreY = h / 2f;
        // Straddle the card's boundary (the padding-inset frame the card sits in),
        // so half the stroke shows in the gutter and half tucks under the card.
        ring.set(getPaddingLeft(), getPaddingTop(),
                w - getPaddingRight(), h - getPaddingBottom());
        gradient = new SweepGradient(centreX, centreY, SWEEP_COLORS, SWEEP_POSITIONS);
        paint.setShader(gradient);
    }

    /** Begin (or continue) rotating the border. No effect on the wrapped card. */
    public void start() {
        active = true;
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(ROTATION_PERIOD_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /** Stop the rotation and clear the border. */
    public void stop() {
        active = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        invalidate();
    }

    // Runs before the child card is drawn, so the card paints over the ring's
    // inner half and the green band hugs the card's edge.
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (!active || gradient == null) return;
        rotationMatrix.setRotate(angle, centreX, centreY);
        gradient.setLocalMatrix(rotationMatrix);
        canvas.drawRoundRect(ring, cornerRadiusPx, cornerRadiusPx, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }
}
