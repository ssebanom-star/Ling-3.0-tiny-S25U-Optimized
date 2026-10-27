package net.classcardauto.mobile;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;

/**
 * Design tokens and small building blocks shared by every screen and by the floating panel.
 * Views are built in code (the original app has no layout XML), on an 8dp grid.
 */
public final class AppStyle {
    public static final AppStyle INSTANCE = new AppStyle();

    private static final int background = Color.rgb(244, 247, 246);
    private static final int surface = -1;
    private static final int ink = Color.rgb(20, 32, 28);
    private static final int muted = Color.rgb(98, 113, 107);
    private static final int accent = Color.rgb(11, 122, 102);
    private static final int accentDark = Color.rgb(8, 92, 77);
    private static final int softAccent = Color.rgb(224, 242, 236);
    private static final int border = Color.rgb(225, 232, 228);
    private static final int danger = Color.rgb(188, 54, 54);
    private static final int softDanger = Color.rgb(252, 232, 230);
    private static final int warn = Color.rgb(166, 98, 0);
    private static final int softWarn = Color.rgb(255, 241, 214);
    private static final int info = Color.rgb(30, 100, 190);
    private static final int softInfo = Color.rgb(226, 238, 252);

    /** Visual weight of a button. */
    public enum Tone { PRIMARY, TONAL, OUTLINE, DANGER, TEXT }

    private AppStyle() {
    }

    // ---- compatibility entry points used by the decompiled code ----

    public static /* synthetic */ void addSpaced$default(AppStyle appStyle, LinearLayout linearLayout, View view, int i, int i2, int i3, Object obj) {
        if ((i3 & 2) != 0) {
            i = 16;
        }
        if ((i3 & 4) != 0) {
            i2 = -2;
        }
        appStyle.addSpaced(linearLayout, view, i, i2);
    }

    public static /* synthetic */ Button button$default(AppStyle appStyle, Context context, String str, boolean z, Function0 function0, int i, Object obj) {
        if ((i & 4) != 0) {
            z = true;
        }
        return appStyle.button(context, str, z, function0);
    }

    public static /* synthetic */ TextView label$default(AppStyle appStyle, Context context, String str, float f, int i, boolean z, int i2, Object obj) {
        if ((i2 & 4) != 0) {
            f = 15.0f;
        }
        if ((i2 & 8) != 0) {
            i = ink;
        }
        if ((i2 & 16) != 0) {
            z = false;
        }
        return appStyle.label(context, str, f, i, z);
    }

    public static /* synthetic */ GradientDrawable shape$default(AppStyle appStyle, Context context, int i, int i2, boolean z, int i3, Object obj) {
        if ((i3 & 4) != 0) {
            i2 = 20;
        }
        if ((i3 & 8) != 0) {
            z = false;
        }
        return appStyle.shape(context, i, i2, z);
    }

    public static /* synthetic */ LinearLayout stack$default(AppStyle appStyle, Context context, int i, int i2, Object obj) {
        if ((i2 & 2) != 0) {
            i = 24;
        }
        return appStyle.stack(context, i);
    }

    public final void addSpaced(LinearLayout linearLayout, View view, int i, int i2) {
        Intrinsics.checkNotNullParameter(linearLayout, "<this>");
        Intrinsics.checkNotNullParameter(view, "view");
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, i2);
        layoutParams.topMargin = dp(linearLayout.getContext(), i);
        linearLayout.addView(view, layoutParams);
    }

    /** Boolean flavour kept for the existing call sites: true = filled primary, false = outlined. */
    public final Button button(Context context, String text, boolean primary, Function0<Unit> action) {
        return button(context, text, primary ? Tone.PRIMARY : Tone.OUTLINE, action);
    }

    public final int dp(Context context, int value) {
        Intrinsics.checkNotNullParameter(context, "context");
        return (int) (context.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    public final int getAccent() {
        return accent;
    }

    public final int getAccentDark() {
        return accentDark;
    }

    public final int getBackground() {
        return background;
    }

    public final int getBorder() {
        return border;
    }

    public final int getDanger() {
        return danger;
    }

    public final int getInk() {
        return ink;
    }

    public final int getMuted() {
        return muted;
    }

    public final int getSoftAccent() {
        return softAccent;
    }

    public final int getSoftDanger() {
        return softDanger;
    }

    public final int getSoftInfo() {
        return softInfo;
    }

    public final int getSoftWarn() {
        return softWarn;
    }

    public final int getInfo() {
        return info;
    }

    public final int getWarn() {
        return warn;
    }

    public final int getSurface() {
        return surface;
    }

    public final TextView label(Context context, String text, float size, int color, boolean bold) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        TextView textView = new TextView(context);
        textView.setText(keepWords(text));
        textView.setTextSize(size);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", bold ? 1 : 0));
        textView.setLineSpacing(dp(context, 3), 1.0f);
        return textView;
    }

    public final GradientDrawable shape(Context context, int fill, int radius, boolean stroke) {
        Intrinsics.checkNotNullParameter(context, "context");
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(fill);
        gradientDrawable.setCornerRadius(dp(context, radius));
        if (stroke) {
            gradientDrawable.setStroke(dp(context, 1), border);
        }
        return gradientDrawable;
    }

    public final LinearLayout stack(Context context, int padding) {
        Intrinsics.checkNotNullParameter(context, "context");
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        int dp = dp(context, padding);
        linearLayout.setPadding(dp, dp, dp, dp);
        return linearLayout;
    }

    // ---- new building blocks ----

    public final GradientDrawable roundRect(Context context, int fill, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context, radiusDp));
        if (strokeColor != 0) {
            d.setStroke(dp(context, 1), strokeColor);
        }
        return d;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public final Button button(Context context, String text, Tone tone, final Function0<Unit> action) {
        int fill;
        int stroke = 0;
        int textColor;
        switch (tone) {
            case PRIMARY:
                fill = accent;
                textColor = -1;
                break;
            case TONAL:
                fill = softAccent;
                textColor = accentDark;
                break;
            case DANGER:
                fill = softDanger;
                textColor = danger;
                break;
            case TEXT:
                fill = 0;
                textColor = accent;
                break;
            default:
                fill = surface;
                stroke = border;
                textColor = ink;
                break;
        }
        Button button = new Button(context);
        button.setText(text);
        button.setContentDescription(text);
        button.setAllCaps(false);
        button.setTextSize(15.0f);
        button.setTypeface(Typeface.create("sans-serif-medium", 0));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-16842910}, new int[0]}, new int[]{withAlpha(textColor, 110), textColor}));
        int ripple = withAlpha(tone == Tone.PRIMARY ? -1 : accent, 40);
        GradientDrawable base = roundRect(context, fill, 16, stroke);
        if (tone == Tone.TEXT) {
            base.setColor(0);
        }
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), base, null));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                action.invoke();
            }
        });
        return button;
    }

    /** A white rounded surface with a hairline border. */
    public final LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(1);
        card.setBackground(roundRect(context, surface, 20, border));
        int p = dp(context, 20);
        card.setPadding(p, p, p, p);
        return card;
    }

    public final TextView title(Context context, String text) {
        return label(context, text, 20.0f, ink, true);
    }

    public final TextView body(Context context, String text) {
        TextView t = label(context, text, 15.0f, ink, false);
        return t;
    }

    public final TextView caption(Context context, String text) {
        TextView t = label(context, text, 13.0f, muted, false);
        return t;
    }

    public final TextView sectionLabel(Context context, String text) {
        TextView t = label(context, text, 12.5f, muted, true);
        t.setLetterSpacing(0.04f);
        return t;
    }

    public final ImageView icon(Context context, int resId, int tint, int sizeDp) {
        ImageView image = new ImageView(context);
        image.setImageResource(resId);
        image.setColorFilter(tint);
        image.setImportantForAccessibility(2);
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return image;
    }

    /** A 44dp circular tap target holding an icon. */
    public final FrameLayout iconButton(Context context, int resId, String description, int tint, final Function0<Unit> action) {
        FrameLayout frame = new FrameLayout(context);
        frame.setContentDescription(description);
        frame.setClickable(true);
        frame.setFocusable(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(0);
        frame.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(accent, 40)), shape, null));
        ImageView image = new ImageView(context);
        image.setImageResource(resId);
        image.setColorFilter(tint);
        image.setImportantForAccessibility(2);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(context, 24), dp(context, 24));
        lp.gravity = Gravity.CENTER;
        frame.addView(image, lp);
        frame.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                action.invoke();
            }
        });
        frame.setLayoutParams(new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return frame;
    }

    public final TextView chip(Context context, String text, int fg, int bg) {
        TextView t = label(context, text, 12.5f, fg, true);
        t.setBackground(roundRect(context, bg, 999, 0));
        t.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    public final View dot(Context context, int color, int sizeDp) {
        View v = new View(context);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        v.setBackground(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp)));
        return v;
    }

    public final void setDotColor(View dot, int color) {
        if (dot.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) dot.getBackground()).setColor(color);
        }
    }

    public final View divider(Context context) {
        View v = new View(context);
        v.setBackgroundColor(border);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(context, 1))));
        return v;
    }

    public final CompoundButton toggle(Context context, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        PillSwitch sw = new PillSwitch(context);
        sw.setChecked(checked);
        if (listener != null) {
            sw.setOnCheckedChangeListener(listener);
        }
        return sw;
    }

    /**
     * Android breaks Hangul between any two syllables, which splits words like "않습/니다". A word joiner between the
     * syllables of a word keeps each word on one line; spaces remain the only break points.
     */
    public static String keepWords(String text) {
        if (text == null || text.length() < 2) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c);
            if (i + 1 < text.length() && isHangul(c) && isHangul(text.charAt(i + 1))) {
                out.append('⁠');
            }
        }
        return out.toString();
    }

    private static boolean isHangul(char c) {
        return (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F);
    }

    /** Title + description on the left, a switch on the right. */
    public final LinearLayout switchRow(Context context, String title, String description, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(1);
        text.addView(label(context, title, 15.5f, ink, true));
        if (description != null && !description.isEmpty()) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(context, 4);
            text.addView(caption(context, description), lp);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1.0f));
        CompoundButton sw = toggle(context, checked, listener);
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(-2, -2);
        swp.leftMargin = dp(context, 16);
        row.addView(sw, swp);
        final CompoundButton swRef = sw;
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                swRef.toggle();
            }
        });
        return row;
    }

    /** Row with a trailing chevron that runs an action. */
    public final LinearLayout navRow(Context context, String title, String description, final Function0<Unit> action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(0);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(context, 56));
        row.setPadding(0, dp(context, 12), 0, dp(context, 12));
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(1);
        text.addView(label(context, title, 15.5f, ink, false));
        if (description != null && !description.isEmpty()) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(context, 2);
            text.addView(caption(context, description), lp);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1.0f));
        row.addView(icon(context, R.drawable.ic_chevron, muted, 22));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(accent, 30)), null, new ColorDrawable(-1)));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                action.invoke();
            }
        });
        return row;
    }

    public final SeekBar seek(Context context, int steps, int progress) {
        SeekBar seek = new SeekBar(context);
        seek.setMax(steps);
        seek.setProgress(progress);
        seek.setProgressTintList(ColorStateList.valueOf(accent));
        seek.setThumbTintList(ColorStateList.valueOf(accent));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(accent, 60)));
        return seek;
    }

    /** 572 -> "9:32", 3725 -> "1:02:05". */
    public static String formatClock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    /** Values stored in milliseconds are shown in seconds ("0.45초"); anything else keeps its unit. */
    public static String formatValue(int value, String unit) {
        if (!"ms".equals(unit)) {
            return value + unit;
        }
        double seconds = value / 1000.0;
        String text = String.format(java.util.Locale.US, "%.2f", seconds);
        if (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".0")) {
            text = text.substring(0, text.length() - 2);
        }
        return text + "초";
    }

    /** "label ........ value" over a slider. {@code onChange} is called with the real value while dragging. */
    public final LinearLayout sliderRow(Context context, String title, String unit, final int min, int max, final int step, int current, final Function1<Integer, Unit> onChange, boolean compact) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(1);
        LinearLayout head = new LinearLayout(context);
        head.setOrientation(0);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(context, title, compact ? 12.5f : 15.0f, compact ? -1 : ink, !compact);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1.0f));
        final TextView value = label(context, formatValue(current, unit), compact ? 12.5f : 14.0f, compact ? -1 : accentDark, true);
        head.addView(value);
        box.addView(head);
        SeekBar seek = seek(context, (max - min) / step, (current - min) / step);
        if (compact) {
            seek.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(-1, 70)));
            seek.setProgressTintList(ColorStateList.valueOf(-1));
            seek.setThumbTintList(ColorStateList.valueOf(-1));
        }
        final String suffix = unit;
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = min + (progress * step);
                value.setText(formatValue(v, suffix));
                if (fromUser) {
                    onChange.invoke(Integer.valueOf(v));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(context, compact ? 0 : 4);
        box.addView(seek, lp);
        return box;
    }

    /** Rounded dialog with a title, scrolling body and up to two text buttons. */
    public final Dialog sheet(Context context, String title, View body, String positive, final Runnable onPositive, String negative, final Runnable onNegative) {
        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(1);
        root.setBackground(roundRect(context, surface, 24, 0));
        int p = dp(context, 24);
        root.setPadding(p, p, p, dp(context, 12));
        if (title != null) {
            root.addView(title(context, title));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setOverScrollMode(2);
        scroll.addView(body);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2, 1.0f);
        sp.topMargin = dp(context, title != null ? 14 : 0);
        sp.bottomMargin = dp(context, 8);
        root.addView(scroll, sp);
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(0);
        buttons.setGravity(Gravity.END);
        if (negative != null) {
            Button b = button(context, negative, Tone.TEXT, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    dialog.dismiss();
                    if (onNegative != null) {
                        onNegative.run();
                    }
                    return Unit.INSTANCE;
                }
            });
            b.setMinHeight(dp(context, 44));
            b.setMinimumHeight(dp(context, 44));
            buttons.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        if (positive != null) {
            Button b = button(context, positive, Tone.TEXT, new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    dialog.dismiss();
                    if (onPositive != null) {
                        onPositive.run();
                    }
                    return Unit.INSTANCE;
                }
            });
            b.setMinHeight(dp(context, 44));
            b.setMinimumHeight(dp(context, 44));
            buttons.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.92f);
            int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.8f);
            window.setLayout(Math.min(width, dp(context, 520)), ViewGroup.LayoutParams.WRAP_CONTENT);
            root.setMinimumHeight(0);
            scroll.setLayoutParams(sp);
            window.setDimAmount(0.45f);
            final int cap = maxHeight;
            root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    if (b - t > cap) {
                        v.getLayoutParams().height = cap;
                        v.requestLayout();
                    }
                }
            });
        }
        return dialog;
    }
}
