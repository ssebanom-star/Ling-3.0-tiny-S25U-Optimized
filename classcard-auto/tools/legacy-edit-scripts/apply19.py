exec(open('/tmp/claude-0/work/fix.py').read())
def read(p): return open(D+p).read()
def write(p,s): open(D+p,'w').write(s)
def sub(p,a,b):
    s=read(p); assert a in s,(p,a[:90]); write(p,s.replace(a,b,1))

# ---- BotEngine
sub('BotEngine.java','        this.pauseState.reset();','        this.pauseState.reset();\n        this.blockedTouches = 0;')
sub('BotEngine.java','    public final boolean isInTest() {','''    private int blockedTouches;

    /** True while the whole screen except the floating panel swallows touches: a run is going, not paused, setting on. */
    public final boolean isTouchLocked() {
        return this.active && !this.pauseState.isPaused() && this.storage.getPrefs().getBoolean("touch_lock", true);
    }

    public final int getBlockedTouches() {
        return this.blockedTouches;
    }

    /** A finger landed on the blocking layer (swallowed, not passed to ClassCard). */
    public final void registerBlockedTouch() {
        if (!isTouchLocked()) {
            return;
        }
        this.blockedTouches++;
        if (this.blockedTouches <= 50) {
            log("blocked_touch", new QuietJson().put("count", this.blockedTouches).put("phase", this.phase));
        }
        this.service.refreshOverlay();
    }

    public final boolean isInTest() {''')

# ---- BotService
sub('BotService.java','    private View touchWatcher;','    private View touchWatcher;\n    private FrameLayout touchBlocker;\n    private WindowManager.LayoutParams blockerParams;')
sub('BotService.java','    private final void onOutsideTouch(final long eventTime) {\n        if (this.engine == null) {\n            return;\n        }\n        BotEngine e = getEngine();','''    /**
     * A transparent full-screen window above ClassCard and below the floating panel (windows stack in the order they
     * were added). While locked it swallows every touch, so only the panel can be operated. System gestures, the
     * status bar and hardware buttons are outside what an app window can block.
     */
    private final void createTouchBlocker() {
        FrameLayout blocker = new FrameLayout(this);
        blocker.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && BotService.this.engine != null) {
                    BotService.this.getEngine().registerBlockedTouch();
                }
                return true;
            }
        });
        // not focusable | layout in screen | no limits
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, 2032, 8 | 256 | 512, -3);
        params.gravity = 8388659;
        blocker.setVisibility(View.GONE);
        ((WindowManager) getSystemService("window")).addView(blocker, params);
        this.touchBlocker = blocker;
        this.blockerParams = params;
    }

    private final void updateTouchLock() {
        FrameLayout blocker = this.touchBlocker;
        if (blocker == null || this.engine == null) {
            return;
        }
        boolean lock = getEngine().isTouchLocked();
        int want = lock ? View.VISIBLE : View.GONE;
        if (blocker.getVisibility() != want) {
            blocker.setVisibility(want);
        }
    }

    private final void onOutsideTouch(final long eventTime) {
        if (this.engine == null) {
            return;
        }
        BotEngine e = getEngine();
        if (e.isTouchLocked()) {
            // the blocking layer already swallows (and counts) these
            return;
        }''')
sub('BotService.java','        FloatingPanel panel = this.floating;\n        if (panel != null) {\n            panel.render(active, this.status, !active || collecting, beforeTest);\n            panel.renderRun(getEngine().isPaused(), getEngine().getUserTouches(), getEngine().getTouchLimit(), getEngine().isInTest());',
'        updateTouchLock();\n        FloatingPanel panel = this.floating;\n        if (panel != null) {\n            panel.render(active, this.status, !active || collecting, beforeTest);\n            panel.renderRun(getEngine().isPaused(), getEngine().getUserTouches(), getEngine().getTouchLimit(), getEngine().isInTest(), getEngine().isTouchLocked(), getEngine().getBlockedTouches());')
# bot gestures must pass through the blocker
sub('BotService.java','''        int i = layoutParams.flags;
        layoutParams.flags = touchable ? i & (-17) : i | 16;
        if (!touchable) {''','''        FrameLayout blockerView = this.touchBlocker;
        WindowManager.LayoutParams blockerLp = this.blockerParams;
        if (blockerView != null && blockerLp != null) {
            int bf = blockerLp.flags;
            blockerLp.flags = touchable ? bf & (-17) : bf | 16;
            try {
                ((WindowManager) getSystemService("window")).updateViewLayout(blockerView, blockerLp);
            } catch (Exception ignored) {
                blockerLp.flags = bf;
            }
        }
        int i = layoutParams.flags;
        layoutParams.flags = touchable ? i & (-17) : i | 16;
        if (!touchable) {''')
sub('BotService.java','        View watcher = this.touchWatcher;\n        if (watcher != null) {','''        FrameLayout blockerView = this.touchBlocker;
        if (blockerView != null) {
            try {
                ((WindowManager) getSystemService("window")).removeView(blockerView);
            } catch (Exception ignored) {
            }
            this.touchBlocker = null;
        }
        View watcher = this.touchWatcher;
        if (watcher != null) {''')
sub('BotService.java','        createOverlay();\n        createTouchWatcher();','        createTouchBlocker();\n        createOverlay();\n        createTouchWatcher();')

# ---- FloatingPanel
s=read('FloatingPanel.java')
s=s.replace('public void renderRun(boolean paused, int touches, int limit, boolean inTest) {','''public void renderRun(boolean paused, int touches, int limit, boolean inTest) {
        renderRun(paused, touches, limit, inTest, false, 0);
    }

    public void renderRun(boolean paused, int touches, int limit, boolean inTest, boolean locked, int blocked) {''',1)
s=s.replace('''        if (running && (inTest || pausedNow)) {
            touchText.setVisibility(VISIBLE);
            if (pausedNow) {''','''        if (running && locked && !pausedNow) {
            touchText.setVisibility(VISIBLE);
            touchText.setText("화면 잠금 중 · 막은 터치 " + blocked + "회");
            touchText.setTextColor(blocked > 0 ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
        } else if (running && (inTest || pausedNow)) {
            touchText.setVisibility(VISIBLE);
            if (pausedNow) {''',1)
s=s.replace('touchText.setText("일시정지됨 · 직접 터치 " + touches + "회");','touchText.setText("일시정지됨 · 잠금 해제됨 · 직접 터치 " + touches + "회");',1)
write('FloatingPanel.java',s)

# ---- Settings
sub('Screens.java','        LinearLayout humanCard = s.card(c);','''        LinearLayout lockCard = s.card(c);
        lockCard.addView(s.switchRow(c, "실행 중 화면 터치 잠금", "실행 중에는 플로팅 패널 외의 화면 터치를 막습니다. 일시정지하면 풀립니다. 상태바 내리기·홈/뒤로 제스처·전원/볼륨 버튼은 앱이 막을 수 없습니다. 다음 실행부터 적용됩니다.", p.getBoolean("touch_lock", true), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                p.edit().putBoolean("touch_lock", checked).apply();
            }
        }));
        add(content, lockCard, 8);
        LinearLayout humanCard = s.card(c);''')
sub('MainActivity.java','시험 중에는 사용자가 화면을 직접 터치한 횟수','실행 중 화면 터치 잠금을 켜면 눈에 보이지 않는 투명한 막이 화면을 덮어 플로팅 패널 외의 터치를 받아 버리며, 막은 횟수만 세고 위치·내용은 기록하지 않습니다. 시험 중에는 사용자가 화면을 직접 터치한 횟수')
sub('../../../../../../build.gradle' if False else 'MainActivity.java','"개인정보처리방침",','"개인정보처리방침",') if False else None
