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

        /** "끄기": the panel disappears until the app is opened again (only offered while idle). */
        void onTurnOff();

        /** The panel sits on the right half of the screen (so it docks to the right edge). */
        boolean isDockedRight();

        /** The panel changed size (folded to / unfolded from its edge tab); re-dock it to the edge. */
        void onLayoutChanged();
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
    private final LinearLayout startOnly;
    private final LinearLayout fullRow;
    private final ImageView foldTab;
    private final LinearLayout tab;
    private final ImageView tabArrow;
    private boolean collapsed;
    private boolean minimized;
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
        caption = s.label(context, "MiMiC", 10.5f, ON_PANEL_DIM, false);
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
        LinearLayout minimizeChip = chip(context, R.drawable.ic_chevron, "작게", new TextView(context));
        ((ImageView) minimizeChip.getChildAt(0)).setRotation(90);
        minimizeChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minimized = true;
                applyLayout();
            }
        });
        LayoutParams mcp = new LayoutParams(-2, s.dp(context, 34));
        tools.addView(minimizeChip, mcp);
        LinearLayout offChip = chip(context, R.drawable.ic_hide, "끄기", new TextView(context));
        offChip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onTurnOff();
            }
        });
        LayoutParams ocp = new LayoutParams(-2, s.dp(context, 34));
        ocp.leftMargin = s.dp(context, 8);
        tools.addView(offChip, ocp);
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
        // the big card carries a fold tab on its inner side: pressing it slides the card away, leaving only the edge tab
        fullRow = new LinearLayout(context);
        fullRow.setOrientation(HORIZONTAL);
        foldTab = new ImageView(context);
        foldTab.setImageResource(R.drawable.ic_chevron);
        foldTab.setColorFilter(ON_PANEL);
        foldTab.setPadding(s.dp(context, 2), s.dp(context, 20), s.dp(context, 2), s.dp(context, 20));
        foldTab.setBackground(s.roundRect(context, CHIP, 10, 0));
        foldTab.setContentDescription("패널 접기");
        foldTab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsed = true;
                applyLayout();
                host.onLayoutChanged();
            }
        });
        fullRow.addView(full, new LayoutParams(-2, -2));
        addView(fullRow, 0, new LayoutParams(-2, -2));

        // start-only strip: drag handle, start button, and a button that opens the full card again
        startOnly = new LinearLayout(context);
        startOnly.setOrientation(HORIZONTAL);
        startOnly.setGravity(Gravity.CENTER_VERTICAL);
        startOnly.setVisibility(GONE);
        ImageView startHandle = new ImageView(context);
        startHandle.setImageResource(R.drawable.ic_drag);
        startHandle.setColorFilter(ON_PANEL_DIM);
        startHandle.setContentDescription("패널 이동");
        startHandle.setOnTouchListener(dragListener(host));
        startOnly.addView(startHandle, new LayoutParams(s.dp(context, 24), s.dp(context, 36)));
        Button startBtn = new Button(context);
        startBtn.setAllCaps(false);
        startBtn.setText("시작");
        startBtn.setTextSize(13.0f);
        startBtn.setTypeface(Typeface.create("sans-serif-medium", 0));
        startBtn.setTextColor(-1);
        startBtn.setStateListAnimator(null);
        startBtn.setElevation(0);
        startBtn.setMinWidth(0);
        startBtn.setMinimumWidth(0);
        startBtn.setMinHeight(0);
        startBtn.setMinimumHeight(0);
        startBtn.setPadding(s.dp(context, 16), 0, s.dp(context, 16), 0);
        startBtn.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)), s.roundRect(context, s.getAccent(), 999, 0), null));
        startBtn.setContentDescription("현재 화면에서 자동 수집과 테스트 시작");
        startBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        startOnly.addView(startBtn, new LayoutParams(-2, s.dp(context, 36)));
        ImageView expand = new ImageView(context);
        expand.setImageResource(R.drawable.ic_tune);
        expand.setColorFilter(ON_PANEL);
        expand.setPadding(s.dp(context, 7), s.dp(context, 7), s.dp(context, 7), s.dp(context, 7));
        expand.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), s.roundRect(context, CHIP, 999, 0), null));
        expand.setContentDescription("설정 펼치기");
        expand.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minimized = false;
                applyLayout();
            }
        });
        LayoutParams exp = new LayoutParams(s.dp(context, 34), s.dp(context, 34));
        exp.leftMargin = s.dp(context, 6);
        startOnly.addView(expand, exp);
        addView(startOnly, new LayoutParams(-2, -2));

        // edge tab: what is left of the strip when it is folded away; tapping it unfolds the strip again
        tab = new LinearLayout(context);
        tab.setOrientation(VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setVisibility(GONE);
        tab.setContentDescription("패널 펼치기");
        tabArrow = new ImageView(context);
        tabArrow.setImageResource(R.drawable.ic_chevron);
        tabArrow.setColorFilter(ON_PANEL);
        tab.addView(tabArrow, new LayoutParams(s.dp(context, 20), s.dp(context, 20)));
        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsed = false;
                applyLayout();
                host.onLayoutChanged();
            }
        });
        addView(tab, new LayoutParams(s.dp(context, 24), s.dp(context, 96)));
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
        compact = on;
        applyLayout();
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

    /** Folds the run strip into its edge tab (or unfolds it). */
    public void setCollapsed(boolean on) {
        collapsed = on;
        applyLayout();
    }

    /** Shows only the start button (while idle) or the full card again. */
    public void setMinimized(boolean on) {
        minimized = on;
        applyLayout();
    }

    /** Picks which of the three layouts is visible: the strip while running, the start-only strip, or the full card. */
    private void applyLayout() {
        boolean startMode = !running && minimized && !compact && !collapsed;
        boolean tabMode = collapsed && !running;
        fullRow.setVisibility(compact || startMode || tabMode ? GONE : VISIBLE);
        mini.setVisibility(compact ? VISIBLE : GONE);
        startOnly.setVisibility(startMode ? VISIBLE : GONE);
        tab.setVisibility(tabMode ? VISIBLE : GONE);
        AppStyle s = AppStyle.INSTANCE;
        boolean right = hostRef.isDockedRight();
        // the fold tab sits on the card's inner side and points at the edge it folds into; the edge tab points back
        foldTab.setRotation(right ? 0 : 180);
        tabArrow.setRotation(right ? 180 : 0);
        fullRow.removeView(foldTab);
        LinearLayout.LayoutParams ftp = new LinearLayout.LayoutParams(s.dp(getContext(), 22), s.dp(getContext(), 64));
        ftp.topMargin = s.dp(getContext(), 4);
        if (right) {
            ftp.rightMargin = s.dp(getContext(), 6);
            fullRow.addView(foldTab, 0, ftp);
        } else {
            ftp.leftMargin = s.dp(getContext(), 6);
            fullRow.addView(foldTab, ftp);
        }
        tab.setBackground(s.roundRect(getContext(), PANEL, 12, 0));
        if (tabMode) {
            setBackground(null);
            setElevation(0);
            setPadding(0, 0, 0, 0);
        } else {
            setBackground(s.roundRect(getContext(), PANEL, 22, Color.argb(40, 255, 255, 255)));
            setElevation(s.dp(getContext(), 6));
        }
        if (tabMode) {
            // no padding around the bare tab
        } else if (compact) {
            setPadding(s.dp(getContext(), 4), s.dp(getContext(), 4), s.dp(getContext(), 4), s.dp(getContext(), 6));
        } else if (startMode) {
            setPadding(s.dp(getContext(), 4), s.dp(getContext(), 4), s.dp(getContext(), 6), s.dp(getContext(), 4));
        } else {
            setPadding(padL, padT, padR, padB);
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
            caption.setText("MiMiC");
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
        if (active) {
            collapsed = false;
        }
        tools.setVisibility((canSettings || canHide) ? VISIBLE : GONE);
        applySettingsVisibility(!active && canSettings);
        applyLayout();
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
