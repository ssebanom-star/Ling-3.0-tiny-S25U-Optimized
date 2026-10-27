package net.classcardauto.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.CompoundButton;

/** A pill-shaped on/off switch drawn directly, so it looks the same on every Android version. */
public final class PillSwitch extends CompoundButton {
    private static final int OFF_TRACK = Color.rgb(226, 232, 229);
    private static final int OFF_EDGE = Color.rgb(160, 172, 166);
    private static final int OFF_THUMB = Color.rgb(116, 129, 123);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float progress;
    private ValueAnimator animator;

    public PillSwitch(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setButtonDrawable(null);
        setBackground(null);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension((int) (52 * density), (int) (32 * density));
    }

    @Override
    public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        float target = checked ? 1.0f : 0.0f;
        if (!changed || !isLaidOut()) {
            progress = target;
            invalidate();
            return;
        }
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(140);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                progress = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        animator.start();
    }

    private static int mix(int from, int to, float t) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float on = isLaidOut() ? progress : (isChecked() ? 1.0f : 0.0f);
        float edge = 2 * density;
        rect.set(edge / 2, edge / 2, w - edge / 2, h - edge / 2);
        float radius = rect.height() / 2;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mix(OFF_TRACK, AppStyle.INSTANCE.getAccent(), on));
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (on < 1.0f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(edge);
            paint.setColor(mix(OFF_EDGE, AppStyle.INSTANCE.getAccent(), on));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        float thumbRadius = (8 + 4 * on) * density;
        float left = 16 * density;
        float right = w - 16 * density;
        float cx = left + (right - left) * on;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mix(OFF_THUMB, Color.WHITE, on));
        canvas.drawCircle(cx, h / 2, thumbRadius, paint);
    }
}
