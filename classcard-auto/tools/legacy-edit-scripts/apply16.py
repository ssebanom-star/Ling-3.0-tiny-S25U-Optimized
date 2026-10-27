import re
exec(open('/tmp/claude-0/work/fix.py').read())

def read(p):
    return open(D + p).read()

def write(p, s):
    open(D + p, 'w').write(s)

def sub(p, a, b, count=1):
    s = read(p)
    assert a in s, (p, a[:110])
    write(p, s.replace(a, b, count))

# ---------------- AdvancedSettings: how many user touches pause the run ----------------
sub('AdvancedSettings.java', '    // ---- deliberate mistakes ----', '''    /** How many touches by the user during the test pause the run. */
    public static final int TOUCH_MIN = 1, TOUCH_MAX = 5, TOUCH_DEFAULT = 2, TOUCH_STEP = 1;

    // ---- deliberate mistakes ----''')
sub('AdvancedSettings.java', '    private static final String K_WRONG = "wrong_count";', '    private static final String K_WRONG = "wrong_count";\n    private static final String K_TOUCH = "limit_touch_pause";')
sub('AdvancedSettings.java', '    public static int wrongCount(SharedPreferences p) {', '''    public static int touchPauseCount(SharedPreferences p) {
        return clamp(p.getInt(K_TOUCH, TOUCH_DEFAULT), TOUCH_MIN, TOUCH_MAX);
    }

    public static void setTouchPauseCount(SharedPreferences p, int v) {
        p.edit().putInt(K_TOUCH, clamp(v, TOUCH_MIN, TOUCH_MAX)).apply();
    }

    public static int wrongCount(SharedPreferences p) {''')
sub('AdvancedSettings.java', '.remove(K_RETRY).remove(K_GATE).apply();', '.remove(K_RETRY).remove(K_GATE).remove(K_TOUCH).apply();')

# ---------------- Screens: slider for it ----------------
sub('Screens.java', '''            card.addView(s.sliderRow(c, "안 눌린 터치 재시도 횟수", "회",''', '''            card.addView(s.sliderRow(c, "직접 터치로 일시정지되는 횟수", "회", AdvancedSettings.TOUCH_MIN, AdvancedSettings.TOUCH_MAX, AdvancedSettings.TOUCH_STEP, AdvancedSettings.touchPauseCount(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setTouchPauseCount(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "안 눌린 터치 재시도 횟수", "회",''')

# ---------------- BotEngine ----------------
sub('BotEngine.java', '    private boolean human;\n', '''    private boolean human;
    private final PauseState pauseState = new PauseState();
    private boolean intervened;
    private boolean guessNext;
    private final java.util.Map<String, String> guessPick = new java.util.HashMap<>();

    public final boolean isPaused() {
        return this.active && this.pauseState.isPaused();
    }

    public final int getUserTouches() {
        return this.pauseState.getTouches();
    }

    public final int getTouchLimit() {
        return AdvancedSettings.touchPauseCount(this.storage.getPrefs());
    }

    public final boolean isInTest() {
        return this.active && "test".equals(this.phase);
    }

    /** Stops the bot acting until {@link #resume()}; the screen is left exactly as it is. */
    public final void pause(String reason) {
        if (!this.active || !this.pauseState.pause(SystemClock.elapsedRealtime())) {
            return;
        }
        QuietJson put = new QuietJson().put("reason", reason).put("phase", this.phase).put("touches", this.pauseState.getTouches());
        log("paused", put);
        this.service.setStatus("일시정지됨 · " + reason);
    }

    /**
     * Continues after a pause: the run clocks are moved forward by the pause, in-flight state is dropped, the touch
     * count restarts at 0, and the first answer afterwards is a guess if the question cannot be resolved (the user
     * may have moved on or answered by hand while paused).
     */
    public final void resume() {
        long pausedFor = this.pauseState.resume(SystemClock.elapsedRealtime());
        if (pausedFor < 0 || !this.active) {
            return;
        }
        this.started += pausedFor;
        this.entered += pausedFor;
        if (this.testStarted > 0) {
            this.testStarted += pausedFor;
        }
        this.humanDue.clear();
        this.pending = null;
        this.collectionReadPending = false;
        this.gate = new SwipeGate(AdvancedSettings.gateMs(this.storage.getPrefs()), 2);
        this.intervened = true;
        this.guessNext = true;
        QuietJson put = new QuietJson().put("paused_ms", pausedFor).put("phase", this.phase);
        log("resumed", put);
        this.service.setStatus("재개했어요");
    }

    /** A touch by the user during the test; pauses the run when the configured number is reached. */
    public final void registerUserTouch() {
        if (!isInTest()) {
            return;
        }
        int limit = getTouchLimit();
        boolean reached = this.pauseState.addTouch(limit);
        QuietJson put = new QuietJson().put("count", this.pauseState.getTouches()).put("limit", limit);
        log("user_touch", put);
        if (reached) {
            pause("직접 터치 " + this.pauseState.getTouches() + "회");
        } else {
            this.service.refreshOverlay();
        }
    }
''')
sub('BotEngine.java', '''        long left = (this.started + runLimitMs()) - SystemClock.elapsedRealtime();
        return Math.max(0L, left / 1000L);''', '''        return this.pauseState.remainingSeconds(this.started, runLimitMs(), SystemClock.elapsedRealtime());''')
sub('BotEngine.java', '''        if (this.active) {
            if (now - this.started >= runLimitMs()) {''', '''        if (this.paused()) {
            return;
        }
        if (this.active) {
            if (now - this.started >= runLimitMs()) {''')
sub('BotEngine.java', '    public final boolean isPaused() {', '''    private final boolean paused() {
        return this.pauseState.isPaused();
    }

    public final boolean isPaused() {''')
# a new run starts clean
sub('BotEngine.java', '''        this.wrongSet = null;
        this.wrongPick.clear();''', '''        this.wrongSet = null;
        this.wrongPick.clear();
        this.pauseState.reset();
        this.intervened = false;
        this.guessNext = false;
        this.guessPick.clear();''')
# guess once after a resume
sub('BotEngine.java', '''        ChoiceAnswer correct = solver.answer(prompt, choices);
        int wanted''', '''        String guessed = this.guessPick.get(progress);
        if (guessed != null) {
            for (UiNode node : choices) {
                if (Intrinsics.areEqual(node.getText(), guessed)) {
                    return new ChoiceAnswer(node, new ArrayList<Card>(), 0);
                }
            }
        }
        ChoiceAnswer correct;
        try {
            correct = solver.answer(prompt, choices);
        } catch (AnswerResolutionException e) {
            if (this.guessNext && !choices.isEmpty()) {
                this.guessNext = false;
                UiNode pick = choices.get(this.rng.nextInt(choices.size()));
                this.guessPick.put(progress, pick.getText());
                QuietJson put = new QuietJson().put("progress", progress).put("reason", e.getFailure().name());
                log("resume_guess", put);
                return new ChoiceAnswer(pick, new ArrayList<Card>(), 0);
            }
            throw e;
        }
        this.guessNext = false;
        int wanted''')
# questions the user answered by hand while paused are adopted instead of stalling the run
sub('BotEngine.java', '''                        if (!z || !this.answered.contains(str)) {
                            this.gate.resetObservation();''', '''                        if (z && this.intervened && !this.answered.contains(str)) {
                            this.answered.add(str);
                            QuietJson adopted = new QuietJson().put("progress", str);
                            log("adopted_user_answer", adopted);
                        }
                        if (!z || !this.answered.contains(str)) {
                            this.gate.resetObservation();''')
sub('BotEngine.java', 'if (this.expectedQuestions <= 0 || this.answered.size() != this.expectedQuestions) {', 'if (this.expectedQuestions <= 0 || (!this.intervened && this.answered.size() != this.expectedQuestions)) {')
sub('BotEngine.java', '.put("deliberate_wrong", this.wrongPick.size());', '.put("deliberate_wrong", this.wrongPick.size()).put("pauses", this.pauseState.getPauseCount()).put("intervened", this.intervened);')

# ---------------- BotService ----------------
s = read('BotService.java')
s = s.replace('    private boolean dragging;\n', '''    private boolean dragging;
    private View touchWatcher;
    private long lastGestureEnd;
    private long panelTouchTime = -1;
''', 1)
# our own gestures are not user touches
a = '            botService.busy = false;\n            botService.hold(j2);'
assert a in s
s = s.replace(a, '            botService.lastGestureEnd = SystemClock.uptimeMillis();\n' + a, 1)
# pause: leave the screen alone
a = '''                        j2 = BotService.this.armUntil;
                        if (elapsedRealtime < j2) {'''
assert a in s
s = s.replace(a, '''                        j2 = BotService.this.armUntil;
                        if (BotService.this.getEngine().isPaused()) {
                            // paused: the user is in control, do not touch the screen
                        } else if (elapsedRealtime < j2) {''', 1)
# panel host
a = '''            @Override
            public boolean isBlocked() {
                return BotService.this.overlayInputGuard.getBlocked() || BotService.this.busy;
            }
        });'''
assert a in s
s = s.replace(a, '''            @Override
            public boolean isBlocked() {
                return BotService.this.overlayInputGuard.getBlocked() || BotService.this.busy;
            }

            @Override
            public void onPause() {
                BotEngine engine = BotService.this.getEngine();
                if (engine.isPaused()) {
                    engine.resume();
                } else {
                    engine.pause("직접 일시정지");
                }
                BotService.this.refreshOverlay();
            }

            @Override
            public void onPanelTouch(long eventTime) {
                BotService.this.panelTouchTime = eventTime;
            }
        });''', 1)
# render the run state in the panel
a = '''        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.render(active, this.status, !active || collecting, beforeTest);
        }'''
assert a in s
s = s.replace(a, '''        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.render(active, this.status, !active || collecting, beforeTest);
            panel.renderRun(getEngine().isPaused(), getEngine().getUserTouches(), getEngine().getTouchLimit(), getEngine().isInTest());
        }''', 1)
# the touch watcher window
a = '    private final void dragOverlay(float dx, float dy, boolean finished) {'
s = s.replace(a, '''    /**
     * A 1x1 invisible window that is told (ACTION_OUTSIDE) whenever the screen is touched anywhere else. Only the fact
     * of a touch is used - not its position or what was touched. Touches made by the bot's own gestures and touches
     * on the floating panel are not counted.
     */
    private final void createTouchWatcher() {
        FrameLayout watcher = new FrameLayout(this);
        watcher.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    BotService.this.onOutsideTouch(event.getEventTime());
                }
                return false;
            }
        });
        // not focusable | not touch modal | watch outside touch
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(1, 1, 2032, 8 | 32 | 262144, -3);
        params.gravity = 8388659;
        Object systemService = getSystemService("window");
        ((WindowManager) systemService).addView(watcher, params);
        this.touchWatcher = watcher;
    }

    private final void onOutsideTouch(final long eventTime) {
        if (this.engine == null) {
            return;
        }
        BotEngine e = getEngine();
        if (!e.isInTest() || e.isPaused()) {
            return;
        }
        if (this.busy || SystemClock.uptimeMillis() - this.lastGestureEnd < 150) {
            return;
        }
        // the panel receives the same event as a normal touch; give it a moment to claim it
        this.handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (BotService.this.panelTouchTime == eventTime || BotService.this.busy || BotService.this.destroyed) {
                    return;
                }
                BotEngine engine = BotService.this.getEngine();
                if (engine.isInTest() && !engine.isPaused()) {
                    engine.registerUserTouch();
                }
            }
        }, 40L);
    }

    private final void dragOverlay(float dx, float dy, boolean finished) {''', 1)
s = s.replace('        createOverlay();\n        this.handler.post(this.ticker);', '        createOverlay();\n        createTouchWatcher();\n        this.handler.post(this.ticker);', 1)
a = '''        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            Object systemService = getSystemService("window");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
            ((WindowManager) systemService).removeView(linearLayout);
        }'''
assert a in s
s = s.replace(a, a + '''
        View watcher = this.touchWatcher;
        if (watcher != null) {
            ((WindowManager) getSystemService("window")).removeView(watcher);
            this.touchWatcher = null;
        }''', 1)
if 'import android.view.MotionEvent;' not in s:
    s = s.replace('import android.view.View;', 'import android.view.MotionEvent;\nimport android.view.View;', 1)
if 'import android.widget.FrameLayout;' not in s:
    s = s.replace('import android.widget.LinearLayout;', 'import android.widget.FrameLayout;\nimport android.widget.LinearLayout;', 1)
write('BotService.java', s)

# ---------------- FloatingPanel ----------------
s = read('FloatingPanel.java')
s = s.replace('''        boolean isBlocked();
    }''', '''        boolean isBlocked();

        /** Pause button: pauses a running bot, or resumes a paused one. */
        void onPause();

        /** A finger went down on the panel itself (event time in uptime ms), so it is not a touch on the app below. */
        void onPanelTouch(long eventTime);
    }''', 1)
s = s.replace('    private boolean running;\n', '''    private boolean running;
    private boolean pausedNow;
    private final LinearLayout pauseChip;
    private final TextView pauseLabel;
    private final ImageView pauseIcon;
    private final TextView touchText;
    private final Host hostRef;
''', 1)
s = s.replace('        super(context);\n        final AppStyle s = AppStyle.INSTANCE;', '        super(context);\n        this.hostRef = host;\n        final AppStyle s = AppStyle.INSTANCE;', 1)
# touch label under the header, then the tools row with the pause chip first
s = s.replace('''        addView(header, new LayoutParams(-2, -2));

        // tools:''', '''        addView(header, new LayoutParams(-2, -2));

        touchText = s.label(context, "", 11.5f, ON_PANEL_DIM, true);
        touchText.setVisibility(GONE);
        LayoutParams touchParams = new LayoutParams(-2, -2);
        touchParams.leftMargin = s.dp(context, 6);
        touchParams.topMargin = s.dp(context, 2);
        addView(touchText, touchParams);

        // tools:''', 1)
s = s.replace('''        tools.addView(settingsChip, new LayoutParams(-2, s.dp(context, 34)));''', '''        pauseLabel = new TextView(context);
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
        tools.addView(settingsChip, sp0);''', 1)
# panel touches are reported so they are not mistaken for touches on the app
s = s.replace('''    private LinearLayout chip(Context context, int iconRes, String label, TextView labelView) {''', '''    @Override
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
        AppStyle s = AppStyle.INSTANCE;
        pausedNow = paused && running;
        pauseChip.setVisibility(running ? VISIBLE : GONE);
        pauseLabel.setText(pausedNow ? "재개" : "일시정지");
        pauseIcon.setImageResource(pausedNow ? R.drawable.ic_play : R.drawable.ic_pause);
        pauseChip.setContentDescription(pausedNow ? "재개" : "일시정지");
        if (running) {
            tools.setVisibility(VISIBLE);
        }
        if (running && (inTest || pausedNow)) {
            touchText.setVisibility(VISIBLE);
            if (pausedNow) {
                touchText.setText("일시정지됨 · 직접 터치 " + touches + "회");
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
    }

    private LinearLayout chip(Context context, int iconRes, String label, TextView labelView) {''', 1)
# caption knows about the pause
s = s.replace('''        caption.setText("실행 중 · 남은 " + AppStyle.formatClock(remainingSeconds));''', '''        caption.setText((pausedNow ? "일시정지 · 남은 " : "실행 중 · 남은 ") + AppStyle.formatClock(remainingSeconds));''', 1)
s = s.replace('''        if (remainingSeconds < 0) {
            caption.setText("실행 중");''', '''        if (remainingSeconds < 0) {
            caption.setText(pausedNow ? "일시정지" : "실행 중");''', 1)
if 'import android.view.MotionEvent;' not in s:
    s = s.replace('import android.view.View;', 'import android.view.MotionEvent;\nimport android.view.View;', 1)
write('FloatingPanel.java', s)
print('ok')
