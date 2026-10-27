package net.classcardauto.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The card that floats above ClassCard: status, start/stop, and (before the test) scroll settings and a button that
 * hides the panel. It only builds and updates views; BotService owns the window and decides what the buttons do.
 */
public final class FloatingPanel extends LinearLayout {
    public interface Host {
        void onPrimary();

        void onHide();

        /** Called while the handle is dragged, with the movement since the touch started (pixels). */
        void onDrag(float dx, float dy, boolean finished);

        boolean isBlocked();

        /** Pause button: pauses a running bot, or resumes a paused one. */
        void onPause();

        /** A finger went down on the panel itself (event time in uptime ms), so it is not a touch on the app below. */
        void onPanelTouch(long eventTime);
    }

    private static final int PANEL = Color.argb(238, 18, 28, 25);
    private static final int ON_PANEL = Color.argb(255, 255, 255, 255);
    private static final int ON_PANEL_DIM = Color.argb(170, 255, 255, 255);
    private static final int CHIP = Color.argb(34, 255, 255, 255);

    private final TextView statusText;
    private final TextView caption;
    private final View dot;
    private final Button primary;
    private final LinearLayout tools;
    private final LinearLayout settingsChip;
    private final LinearLayout hideChip;
    private final LinearLayout sliders;
    private final android.widget.ScrollView settingsScroll;
    private boolean userCollapsed;
    private boolean settingsShown;
    private final android.content.SharedPreferences panelPrefs;
    private final TextView settingsLabel;
    private boolean running;
    private boolean pausedNow;
    private final LinearLayout pauseChip;
    private final TextView pauseLabel;
    private final ImageView pauseIcon;
    private final TextView touchText;
    private final Host hostRef;
    private long remainingSeconds = -1;
    private final LinearLayout mini;
    private final LinearLayout full;
    private final TextView miniTime;
    private final TextView miniInfo;
    private final ImageView miniPauseIcon;
    private boolean compact;
    private final int padL, padT, padR, padB;

    public FloatingPanel(final Context context, SharedPreferences prefs, final Host host) {
        super(context);
        this.hostRef = host;
        final AppStyle s = AppStyle.INSTANCE;
        setOrientation(VERTICAL);
        setBackground(s.roundRect(context, PANEL, 22, Color.argb(40, 255, 255, 255)));
        setPadding(s.dp(context, 8), s.dp(context, 8), s.dp(context, 10), s.dp(context, 8));
        setElevation(s.dp(context, 6));

        // header: drag handle | dot + text | primary button
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView handle = new ImageView(context);
        handle.setImageResource(R.drawable.ic_drag);
        handle.setColorFilter(ON_PANEL_DIM);
        handle.setContentDescription("패널 이동");
        handle.setPadding(s.dp(context, 2), s.dp(context, 8), s.dp(context, 2), s.dp(context, 8));
        handle.setOnTouchListener(new OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        host.onDrag(e.getRawX() - downX, e.getRawY() - downY, false);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        host.onDrag(e.getRawX() - downX, e.getRawY() - downY, true);
                        return true;
                    default:
                        return false;
                }
            }
        });
        header.addView(handle, new LayoutParams(s.dp(context, 24), s.dp(context, 40)));

        dot = s.dot(context, s.getAccent(), 8);
        LayoutParams dotParams = new LayoutParams(s.dp(context, 8), s.dp(context, 8));
        dotParams.leftMargin = s.dp(context, 4);
        dotParams.rightMargin = s.dp(context, 8);
        header.addView(dot, dotParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        caption = s.label(context, "단어 도우미", 10.5f, ON_PANEL_DIM, false);
        caption.setLineSpacing(0, 1.0f);
        texts.addView(caption);
        statusText = s.label(context, "", 12.5f, ON_PANEL, true);
        statusText.setMaxLines(2);
        statusText.setLineSpacing(0, 1.05f);
        texts.addView(statusText);
        LayoutParams tp = new LayoutParams(s.dp(context, 112), -2);
        header.addView(texts, tp);

        primary = new Button(context);
        primary.setAllCaps(false);
        primary.setTextSize(13.0f);
        primary.setTypeface(Typeface.create("sans-serif-medium", 0));
        primary.setTextColor(-1);
        primary.setStateListAnimator(null);
        primary.setElevation(0);
        primary.setMinWidth(0);
        primary.setMinimumWidth(0);
        primary.setMinHeight(0);
        primary.setMinimumHeight(0);
        primary.setPadding(s.dp(context, 14), 0, s.dp(context, 14), 0);
        primary.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        LayoutParams pp = new LayoutParams(-2, s.dp(context, 40));
        pp.leftMargin = s.dp(context, 8);
        header.addView(primary, pp);
        addView(header, new LayoutParams(-2, -2));

        touchText = s.label(context, "", 11.5f, ON_PANEL_DIM, true);
        touchText.setVisibility(GONE);
        LayoutParams touchParams = new LayoutParams(-2, -2);
        touchParams.leftMargin = s.dp(context, 6);
        touchParams.topMargin = s.dp(context, 2);
        addView(touchText, touchParams);

        // tools: scroll settings / hide (only offered before the test starts)
        tools = new LinearLayout(context);
        tools.setOrientation(HORIZONTAL);
        tools.setGravity(Gravity.END);
        settingsLabel = new TextView(context);
        settingsChip = chip(context, R.drawable.ic_tune, "설정 접기", settingsLabel);
        hideChip = chip(context, R.drawable.ic_hide, "UI 끄기", new TextView(context));
        hideChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onHide();
                }
            }
        });
        pauseLabel = new TextView(context);
        pauseChip = chip(context, R.drawable.ic_pause, "일시정지", pauseLabel);
        pauseIcon = (ImageView) pauseChip.getChildAt(0);
        pauseChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPause();
                }
            }
        });
        pauseChip.setVisibility(GONE);
        tools.addView(pauseChip, new LayoutParams(-2, s.dp(context, 34)));
        LayoutParams sp0 = new LayoutParams(-2, s.dp(context, 34));
        sp0.leftMargin = s.dp(context, 8);
        tools.addView(settingsChip, sp0);
        LayoutParams hp = new LayoutParams(-2, s.dp(context, 34));
        hp.leftMargin = s.dp(context, 8);
        tools.addView(hideChip, hp);
        LayoutParams toolsParams = new LayoutParams(-1, -2);
        toolsParams.topMargin = s.dp(context, 6);
        addView(tools, toolsParams);

        // compact strip used during the test so the panel covers as little of the questions as possible
        mini = new LinearLayout(context);
        mini.setOrientation(VERTICAL);
        mini.setGravity(Gravity.CENTER_HORIZONTAL);
        mini.setVisibility(GONE);
        ImageView miniHandle = new ImageView(context);
        miniHandle.setImageResource(R.drawable.ic_drag);
        miniHandle.setColorFilter(ON_PANEL_DIM);
        miniHandle.setContentDescription("패널 이동");
        miniHandle.setRotation(90);
        miniHandle.setOnTouchListener(dragListener(host));
        mini.addView(miniHandle, new LayoutParams(s.dp(context, 40), s.dp(context, 22)));
        miniTime = s.label(context, "", 12.0f, ON_PANEL, true);
        miniTime.setGravity(Gravity.CENTER);
        mini.addView(miniTime, new LayoutParams(-2, -2));
        LinearLayout miniPause = new LinearLayout(context);
        miniPause.setGravity(Gravity.CENTER);
        GradientDrawable pauseBg = s.roundRect(context, CHIP, 999, 0);
        miniPause.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), pauseBg, null));
        miniPause.setClickable(true);
        miniPauseIcon = new ImageView(context);
        miniPauseIcon.setImageResource(R.drawable.ic_pause);
        miniPauseIcon.setColorFilter(ON_PANEL);
        miniPause.addView(miniPauseIcon, new LayoutParams(s.dp(context, 18), s.dp(context, 18)));
        miniPause.setContentDescription("일시정지");
        miniPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPause();
                }
            }
        });
        LayoutParams mpp = new LayoutParams(s.dp(context, 40), s.dp(context, 32));
        mpp.topMargin = s.dp(context, 4);
        mini.addView(miniPause, mpp);
        Button miniStop = new Button(context);
        miniStop.setAllCaps(false);
        miniStop.setText("중지");
        miniStop.setTextSize(11.0f);
        miniStop.setTextColor(-1);
        miniStop.setStateListAnimator(null);
        miniStop.setElevation(0);
        miniStop.setMinWidth(0);
        miniStop.setMinimumWidth(0);
        miniStop.setMinHeight(0);
        miniStop.setMinimumHeight(0);
        miniStop.setPadding(0, 0, 0, 0);
        miniStop.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), s.roundRect(context, s.getDanger(), 999, 0), null));
        miniStop.setContentDescription("자동 풀이 중지");
        miniStop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        LayoutParams msp = new LayoutParams(s.dp(context, 40), s.dp(context, 28));
        msp.topMargin = s.dp(context, 4);
        mini.addView(miniStop, msp);
        miniInfo = s.label(context, "", 10.0f, ON_PANEL_DIM, true);
        miniInfo.setGravity(Gravity.CENTER);
        LayoutParams mip = new LayoutParams(-2, -2);
        mip.topMargin = s.dp(context, 4);
        mini.addView(miniInfo, mip);
        addView(mini, new LayoutParams(-2, -2));
        padL = s.dp(context, 8); padT = s.dp(context, 8); padR = s.dp(context, 10); padB = s.dp(context, 8);

        panelPrefs = prefs;
        sliders = new LinearLayout(context);
        sliders.setOrientation(VERTICAL);
        sliders.setPadding(s.dp(context, 8), s.dp(context, 4), s.dp(context, 4), 0);
        Screens.addPanelSettings(context, sliders, prefs);
        settingsScroll = new android.widget.ScrollView(context);
        settingsScroll.setOverScrollMode(2);
        settingsScroll.setVisibility(GONE);
        settingsScroll.addView(sliders, new android.view.ViewGroup.LayoutParams(-1, -2));
        addView(settingsScroll, new LayoutParams(s.dp(context, 252), s.dp(context, 330)));

        settingsChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (host.isBlocked()) {
                    return;
                }
                userCollapsed = !userCollapsed;
                applySettingsVisibility(!running);
            }
        });

        // everything except the compact strip goes into one container so the two layouts can be swapped
        full = new LinearLayout(context);
        full.setOrientation(VERTICAL);
        java.util.List<View> kids = new java.util.ArrayList<>();
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) != mini) {
                kids.add(getChildAt(i));
            }
        }
        for (View kid : kids) {
            android.view.ViewGroup.LayoutParams kp = kid.getLayoutParams();
            removeView(kid);
            full.addView(kid, kp);
        }
        addView(full, 0, new LayoutParams(-2, -2));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            hostRef.onPanelTouch(ev.getEventTime());
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * Pause state and the user-touch counter. Shown only during a run; the counter only during the test.
     *
     * @param paused  the bot is paused
     * @param touches touches by the user since the run started or was last resumed
     * @param limit   touches that pause the bot
     * @param inTest  the test screen is active
     */
    public void renderRun(boolean paused, int touches, int limit, boolean inTest) {
        renderRun(paused, touches, limit, inTest, false, 0);
    }

    public void renderRun(boolean paused, int touches, int limit, boolean inTest, boolean locked, int blocked) {
        AppStyle s = AppStyle.INSTANCE;
        pausedNow = paused && running;
        pauseChip.setVisibility(running ? VISIBLE : GONE);
        pauseLabel.setText(pausedNow ? "재개" : "일시정지");
        pauseIcon.setImageResource(pausedNow ? R.drawable.ic_play : R.drawable.ic_pause);
        pauseChip.setContentDescription(pausedNow ? "재개" : "일시정지");
        if (running) {
            tools.setVisibility(VISIBLE);
        }
        if (running && locked && !pausedNow) {
            touchText.setVisibility(VISIBLE);
            touchText.setText("화면 잠금 중 · 막은 터치 " + blocked + "회");
            touchText.setTextColor(blocked > 0 ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
        } else if (running && (inTest || pausedNow)) {
            touchText.setVisibility(VISIBLE);
            if (pausedNow) {
                touchText.setText("일시정지됨 · 잠금 해제됨 · 직접 터치 " + touches + "회");
                touchText.setTextColor(Color.rgb(255, 196, 92));
            } else {
                touchText.setText("직접 터치 " + touches + " / " + limit + "회");
                touchText.setTextColor(touches > 0 ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
            }
        } else {
            touchText.setVisibility(GONE);
        }
        s.setDotColor(dot, !running ? s.getAccent() : (pausedNow ? s.getWarn() : s.getInfo()));
        updateCaption();
        applyCompact(running, inTest, locked, blocked, touches, limit);
    }

    private OnTouchListener dragListener(final Host host) {
        return new OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        host.onDrag(e.getRawX() - downX, e.getRawY() - downY, false);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        host.onDrag(e.getRawX() - downX, e.getRawY() - downY, true);
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    /** While a run is going (collecting or testing) only the narrow strip is shown; otherwise the full card. */
    private void applyCompact(boolean on, boolean inTest, boolean locked, int blocked, int touches, int limit) {
        if (on != compact) {
            compact = on;
            full.setVisibility(on ? GONE : VISIBLE);
            mini.setVisibility(on ? VISIBLE : GONE);
            if (on) {
                setPadding(AppStyle.INSTANCE.dp(getContext(), 4), AppStyle.INSTANCE.dp(getContext(), 4), AppStyle.INSTANCE.dp(getContext(), 4), AppStyle.INSTANCE.dp(getContext(), 6));
            } else {
                setPadding(padL, padT, padR, padB);
            }
        }
        if (on) {
            miniPauseIcon.setImageResource(pausedNow ? R.drawable.ic_play : R.drawable.ic_pause);
            ((View) miniPauseIcon.getParent()).setContentDescription(pausedNow ? "재개" : "일시정지");
            miniTime.setText(remainingSeconds < 0 ? "" : AppStyle.formatClock(remainingSeconds));
            miniTime.setTextColor(pausedNow || (remainingSeconds >= 0 && remainingSeconds <= 60) ? Color.rgb(255, 196, 92) : ON_PANEL);
            if (pausedNow) {
                miniInfo.setText("정지\n터치 " + touches);
            } else if (locked) {
                miniInfo.setText("잠금\n막음 " + blocked);
            } else if (inTest) {
                miniInfo.setText("터치\n" + touches + "/" + limit);
            } else {
                miniInfo.setText("수집");
            }
            miniInfo.setTextColor((pausedNow || touches > 0 || blocked > 0) ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
        }
    }

    private LinearLayout chip(Context context, int iconRes, String label, TextView labelView) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(s.dp(context, 10), 0, s.dp(context, 12), 0);
        GradientDrawable base = s.roundRect(context, CHIP, 999, 0);
        chip.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), base, null));
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(label);
        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(ON_PANEL);
        icon.setImportantForAccessibility(2);
        chip.addView(icon, new LayoutParams(s.dp(context, 18), s.dp(context, 18)));
        labelView.setText(label);
        labelView.setTextSize(12.0f);
        labelView.setTextColor(ON_PANEL);
        labelView.setTypeface(Typeface.create("sans-serif-medium", 0));
        LayoutParams lp = new LayoutParams(-2, -2);
        lp.leftMargin = s.dp(context, 6);
        chip.addView(labelView, lp);
        return chip;
    }

    /** Seconds left before the run limit; shown next to "실행 중" and turns amber in the last minute. */
    public void setRemaining(long seconds) {
        if (seconds == remainingSeconds) {
            return;
        }
        remainingSeconds = seconds;
        if (running) {
            updateCaption();
            if (compact) {
                miniTime.setText(seconds < 0 ? "" : AppStyle.formatClock(seconds));
                miniTime.setTextColor(pausedNow || (seconds >= 0 && seconds <= 60) ? Color.rgb(255, 196, 92) : ON_PANEL);
            }
        }
    }

    private void updateCaption() {
        AppStyle s = AppStyle.INSTANCE;
        if (!running) {
            caption.setText("단어 도우미");
            caption.setTextColor(ON_PANEL_DIM);
            return;
        }
        if (remainingSeconds < 0) {
            caption.setText(pausedNow ? "일시정지" : "실행 중");
            caption.setTextColor(ON_PANEL_DIM);
            return;
        }
        caption.setText((pausedNow ? "일시정지 · 남은 " : "실행 중 · 남은 ") + AppStyle.formatClock(remainingSeconds));
        caption.setTextColor(remainingSeconds <= 60 ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
    }

    public TextView getStatusText() {
        return statusText;
    }

    public Button getPrimary() {
        return primary;
    }

    /**
     * @param active       a run is in progress
     * @param status       text to show while running
     * @param canSettings  scroll settings may be changed now (idle or collecting)
     * @param canHide      the panel may still be switched off (before the test)
     */
    public void render(boolean active, String status, boolean canSettings, boolean canHide) {
        AppStyle s = AppStyle.INSTANCE;
        Context c = getContext();
        statusText.setText(AppStyle.keepWords(active ? status : "현재 화면에서 시작"));
        running = active;
        updateCaption();
        s.setDotColor(dot, active ? s.getInfo() : s.getAccent());
        primary.setText(active ? "중지" : "시작");
        primary.setContentDescription(active ? "자동 풀이 중지" : "현재 화면에서 자동 수집과 테스트 시작");
        GradientDrawable base = s.roundRect(c, active ? s.getDanger() : s.getAccent(), 999, 0);
        primary.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), base, null));
        settingsChip.setVisibility(canSettings ? VISIBLE : GONE);
        hideChip.setVisibility(canHide ? VISIBLE : GONE);
        tools.setVisibility((canSettings || canHide) ? VISIBLE : GONE);
        applySettingsVisibility(!active && canSettings);
    }

    /** The settings are open by default while idle; the chip folds them away. */
    private void applySettingsVisibility(boolean allowed) {
        boolean show = allowed && !userCollapsed;
        if (show && !settingsShown) {
            // values may have been changed in the app since the panel was built
            sliders.removeAllViews();
            Screens.addPanelSettings(getContext(), sliders, panelPrefs);
        }
        settingsShown = show;
        settingsScroll.setVisibility(show ? VISIBLE : GONE);
        settingsLabel.setText(userCollapsed ? "설정 펼치기" : "설정 접기");
    }
}
