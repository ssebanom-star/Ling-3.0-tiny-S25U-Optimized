package net.classcardauto.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * A see-through, untouchable drawing over the whole screen that shows one swipe of the word collection: the band the
 * list is dragged through, the start and end points, a dot that travels with the finger, and the range in percent.
 * It holds no text nodes, so it does not show up when the screen is read.
 */
public final class SwipeGuide extends View {
    private static final int ACCENT = Color.rgb(11, 160, 132);

    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x;
    private float y1;
    private float y2;
    private float progress;
    private String label = "";
    private ValueAnimator animator;
    private final Runnable hide = new Runnable() {
        @Override
        public void run() {
            setVisibility(GONE);
        }
    };

    public SwipeGuide(Context context) {
        super(context);
        float d = context.getResources().getDisplayMetrics().density;
        band.setColor(Color.argb(46, 11, 160, 132));
        line.setColor(ACCENT);
        line.setStrokeWidth(3 * d);
        line.setStyle(Paint.Style.STROKE);
        line.setPathEffect(new android.graphics.DashPathEffect(new float[]{8 * d, 6 * d}, 0));
        dot.setColor(ACCENT);
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2.5f * d);
        text.setColor(Color.WHITE);
        text.setTextSize(13 * d);
        text.setFakeBoldText(true);
        pill.setColor(Color.argb(230, 17, 26, 24));
        setVisibility(GONE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    /**
     * Shows a swipe from (x, fromY) to (x, toY) in screen pixels; the moving dot takes {@code durationMs}.
     * The drawing stays until {@link #dismiss()} or, at the latest, {@code durationMs + lingerMs}.
     */
    public void show(float x, float fromY, float toY, long durationMs, long lingerMs, String label) {
        this.x = x;
        this.y1 = fromY;
        this.y2 = toY;
        this.label = label;
        this.progress = 0;
        removeCallbacks(hide);
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Math.max(1, durationMs));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        setVisibility(VISIBLE);
        animator.start();
        postDelayed(hide, durationMs + lingerMs);
    }

    /** Removes the drawing now (before the screen is read again). */
    public void dismiss() {
        removeCallbacks(hide);
        if (animator != null) {
            animator.cancel();
        }
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float top = Math.min(y1, y2);
        float bottom = Math.max(y1, y2);
        float half = 34 * d;
        canvas.drawRoundRect(new RectF(x - half, top, x + half, bottom), half, half, band);
        canvas.drawLine(x, y1, x, y2, line);

        // arrow head at the end point
        float dir = Math.signum(y2 - y1);
        Path head = new Path();
        head.moveTo(x, y2);
        head.lineTo(x - 9 * d, y2 - dir * 14 * d);
        head.lineTo(x + 9 * d, y2 - dir * 14 * d);
        head.close();
        canvas.drawPath(head, dot);

        // start point, and the finger travelling along the path
        canvas.drawCircle(x, y1, 7 * d, dot);
        canvas.drawCircle(x, y1, 7 * d, ring);
        float fy = y1 + (y2 - y1) * progress;
        canvas.drawCircle(x, fy, 12 * d, dot);
        canvas.drawCircle(x, fy, 12 * d, ring);

        if (!label.isEmpty()) {
            float w = text.measureText(label);
            float cx = x + half + 10 * d;
            float cy = (top + bottom) / 2f;
            if (cx + w + 20 * d > getWidth()) {
                cx = x - half - 10 * d - w - 20 * d;
            }
            canvas.drawRoundRect(new RectF(cx, cy - 14 * d, cx + w + 20 * d, cy + 14 * d), 14 * d, 14 * d, pill);
            canvas.drawText(label, cx + 10 * d, cy + 4.5f * d, text);
        }
    }
}
