package net.classcardauto.mobile;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The card that floats above ClassCard. It shows exactly one of five layouts at a time:
 * <ul>
 *   <li>CARD — idle: header (move · status · 시작), a toolbar (작게 · 끄기 · 설정) and the settings (run mode, mistakes, a way to the advanced settings);</li>
 *   <li>START — idle, minimized: only the start button;</li>
 *   <li>TAB — idle, folded into a small tab on the screen edge;</li>
 *   <li>MEASURE — the personal pace measurement is running: what to do and a button that ends it;</li>
 *   <li>STRIP — a run is going: a narrow vertical strip (time, pause, stop, touches).</li>
 * </ul>
 * It only builds and updates views; BotService owns the window and decides what the buttons do.
 */
public final class FloatingPanel extends LinearLayout {
    public interface Host {
        void onPrimary();

        /** Kept for callers built against the older panel; the panel no longer offers a separate hide button. */
        void onHide();

        /** Called while a handle is dragged, with the movement since the touch started (pixels). */
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

    private enum Mode { CARD, START, TAB, MEASURE, STRIP }

    static final int PANEL = Color.argb(244, 15, 21, 20);
    static final int ON_PANEL = Color.WHITE;
    static final int ON_PANEL_DIM = Color.argb(150, 255, 255, 255);
    static final int CHIP = Color.argb(22, 255, 255, 255);
    static final int HAIRLINE = Color.argb(24, 255, 255, 255);
    static final int AMBER = Color.rgb(255, 196, 92);

    private final Host host;
    private final SharedPreferences prefs;
    /** Carries the background and all five layouts; the panel itself is only the transparent window root. */
    private final LinearLayout body;
    private final GradientDrawable bodyBg;
    private ValueAnimator morphAnim;
    private Boolean foldRight;

    // CARD
    private final LinearLayout cardRow;
    private final ImageView foldTab;
    private final View dot;
    private final TextView caption;
    private final TextView statusText;
    private final Button primary;
    private final TextView settingsToggle;
    private final LinearLayout settingsBox;
    private final LinearLayout groupBody;
    private final ScrollView groupScroll;
    // START / TAB
    private final LinearLayout startOnly;
    private final LinearLayout edgeTab;
    private final ImageView edgeArrow;
    // MEASURE
    private final LinearLayout measure;
    private final TextView measureText;
    // STRIP
    private final LinearLayout strip;
    private final TextView stripTime;
    private final TextView stripEta;
    private final ImageView stripPauseIcon;
    private final TextView stripInfo;

    private boolean running;
    private boolean pausedNow;
    private boolean calibrating;
    private boolean minimized;
    private boolean collapsed;
    private boolean settingsOpen = true;
    private long remainingSeconds = -1;
    private long etaSeconds = -1;
    private Mode mode;

    public FloatingPanel(final Context context, SharedPreferences prefs, final Host host) {
        super(context);
        this.host = host;
        this.prefs = prefs;
        final AppStyle s = AppStyle.INSTANCE;
        setOrientation(VERTICAL);
        body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        bodyBg = s.roundRect(context, PANEL, 22, HAIRLINE);
        body.setBackground(bodyBg);
        body.setElevation(dp(8));
        super.addView(body, new LayoutParams(-2, -2));

        // ---------------------------------------------------------------- CARD
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(handle(context, false), new LayoutParams(dp(24), dp(40)));
        dot = s.dot(context, s.getAccent(), 7);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        LinearLayout brandRow = new LinearLayout(context);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams dotParams = new LayoutParams(dp(7), dp(7));
        dotParams.rightMargin = dp(6);
        brandRow.addView(dot, dotParams);
        caption = s.label(context, "MiMiC", 10.5f, ON_PANEL_DIM, true);
        caption.setLetterSpacing(0.06f);
        brandRow.addView(caption);
        texts.addView(brandRow);
        statusText = s.label(context, "", 13.0f, ON_PANEL, true);
        statusText.setMaxLines(2);
        LayoutParams stp0 = new LayoutParams(-2, -2);
        stp0.topMargin = dp(1);
        texts.addView(statusText, stp0);
        header.addView(texts, new LayoutParams(0, -2, 1.0f));
        primary = pill(context, "시작", s.getAccent(), 13.0f, 18);
        primary.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        LayoutParams pp = new LayoutParams(-2, dp(36));
        pp.leftMargin = dp(8);
        header.addView(primary, pp);
        card.addView(header, new LayoutParams(-1, -2));

        // toolbar: quiet text actions under a hairline
        View rule = new View(context);
        rule.setBackgroundColor(HAIRLINE);
        LayoutParams rp = new LayoutParams(-1, dp(1));
        rp.topMargin = dp(6);
        rp.leftMargin = dp(4);
        rp.rightMargin = dp(4);
        card.addView(rule, rp);
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView small = tool(context, "작게");
        small.setContentDescription("시작 버튼만 남기기");
        small.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minimized = true;
                applyMode();
            }
        });
        TextView off = tool(context, "끄기");
        off.setContentDescription("앱을 다시 열 때까지 패널 끄기");
        off.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onTurnOff();
            }
        });
        settingsToggle = tool(context, "");
        settingsToggle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (host.isBlocked()) {
                    return;
                }
                settingsOpen = !settingsOpen;
                applySettings();
            }
        });
        toolbar.addView(small, toolParams());
        toolbar.addView(off, toolParams());
        View spacer = new View(context);
        toolbar.addView(spacer, new LayoutParams(0, 1, 1.0f));
        toolbar.addView(settingsToggle, toolParams());
        LayoutParams tbp = new LayoutParams(-1, dp(34));
        card.addView(toolbar, tbp);

        // settings: the run mode and the mistake count; everything else lives in the app
        settingsBox = new LinearLayout(context);
        settingsBox.setOrientation(VERTICAL);
        groupBody = new LinearLayout(context);
        groupBody.setOrientation(VERTICAL);
        groupBody.setPadding(dp(6), dp(2), dp(6), dp(4));
        groupScroll = new ScrollView(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                // grow with the group, but never taller than 300dp
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(dp(300), MeasureSpec.AT_MOST));
            }
        };
        groupScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        groupScroll.addView(groupBody, new android.view.ViewGroup.LayoutParams(-1, -2));
        LayoutParams gsp = new LayoutParams(-1, -2);
        gsp.topMargin = dp(4);
        settingsBox.addView(groupScroll, gsp);
        card.addView(settingsBox, new LayoutParams(-1, -2));

        cardRow = new LinearLayout(context);
        cardRow.setOrientation(HORIZONTAL);
        foldTab = new ImageView(context);
        foldTab.setImageResource(R.drawable.ic_chevron);
        foldTab.setColorFilter(ON_PANEL);
        foldTab.setPadding(dp(3), dp(22), dp(3), dp(22));
        foldTab.setAlpha(0.7f);
        foldTab.setBackground(ripple(CHIP, 999));
        foldTab.setContentDescription("패널을 화면 가장자리로 접기");
        foldTab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsed = true;
                applyMode();
                host.onLayoutChanged();
            }
        });
        cardRow.addView(card, new LayoutParams(dp(248), -2));
        body.addView(cardRow, new LayoutParams(-2, -2));

        // --------------------------------------------------------------- START
        startOnly = new LinearLayout(context);
        startOnly.setOrientation(HORIZONTAL);
        startOnly.setGravity(Gravity.CENTER_VERTICAL);
        startOnly.addView(handle(context, false), new LayoutParams(dp(24), dp(36)));
        Button startBtn = pill(context, "시작", s.getAccent(), 13.0f, 16);
        startBtn.setContentDescription("현재 화면에서 자동 수집과 테스트 시작");
        startBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        startOnly.addView(startBtn, new LayoutParams(-2, dp(36)));
        ImageView expand = new ImageView(context);
        expand.setImageResource(R.drawable.ic_tune);
        expand.setColorFilter(ON_PANEL);
        expand.setPadding(dp(7), dp(7), dp(7), dp(7));
        expand.setBackground(ripple(CHIP, 999));
        expand.setContentDescription("패널 크게 보기");
        expand.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minimized = false;
                applyMode();
            }
        });
        LayoutParams exp = new LayoutParams(dp(34), dp(34));
        exp.leftMargin = dp(6);
        startOnly.addView(expand, exp);
        body.addView(startOnly, new LayoutParams(-2, -2));

        // ----------------------------------------------------------------- TAB
        edgeTab = new LinearLayout(context);
        edgeTab.setOrientation(VERTICAL);
        edgeTab.setGravity(Gravity.CENTER);
        edgeTab.setContentDescription("패널 펼치기");
        edgeTab.setBackground(s.roundRect(context, PANEL, 12, 0));
        edgeArrow = new ImageView(context);
        edgeArrow.setImageResource(R.drawable.ic_chevron);
        edgeArrow.setColorFilter(ON_PANEL);
        edgeTab.addView(edgeArrow, new LayoutParams(dp(20), dp(20)));
        edgeTab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsed = false;
                applyMode();
                host.onLayoutChanged();
            }
        });
        body.addView(edgeTab, new LayoutParams(dp(24), dp(96)));

        // ------------------------------------------------------------- MEASURE
        measure = new LinearLayout(context);
        measure.setOrientation(VERTICAL);
        LinearLayout mHead = new LinearLayout(context);
        mHead.setOrientation(HORIZONTAL);
        mHead.setGravity(Gravity.CENTER_VERTICAL);
        mHead.addView(handle(context, false), new LayoutParams(dp(24), dp(32)));
        View mDot = s.dot(context, AMBER, 8);
        LayoutParams mdp = new LayoutParams(dp(8), dp(8));
        mdp.leftMargin = dp(4);
        mdp.rightMargin = dp(8);
        mHead.addView(mDot, mdp);
        mHead.addView(s.label(context, "내 패턴 측정 중", 12.5f, ON_PANEL, true), new LayoutParams(-2, -2));
        measure.addView(mHead, new LayoutParams(-1, -2));
        measureText = s.label(context, "", 11.5f, ON_PANEL_DIM, false);
        measureText.setLineSpacing(0, 1.1f);
        LayoutParams mtp = new LayoutParams(-1, -2);
        mtp.leftMargin = dp(6);
        mtp.topMargin = dp(2);
        measure.addView(measureText, mtp);
        Button endMeasure = pill(context, "측정 끝내기", s.getAccent(), 12.5f, 14);
        endMeasure.setContentDescription("측정 끝내기 (3문항 이상이면 반영)");
        endMeasure.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BotService service = BotService.INSTANCE.getInstance();
                if (service != null) {
                    service.toggleCalibration();
                }
            }
        });
        LayoutParams emp = new LayoutParams(-1, dp(34));
        emp.topMargin = dp(8);
        measure.addView(endMeasure, emp);
        body.addView(measure, new LayoutParams(dp(220), -2));

        // --------------------------------------------------------------- STRIP
        strip = new LinearLayout(context);
        strip.setOrientation(VERTICAL);
        strip.setGravity(Gravity.CENTER_HORIZONTAL);
        strip.addView(handle(context, true), new LayoutParams(dp(40), dp(22)));
        stripTime = s.label(context, "", 12.0f, ON_PANEL, true);
        stripTime.setGravity(Gravity.CENTER);
        strip.addView(stripTime, new LayoutParams(-2, -2));
        stripEta = s.label(context, "", 9.5f, ON_PANEL_DIM, false);
        stripEta.setGravity(Gravity.CENTER);
        stripEta.setVisibility(GONE);
        strip.addView(stripEta, new LayoutParams(-2, -2));
        LinearLayout pause = new LinearLayout(context);
        pause.setGravity(Gravity.CENTER);
        pause.setBackground(ripple(CHIP, 999));
        pause.setClickable(true);
        stripPauseIcon = new ImageView(context);
        stripPauseIcon.setImageResource(R.drawable.ic_pause);
        stripPauseIcon.setColorFilter(ON_PANEL);
        pause.addView(stripPauseIcon, new LayoutParams(dp(20), dp(20)));
        pause.setContentDescription("일시정지");
        pause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPause();
                }
            }
        });
        LayoutParams spp = new LayoutParams(dp(38), dp(38));
        spp.topMargin = dp(6);
        strip.addView(pause, spp);
        ImageView stop = new ImageView(context);
        stop.setImageResource(R.drawable.ic_stop);
        stop.setColorFilter(Color.WHITE);
        stop.setPadding(dp(10), dp(10), dp(10), dp(10));
        stop.setBackground(ripple(s.getDanger(), 999));
        stop.setContentDescription("자동 풀이 중지");
        stop.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!host.isBlocked()) {
                    host.onPrimary();
                }
            }
        });
        LayoutParams stp = new LayoutParams(dp(38), dp(38));
        stp.topMargin = dp(6);
        strip.addView(stop, stp);
        stripInfo = s.label(context, "", 10.0f, ON_PANEL_DIM, true);
        stripInfo.setGravity(Gravity.CENTER);
        LayoutParams sip = new LayoutParams(-2, -2);
        sip.topMargin = dp(4);
        strip.addView(stripInfo, sip);
        body.addView(strip, new LayoutParams(-2, -2));

        applySettings();
        applyMode();
    }

    private SharedPreferences prefs() {
        return prefs;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            host.onPanelTouch(ev.getEventTime());
        }
        return super.dispatchTouchEvent(ev);
    }

    // ------------------------------------------------------------------ public API

    public TextView getStatusText() {
        return statusText;
    }

    public Button getPrimary() {
        return primary;
    }

    /**
     * @param active      a run is in progress
     * @param status      text to show while running
     * @param canSettings settings may be changed now (idle or collecting)
     * @param canHide     unused; kept so callers built for the older panel still compile
     */
    public void render(boolean active, String status, boolean canSettings, boolean canHide) {
        AppStyle s = AppStyle.INSTANCE;
        boolean wasRunning = running;
        running = active;
        statusText.setText(AppStyle.keepWords(active ? status : "현재 화면에서 시작"));
        primary.setText(active ? "중지" : "시작");
        primary.setContentDescription(active ? "자동 풀이 중지" : "현재 화면에서 자동 수집과 테스트 시작");
        primary.setBackground(ripple(active ? s.getDanger() : s.getAccent(), 999));
        s.setDotColor(dot, active ? s.getInfo() : s.getAccent());
        if (active) {
            collapsed = false;
        }
        if (wasRunning && !active) {
            // values may have been changed in the app (or by the measurement) during the run
            fillGroup();
        }
        updateCaption();
        applyMode();
    }

    /** The personal pace measurement is on (or off); {@code status} says what to do next. */
    public void setCalibrating(boolean on, String status) {
        boolean changed = calibrating != on;
        calibrating = on;
        if (on) {
            measureText.setText(AppStyle.keepWords(status == null ? "" : status));
        }
        if (changed) {
            if (!on) {
                fillGroup();
            }
            applyMode();
            host.onLayoutChanged();
        }
    }

    public void renderRun(boolean paused, int touches, int limit, boolean inTest) {
        renderRun(paused, touches, limit, inTest, false, 0);
    }

    /**
     * Pause state and the touch counters, shown in the strip while a run is going.
     *
     * @param touches touches by the user since the run started or was last resumed
     * @param limit   touches that pause the bot
     * @param locked  the screen is locked against touches
     * @param blocked touches the lock swallowed
     */
    public void renderRun(boolean paused, int touches, int limit, boolean inTest, boolean locked, int blocked) {
        AppStyle s = AppStyle.INSTANCE;
        pausedNow = paused && running;
        s.setDotColor(dot, !running ? s.getAccent() : (pausedNow ? s.getWarn() : s.getInfo()));
        updateCaption();
        if (!running) {
            return;
        }
        stripPauseIcon.setImageResource(pausedNow ? R.drawable.ic_play : R.drawable.ic_pause);
        ((View) stripPauseIcon.getParent()).setContentDescription(pausedNow ? "재개" : "일시정지");
        showTime();
        if (pausedNow) {
            stripInfo.setText("정지\n터치 " + touches);
        } else if (locked) {
            stripInfo.setText("잠금\n막음 " + blocked);
        } else if (inTest) {
            stripInfo.setText("터치\n" + touches + "/" + limit);
        } else {
            stripInfo.setText("수집");
        }
        stripInfo.setTextColor(pausedNow || touches > 0 || blocked > 0 ? AMBER : ON_PANEL_DIM);
    }

    /** Seconds left before the run limit; turns amber in the last minute. */
    public void setRemaining(long seconds) {
        if (seconds == remainingSeconds) {
            return;
        }
        remainingSeconds = seconds;
        if (running) {
            updateCaption();
            showTime();
        }
    }

    /** Estimated seconds until the test is answered completely (from this run's own pace); -1 hides it. */
    public void setEta(long seconds) {
        if (seconds == etaSeconds) {
            return;
        }
        etaSeconds = seconds;
        if (seconds < 0) {
            stripEta.setVisibility(GONE);
        } else {
            stripEta.setText("끝까지 " + AppStyle.formatClock(seconds));
            stripEta.setVisibility(VISIBLE);
        }
    }

    /** Rebuilds the open settings so it shows values changed elsewhere (e.g. by the pace measurement). */
    public void rebuildSettings() {
        fillGroup();
    }

    /** Folds the idle card into its edge tab (or unfolds it). */
    public void setCollapsed(boolean on) {
        collapsed = on;
        applyMode();
    }

    /** Shows only the start button (while idle) or the full card again. */
    public void setMinimized(boolean on) {
        minimized = on;
        applyMode();
    }

    // ------------------------------------------------------------------ layout

    private Mode pickMode() {
        if (running) {
            return Mode.STRIP;
        }
        if (calibrating) {
            return Mode.MEASURE;
        }
        if (collapsed) {
            return Mode.TAB;
        }
        return minimized ? Mode.START : Mode.CARD;
    }

    private void applyMode() {
        Mode next = pickMode();
        if (mode != null && next != mode && canMorph()) {
            final Mode target = next;
            morph(new Runnable() {
                @Override
                public void run() {
                    applyModeNow(target);
                }
            });
            return;
        }
        applyModeNow(next);
    }

    private void applyModeNow(Mode next) {
        mode = next;
        cardRow.setVisibility(mode == Mode.CARD ? VISIBLE : GONE);
        startOnly.setVisibility(mode == Mode.START ? VISIBLE : GONE);
        edgeTab.setVisibility(mode == Mode.TAB ? VISIBLE : GONE);
        measure.setVisibility(mode == Mode.MEASURE ? VISIBLE : GONE);
        strip.setVisibility(mode == Mode.STRIP ? VISIBLE : GONE);

        boolean right = host.isDockedRight();
        // the fold tab sits on the card's inner side and points at the edge it folds into; the edge tab points back
        foldTab.setRotation(right ? 0 : 180);
        edgeArrow.setRotation(right ? 180 : 0);
        if (foldRight == null || foldRight != right) {
            foldRight = right;
            cardRow.removeView(foldTab);
            LayoutParams ftp = new LayoutParams(dp(18), dp(60));
            ftp.topMargin = dp(4);
            if (right) {
                ftp.rightMargin = dp(6);
                cardRow.addView(foldTab, 0, ftp);
            } else {
                ftp.leftMargin = dp(6);
                cardRow.addView(foldTab, ftp);
            }
        }

        // the bar disappears behind the edge tab instead of popping away
        bodyBg.setAlpha(mode == Mode.TAB ? 0 : 255);
        switch (mode) {
            case TAB:
                body.setPadding(0, 0, 0, 0);
                break;
            case STRIP:
                body.setPadding(dp(4), dp(4), dp(4), dp(6));
                break;
            case START:
                body.setPadding(dp(4), dp(4), dp(6), dp(4));
                break;
            case MEASURE:
                body.setPadding(dp(8), dp(8), dp(12), dp(10));
                break;
            default:
                body.setPadding(dp(8), dp(8), dp(10), dp(8));
                break;
        }
    }

    // ------------------------------------------------------------------ motion

    /** True when the panel is on screen as a window and animations are not switched off in the system. */
    private boolean canMorph() {
        return isAttachedToWindow() && body.isLaidOut() && getVisibility() == VISIBLE && body.getWidth() > 0
                && getLayoutParams() instanceof WindowManager.LayoutParams && ValueAnimator.areAnimatorsEnabled();
    }

    private void stopMorph() {
        if (morphAnim != null) {
            morphAnim.removeAllListeners();
            morphAnim.removeAllUpdateListeners();
            morphAnim.cancel();
            morphAnim = null;
        }
    }

    /**
     * Applies {@code change} (which switches what is shown) while the card grows or shrinks from its old size to the
     * new one, like an island: the outgoing layout fades, the incoming one fades in a beat later, and the size
     * follows a soft spring. The window is held at the larger of the two sizes while it runs and the card stays
     * glued to the screen edge it is docked to.
     */
    private void morph(Runnable change) {
        stopMorph();
        final boolean right = host.isDockedRight();
        final int ow = body.getWidth();
        final int oh = body.getHeight();
        TransitionSet fades = new TransitionSet();
        fades.setOrdering(TransitionSet.ORDERING_TOGETHER);
        Fade out = new Fade(Fade.OUT);
        out.setDuration(120);
        Fade in = new Fade(Fade.IN);
        in.setStartDelay(130);
        in.setDuration(240);
        fades.addTransition(out);
        fades.addTransition(in);
        TransitionManager.beginDelayedTransition(body, fades);

        final LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) body.getLayoutParams();
        blp.width = -2;
        blp.height = -2;
        blp.gravity = right ? Gravity.END : Gravity.START;
        change.run();
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        body.measure(MeasureSpec.makeMeasureSpec(dm.widthPixels, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dm.heightPixels, MeasureSpec.AT_MOST));
        final int nw = body.getMeasuredWidth();
        final int nh = body.getMeasuredHeight();
        if (nw == ow && nh == oh) {
            body.setLayoutParams(blp);
            return;
        }
        blp.width = ow;
        blp.height = oh;
        body.setLayoutParams(blp);
        int pad = dp(12);
        setWindowSize(Math.max(ow, nw) + pad, Math.max(oh, nh) + pad, right);

        morphAnim = ValueAnimator.ofFloat(0f, 1f);
        morphAnim.setDuration(460);
        morphAnim.setInterpolator(new PathInterpolator(0.34f, 1.45f, 0.64f, 1f));
        morphAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                blp.width = Math.max(1, ow + Math.round((nw - ow) * t));
                blp.height = Math.max(1, oh + Math.round((nh - oh) * t));
                body.setLayoutParams(blp);
            }
        });
        morphAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                morphAnim = null;
                blp.width = -2;
                blp.height = -2;
                body.setLayoutParams(blp);
                setWindowSize(-2, -2, right);
                host.onLayoutChanged();
            }
        });
        morphAnim.start();
    }

    /** Resizes the overlay window; when it is docked to the left edge, the left edge stays where it is. */
    private void setWindowSize(int w, int h, boolean dockedRight) {
        if (!(getLayoutParams() instanceof WindowManager.LayoutParams)) {
            return;
        }
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        int curW = getWidth();
        int newW = w == -2 ? body.getMeasuredWidth() : w;
        if (!dockedRight && curW > 0) {
            // gravity is top|end: x counts from the right edge
            lp.x = Math.max(0, lp.x + curW - newW);
        }
        lp.width = w;
        lp.height = h;
        try {
            ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).updateViewLayout(this, lp);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void setVisibility(int visibility) {
        boolean appearing = visibility == VISIBLE && getVisibility() != VISIBLE;
        super.setVisibility(visibility);
        if (appearing && isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled()) {
            // it rises out of the edge it is docked to
            body.post(new Runnable() {
                @Override
                public void run() {
                    body.setPivotX(host.isDockedRight() ? body.getWidth() : 0);
                    body.setPivotY(0);
                    body.setScaleX(0.86f);
                    body.setScaleY(0.86f);
                    body.setAlpha(0f);
                    body.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320).setInterpolator(new PathInterpolator(0.34f, 1.35f, 0.64f, 1f)).start();
                }
            });
        }
    }

    private void applySettings() {
        if (canMorph() && settingsBox.getVisibility() != (settingsOpen ? VISIBLE : GONE)) {
            morph(new Runnable() {
                @Override
                public void run() {
                    applySettingsNow();
                }
            });
            return;
        }
        applySettingsNow();
    }

    private void applySettingsNow() {
        settingsBox.setVisibility(settingsOpen ? VISIBLE : GONE);
        settingsToggle.setText(settingsOpen ? "설정 접기" : "설정 열기");
        settingsToggle.setContentDescription(settingsOpen ? "설정 접기" : "설정 펼치기");
        if (settingsOpen) {
            fillGroup();
        }
    }

    /** Fills the settings area (read fresh from the preferences). */
    private void fillGroup() {
        if (!settingsOpen) {
            return;
        }
        groupBody.removeAllViews();
        Screens.addPanelMain(getContext(), groupBody, prefs);
        groupScroll.scrollTo(0, 0);
    }

    private void showTime() {
        stripTime.setText(remainingSeconds < 0 ? "" : AppStyle.formatClock(remainingSeconds));
        stripTime.setTextColor(pausedNow || (remainingSeconds >= 0 && remainingSeconds <= 60) ? AMBER : ON_PANEL);
    }

    private void updateCaption() {
        if (!running) {
            caption.setText("MiMiC");
            caption.setTextColor(ON_PANEL_DIM);
            return;
        }
        String head = pausedNow ? "일시정지" : "실행 중";
        caption.setText(remainingSeconds < 0 ? head : head + " · 남은 " + AppStyle.formatClock(remainingSeconds));
        caption.setTextColor(remainingSeconds >= 0 && remainingSeconds <= 60 ? AMBER : ON_PANEL_DIM);
    }

    // ------------------------------------------------------------------ helpers

    private int dp(int v) {
        return AppStyle.INSTANCE.dp(getContext(), v);
    }

    private Drawable ripple(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), AppStyle.INSTANCE.roundRect(getContext(), color, radius, 0), null);
    }

    private Button pill(Context context, String text, int color, float size, int padH) {
        Button b = new Button(context);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(size);
        b.setTypeface(Typeface.create("sans-serif-medium", 0));
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(padH), 0, dp(padH), 0);
        b.setBackground(ripple(color, 999));
        return b;
    }

    private TextView tool(Context context, String text) {
        TextView t = AppStyle.INSTANCE.label(context, text, 12.0f, ON_PANEL_DIM, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), 0, dp(10), 0);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(40, 255, 255, 255)), null, AppStyle.INSTANCE.roundRect(context, Color.WHITE, 999, 0)));
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private LayoutParams toolParams() {
        return new LayoutParams(-2, -1);
    }

    private ImageView handle(Context context, boolean vertical) {
        ImageView h = new ImageView(context);
        h.setImageResource(R.drawable.ic_drag);
        h.setColorFilter(ON_PANEL_DIM);
        h.setContentDescription("패널 이동");
        if (vertical) {
            h.setRotation(90);
        } else {
            h.setPadding(dp(2), dp(8), dp(2), dp(8));
        }
        h.setOnTouchListener(new OnTouchListener() {
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
        return h;
    }
}
