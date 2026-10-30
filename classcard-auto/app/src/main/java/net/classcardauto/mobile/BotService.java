package net.classcardauto.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlin.uuid.Uuid;
import net.classcardauto.mobile.OcrReader;
import org.json.JSONObject;

/* compiled from: BotService.kt */
/* loaded from: classes.dex */
public final class BotService extends AccessibilityService {

    /* renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static BotService instance;
    private long armUntil;
    private boolean busy;
    private boolean currentStartAllowed;
    private boolean destroyed;
    public BotEngine engine;
    private long holdUntil;
    private long idleCheckedAt;
    private long lastActive;
    private LinearLayout overlay;
    private Button overlayButton;
    private FloatingPanel floating;
    private boolean overlayDismissed;
    private boolean panelOff;

    /** Brings back a panel switched off with "끄기" (called when the app screen is opened). */
    public final void showPanel() {
        if (this.panelOff) {
            this.panelOff = false;
            refreshOverlay();
        }
    }
    private boolean dragging;
    private View touchWatcher;
    private FrameLayout touchBlocker;
    long lockConfirmUntil;
    private boolean panelHiddenForShot;

    private final boolean panelCovers(List<UiNode> targets) {
        LinearLayout view = this.overlay;
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        int l = loc[0], t = loc[1], r = l + view.getWidth(), b = t + view.getHeight();
        for (UiNode node : targets) {
            Bounds bd = node.getBounds();
            if (bd.getLeft() < r && bd.getRight() > l && bd.getTop() < b && bd.getBottom() > t) {
                return true;
            }
        }
        return false;
    }
    private WindowManager.LayoutParams blockerParams;
    private long lastGestureEnd;
    private long panelTouchTime = -1;
    private int dragStartX;
    private int dragStartY;
    private WindowManager.LayoutParams overlayParams;
    private TextView overlayText;
    public UiProfile profile;
    private boolean readingImage;
    public Storage storage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String status = "연결됨 · 대기";
    private final OverlayInputGuard overlayInputGuard = new OverlayInputGuard();

    /* renamed from: ocrReader$delegate, reason: from kotlin metadata */
    private final Lazy ocrReader = LazyKt.lazy(new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda6
        @Override // kotlin.jvm.functions.Function0
        public final Object invoke() {
            OcrReader ocrReader_delegate$lambda$0;
            ocrReader_delegate$lambda$0 = BotService.ocrReader_delegate$lambda$0(BotService.this);
            return ocrReader_delegate$lambda$0;
        }
    });
    private final Runnable ticker = new Runnable() { // from class: net.classcardauto.mobile.BotService$ticker$1
        @Override // java.lang.Runnable
        public void run() {
            long j;
            long j2;
            boolean z;
            long j3;
            long j4;
            long j5;
            try {
                if (!UsageConsent.INSTANCE.getAllowed()) {
                    if (BotService.this.getEngine().getActive()) {
                        BotService.this.stop(UsageConsent.INSTANCE.getRequiredMessage());
                    }
                    BotService.this.currentStartAllowed = false;
                    BotService.this.refreshOverlay();
                } else if (BotService.this.getEngine().getActive()) {
                    FloatingPanel clockPanel = BotService.this.floating;
                    if (clockPanel != null) {
                        clockPanel.setRemaining(BotService.this.getEngine().getRemainingSeconds());
                        clockPanel.setEta(BotService.this.getEngine().getEtaSeconds());
                    }
                    long elapsedRealtime = SystemClock.elapsedRealtime();
                    Object systemService = BotService.this.getSystemService("power");
                    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.PowerManager");
                    Object systemService2 = BotService.this.getSystemService("keyguard");
                    Intrinsics.checkNotNull(systemService2, "null cannot be cast to non-null type android.app.KeyguardManager");
                    if (!((PowerManager) systemService).isInteractive() || ((KeyguardManager) systemService2).isKeyguardLocked()) {
                        // wait instead of giving up; the run continues once the screen is back
                        BotService.this.getEngine().autoHold("화면이 잠겼어요 · 켜면 이어서 진행해요");
                    } else {
                        j2 = BotService.this.armUntil;
                        if (BotService.this.getEngine().isPaused()) {
                            // paused: the user is in control, do not touch the screen; a self-hold ends when ClassCard is back
                            if (BotService.this.getEngine().isAutoPaused()) {
                                BotService.this.autoResumeCheck(elapsedRealtime);
                            }
                        } else if (elapsedRealtime < j2) {
                            BotService botService = BotService.this;
                            j5 = botService.armUntil;
                            botService.setStatus((((j5 - elapsedRealtime) + 999) / 1000) + "초 후 시작 · 중지 가능");
                        } else if (!BotService.this.getBusy()) {
                            z = BotService.this.readingImage;
                            if (!z) {
                                j3 = BotService.this.holdUntil;
                                if (elapsedRealtime >= j3) {
                                    List<UiNode> readScreen = BotService.this.readScreen();
                                    if (readScreen.isEmpty()) {
                                        j4 = BotService.this.lastActive;
                                        if (elapsedRealtime - j4 > 3000) {
                                            BotService.this.getEngine().autoHold("클래스카드 화면이 보이지 않아요 · 돌아오면 이어서 진행해요");
                                        }
                                    } else {
                                        BotService.this.lastActive = elapsedRealtime;
                                        BotService.this.getEngine().tick(readScreen, elapsedRealtime);
                                    }
                                }
                            }
                        }
                    }
                } else if (BotService.this.getEngine().isCalibrating()) {
                    // measuring the user's own pace: only look at the screen, never touch it
                    BotService.this.getEngine().observeCalibration(BotService.this.readScreen(), SystemClock.elapsedRealtime());
                    FloatingPanel measuring = BotService.this.floating;
                    if (measuring != null && BotService.this.getEngine().isCalibrating()) {
                        measuring.setCalibrating(true, BotService.this.calibrationText());
                    }
                } else {
                    long elapsedRealtime2 = SystemClock.elapsedRealtime();
                    j = BotService.this.idleCheckedAt;
                    if (elapsedRealtime2 - j >= 700) {
                        BotService.this.idleCheckedAt = SystemClock.elapsedRealtime();
                        BotService.this.currentStartAllowed = SetsKt.setOf((Object[]) new CurrentEntry[]{CurrentEntry.SET, CurrentEntry.PREVIEW}).contains(CoreKt.currentEntry(BotService.this.readScreen()));
                        BotService.this.refreshOverlay();
                    }
                }
                if (BotService.this.getEngine().isAutoPaused()) {
                    long heldMs = BotService.this.getEngine().autoPausedFor(SystemClock.elapsedRealtime());
                    if (heldMs > AdvancedSettings.holdSeconds(BotService.this.getStorage().getPrefs()) * 1000L) {
                        BotService.this.stop("자동 대기 시간이 지나 중지했습니다.");
                    }
                }
            } catch (Throwable e) {
                try {
                    BotEngine engine = BotService.this.getEngine();
                    String message = e.getMessage();
                    if (message == null) {
                        message = e.getClass().getSimpleName();
                    }
                    AnswerResolutionException answerResolutionException = e instanceof AnswerResolutionException ? (AnswerResolutionException) e : null;
                    engine.fail(message, answerResolutionException != null ? answerResolutionException.getFailure() : null);
                } catch (Throwable second) {
                    // the failure path itself broke (e.g. while redrawing the panel): stop quietly and keep the service alive
                    BotService.this.recordCrash(second);
                    BotService.this.getEngine().forceStop();
                }
            } finally {
                BotService.this.getHandler().removeCallbacks(this);
                // 초고속 모드는 화면을 더 자주 읽어 바뀐 화면에 바로 반응한다
                BotService.this.getHandler().postDelayed(this, BotService.this.engine != null && BotService.this.getEngine().isTurbo() ? 35L : 100L);
            }
        }
    };

    /* compiled from: BotService.kt */
    @Metadata(d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003R\"\u0010\u0006\u001a\u0004\u0018\u00010\u00052\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005@BX\u0086\u000e¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\b¨\u0006\t"}, d2 = {"Lnet/classcardauto/mobile/BotService$Companion;", "", "<init>", "()V", "value", "Lnet/classcardauto/mobile/BotService;", "instance", "getInstance", "()Lnet/classcardauto/mobile/BotService;", "app_release"}, k = 1, mv = {2, 1, 0}, xi = 48)
    /* loaded from: classes.dex */
    public static final class Companion {
        private Companion() {
        }

        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        public final BotService getInstance() {
            return BotService.instance;
        }
    }

    private final void createOverlay() {
        final float f = getResources().getDisplayMetrics().density;
        FloatingPanel panel = new FloatingPanel(this, getStorage().getPrefs(), new FloatingPanel.Host() {
            @Override
            public void onPrimary() {
                BotService.createOverlay$lambda$32$lambda$31(BotService.this, null);
            }

            @Override
            public void onHide() {
                if (BotService.this.getEngine().getBeforeTest()) {
                    BotService.this.overlayDismissed = true;
                    BotService.this.refreshOverlay();
                }
            }

            @Override
            public void onDrag(float dx, float dy, boolean finished) {
                BotService.this.dragOverlay(dx, dy, finished);
            }

            @Override
            public boolean isBlocked() {
                return !BotService.this.getEngine().getActive() && (BotService.this.overlayInputGuard.getBlocked() || BotService.this.busy);
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
            public boolean isDockedRight() {
                LinearLayout view = BotService.this.overlay;
                WindowManager.LayoutParams lp = BotService.this.overlayParams;
                if (view == null || lp == null) {
                    return true;
                }
                int maxX = Math.max(0, BotService.this.getResources().getDisplayMetrics().widthPixels - view.getWidth());
                // gravity is top|end, so x counts from the right edge
                return lp.x <= maxX / 2;
            }

            @Override
            public void onLayoutChanged() {
                BotService.this.snapPanelToEdge();
            }

            @Override
            public void onTurnOff() {
                if (BotService.this.getEngine().getActive()) {
                    return;
                }
                BotService.this.panelOff = true;
                BotService.this.refreshOverlay();
                android.widget.Toast.makeText(BotService.this, "MiMiC 앱을 열면 패널이 다시 나타나요", android.widget.Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPanelTouch(long eventTime) {
                BotService.this.panelTouchTime = eventTime;
            }
        });
        panel.setVisibility(8);
        this.overlayText = panel.getStatusText();
        this.overlayButton = panel.getPrimary();
        this.floating = panel;
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(-2, -2, 2032, 40, -3);
        layoutParams.gravity = 8388661;
        layoutParams.x = createOverlay$dp(f, 8);
        layoutParams.y = createOverlay$dp(f, 40);
        Object systemService = getSystemService("window");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
        ((WindowManager) systemService).addView(panel, layoutParams);
        this.overlay = panel;
        this.overlayParams = layoutParams;
    }

    /**
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

    /**
     * A transparent full-screen window above ClassCard and below the floating panel (windows stack in the order they
     * were added). While locked it swallows every touch, so only the panel can be operated. System gestures, the
     * status bar and hardware buttons are outside what an app window can block.
     */
    private SwipeGuide swipeGuide;
    /** The list the last collection swipe moved; the preview draws on it. */
    private Bounds lastCollectionBounds;

    /** Full-screen, untouchable drawing of the collection swipe (see SwipeGuide). */
    private final void createSwipeGuide() {
        SwipeGuide guide = new SwipeGuide(this);
        // not focusable | not touchable | layout in screen | no limits
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1, 2032, 8 | 16 | 256 | 512, -3);
        params.gravity = 8388659;
        ((WindowManager) getSystemService("window")).addView(guide, params);
        this.swipeGuide = guide;
    }

    /** Draws the collection swipe on the real screen without swiping (from the panel's 수집 tab). */
    public final void previewCollectionSwipe() {
        SwipeGuide guide = this.swipeGuide;
        if (guide == null || this.storage == null) {
            return;
        }
        android.content.SharedPreferences prefs = getStorage().getPrefs();
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        Bounds b = this.lastCollectionBounds;
        float x;
        float top;
        float height;
        boolean rough = b == null;
        if (b == null) {
            // no swipe yet: assume the list fills most of the screen under the title bar
            x = m.widthPixels / 2f;
            top = m.heightPixels * 0.18f;
            height = m.heightPixels * 0.72f;
        } else {
            x = b.getX();
            top = b.getTop();
            height = b.getHeight();
        }
        int range = ScrollSettings.rangePercent(prefs);
        float[] span = ScrollSettings.fractions(range, true);
        guide.show(x, top + height * span[0], top + height * span[1], ScrollSettings.durationMs(prefs), 1400L, range + "% · 미리 보기" + (rough ? " (대략)" : ""));
    }

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
        }
        if ((!e.isInTest() && !e.getCollecting()) || e.isPaused()) {
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
                if (engine.isPaused()) {
                    return;
                }
                if (engine.isInTest()) {
                    engine.registerUserTouch();
                } else if (engine.getCollecting()) {
                    engine.registerCollectionTouch();
                }
            }
        }, 40L);
    }

    private final void dragOverlay(float dx, float dy, boolean finished) {
        WindowManager.LayoutParams layoutParams = this.overlayParams;
        LinearLayout view = this.overlay;
        if (layoutParams == null || view == null) {
            return;
        }
        if (!this.dragging) {
            this.dragging = true;
            this.dragStartX = layoutParams.x;
            this.dragStartY = layoutParams.y;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int maxX = Math.max(0, metrics.widthPixels - view.getWidth());
        int maxY = Math.max(0, metrics.heightPixels - view.getHeight());
        // gravity is top|end, so x counts from the right edge: dragging right shrinks it
        layoutParams.x = Math.max(0, Math.min(maxX, (int) (this.dragStartX - dx)));
        layoutParams.y = Math.max(0, Math.min(maxY, (int) (this.dragStartY + dy)));
        Object systemService = getSystemService("window");
        ((WindowManager) systemService).updateViewLayout(view, layoutParams);
        if (finished) {
            this.dragging = false;
            if (this.engine != null && getEngine().getActive()) {
                snapPanelToEdge();
            }
        }
    }

    private boolean snappedForTest;
    private long visibleSince;

    /** Starts or ends the personal-pace measurement (the user answers a test by hand while the app only watches). */
    public final void toggleCalibration() {
        if (getEngine().getActive()) {
            return;
        }
        if (getEngine().isCalibrating()) {
            String message = getEngine().finishCalibration();
            onCalibrationDone(message != null ? message : "측정을 취소했어요 (3문항 이상 필요)");
            return;
        }
        getEngine().startCalibration();
        setStatus("측정 중 · 클래스카드에서 ‘테스트 시작’을 직접 눌러 " + BotEngine.CALIBRATION_TARGET + "문항을 풀어 주세요");
    }

    /** What the floating panel says while the personal pace is measured. */
    final String calibrationText() {
        int n = getEngine().getCalibrationCount();
        if (n <= 0) {
            return "세트 화면에서 ‘테스트 시작’을 직접 누르고 평소처럼 풀어 주세요. 앱은 화면을 보기만 해요.";
        }
        return n + " / " + BotEngine.CALIBRATION_TARGET + "문항 기록됨 · 3문항부터 반영할 수 있어요.";
    }

    public final void onCalibrationDone(String message) {
        if (message != null && message.startsWith("내 패턴")) {
            alert(2);
        }
        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.rebuildSettings();
        }
        setStatus(message != null ? message : "측정을 마쳤어요");
        android.widget.Toast.makeText(this, message != null ? message : "측정을 마쳤어요", android.widget.Toast.LENGTH_LONG).show();
    }
    private UiNode verifyTarget;
    private int verifyAborts;

    /** While self-held: once ClassCard has been on screen for a moment, carry on. */
    private final void autoResumeCheck(long now) {
        if (this.busy) {
            return;
        }
        List<UiNode> nodes = readScreen();
        if (nodes.isEmpty()) {
            this.visibleSince = 0;
            return;
        }
        if (this.visibleSince == 0) {
            this.visibleSince = now;
        }
        if (now - this.visibleSince >= 1200) {
            this.visibleSince = 0;
            getEngine().resumeAuto();
        }
    }

    /** True when the node that was about to be tapped is still on screen at (nearly) the same place. */
    private final boolean targetStillThere(UiNode wanted) {
        for (UiNode n : readScreen()) {
            if (java.util.Objects.equals(n.getId(), wanted.getId()) && n.getText().equals(wanted.getText())) {
                Bounds a = n.getBounds();
                Bounds b = wanted.getBounds();
                if (Math.abs(a.getX() - b.getX()) <= 24 && Math.abs(a.getY() - b.getY()) <= 24) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Short vibration patterns: 0 = waiting, 1 = stopped/failed, 2 = finished. */
    public final void alert(int kind) {
        if (this.storage == null || !getStorage().getPrefs().getBoolean("vibrate_alerts", true)) {
            return;
        }
        try {
            android.os.Vibrator vibrator = (android.os.Vibrator) getSystemService("vibrator");
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            long[] pattern = kind == 0 ? new long[]{0, 120, 120, 120} : (kind == 1 ? new long[]{0, 450} : new long[]{0, 80, 100, 80, 100, 220});
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (this.engine != null && getEngine().getActive()) {
            getEngine().resyncAfterChange();
            hold(1200L);
        }
        snapPanelToEdge();
    }
    private boolean panelPassThrough;

    /** True when a gesture from (x1,y1) to (x2,y2) would be delivered to the floating panel instead of the app below. */
    private final boolean pathHitsPanel(float x1, float y1, float x2, float y2) {
        LinearLayout view = this.overlay;
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float margin = 24.0f;
        float l = Math.min(x1, x2) - margin, r = Math.max(x1, x2) + margin;
        float t = Math.min(y1, y2) - margin, b = Math.max(y1, y2) + margin;
        return l < loc[0] + view.getWidth() && r > loc[0] && t < loc[1] + view.getHeight() && b > loc[1];
    }

    /** The narrow test strip sits against the nearest side edge so it stays out of the middle of the screen. */
    private final void snapPanelToEdge() {
        final LinearLayout view = this.overlay;
        final WindowManager.LayoutParams lp = this.overlayParams;
        if (view == null || lp == null) {
            return;
        }
        view.postDelayed(new Runnable() { // after the panel has re-laid out at its compact size
            @Override
            public void run() {
                android.util.DisplayMetrics metrics = BotService.this.getResources().getDisplayMetrics();
                int maxX = Math.max(0, metrics.widthPixels - view.getWidth());
                // gravity is top|end, so x counts from the right edge
                lp.x = lp.x <= maxX / 2 ? 0 : maxX;
                try {
                    ((WindowManager) BotService.this.getSystemService("window")).updateViewLayout(view, lp);
                } catch (Exception ignored) {
                }
            }
        }, 200L);
    }







    private static final int createOverlay$dp(float f, int i) {
        return (int) (i * f);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void createOverlay$lambda$32$lambda$31(BotService botService, View view) {
        boolean running = botService.getEngine().getActive();
        if (!running && (botService.overlayInputGuard.getBlocked() || botService.busy)) {
            return;
        }
        try {
            if (running) {
                botService.stop("사용자 중지");
            } else if (botService.getStorage().getPrefs().getBoolean("touch_lock", true) && SystemClock.elapsedRealtime() > botService.lockConfirmUntil) {
                // first press: explain the lock; a second press within 10 s starts
                botService.lockConfirmUntil = SystemClock.elapsedRealtime() + 10000;
                botService.setStatus("시작하면 화면 터치가 잠겨요 (일시정지로 해제). 시작하려면 10초 안에 한 번 더");
                // the status line is not visible while the panel is shrunk to its start button
                android.widget.Toast.makeText(botService, "화면 터치가 잠겨요. 시작하려면 10초 안에 한 번 더 누르세요", android.widget.Toast.LENGTH_LONG).show();
            } else {
                botService.lockConfirmUntil = 0;
                botService.arm("screen_auto");
            }
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "시작 실패";
            }
            botService.setStatus(message);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void finishGesture(final long token, final long cooldown, final Function0<Unit> after) {
        this.handler.postDelayed(new Runnable() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                BotService.finishGesture$lambda$25(BotService.this, token, cooldown, after);
            }
        }, 32L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void finishGesture$lambda$25(BotService botService, long j, long j2, Function0 function0) {
        if (botService.destroyed || !botService.overlayInputGuard.owns(j)) {
            return;
        }
        LinearLayout linearLayout = botService.overlay;
        if (linearLayout != null) {
            linearLayout.cancelPendingInputEvents();
        }
        Button button = botService.overlayButton;
        if (button != null) {
            button.setPressed(false);
        }
        try {
            botService.setOverlayTouchable(true);
            botService.overlayInputGuard.finish(j);
            botService.lastGestureEnd = SystemClock.uptimeMillis();
            botService.busy = false;
            botService.hold(j2);
            function0.invoke();
        } catch (Exception unused) {
            botService.overlayInputGuard.finish(j);
            botService.busy = false;
            if (botService.getEngine().getActive()) {
                BotEngine.fail$default(botService.getEngine(), "제어창 터치 복구 실패", null, 2, null);
            }
        }
    }

    private final void gesture(float x1, float y1, float x2, float y2, long duration, final Function0<Unit> after) {
        gesture(x1, y1, x2, y2, duration, 0L, after);
    }

    /** A pending "hold still at the end" for the gesture being dispatched (continued stroke, Android 8+). */
    private GestureDescription.StrokeDescription holdStroke;
    private float holdX;
    private float holdY;
    private long holdMs;

    /**
     * Same, but when {@code endHoldMs > 0} the finger stays still at the end point for that long before lifting, so the
     * list stops exactly where the swipe ended instead of flinging on.
     */
    private final void gesture(float x1, float y1, float x2, float y2, long duration, long endHoldMs, final Function0<Unit> after) {
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException(UsageConsent.INSTANCE.getRequiredMessage().toString());
        }
        if (this.busy) {
            throw new IllegalStateException("다른 제스처가 진행 중입니다.".toString());
        }
        final int epoch = getEngine().getEpoch();
        Path path = new Path();
        path.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) {
            path.lineTo(x2, y2);
        }
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0L, duration, endHoldMs > 0);
        final GestureDescription build = new GestureDescription.Builder().addStroke(stroke).build();
        this.holdStroke = endHoldMs > 0 ? stroke : null;
        this.holdX = x2;
        this.holdY = y2;
        this.holdMs = endHoldMs;
        final long begin = this.overlayInputGuard.begin();
        this.busy = true;
        try {
            this.panelPassThrough = pathHitsPanel(x1, y1, x2, y2);
            setOverlayTouchable(false);
            this.handler.postDelayed(new Runnable() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    BotService.gesture$lambda$24(BotService.this, begin, epoch, build, after);
                }
            }, (this.touchBlocker != null && this.touchBlocker.getVisibility() == View.VISIBLE) ? 90L : 32L); // the full-screen blocker needs a moment to become pass-through
        } catch (Exception e) {
            this.overlayInputGuard.finish(begin);
            this.busy = false;
            throw e;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void gesture$lambda$24(final BotService botService, long j, int i, GestureDescription gestureDescription, Function0 function0) {
        final int i2 = i;
        if (botService.destroyed || !botService.overlayInputGuard.owns(j)) {
            return;
        }
        if (!UsageConsent.INSTANCE.getAllowed() || !botService.getEngine().getActive() || botService.getEngine().getEpoch() != i) {
            botService.finishGesture(j, 45L, new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda2
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit unit;
                    unit = Unit.INSTANCE;
                    return unit;
                }
            });
            return;
        }
        UiNode verify = botService.verifyTarget;
        botService.verifyTarget = null;
        if (verify != null && !botService.targetStillThere(verify) && ++botService.verifyAborts < 3) {
            // the screen moved under us: skip this tap, the engine looks again on its next pass
            botService.finishGesture(j, 60L, new Function0() {
                @Override
                public Object invoke() {
                    return Unit.INSTANCE;
                }
            });
            return;
        }
        botService.verifyAborts = 0;
        try {
            final BotService$gesture$3$accepted$1 accepted = new BotService$gesture$3$accepted$1(botService, j, i2, function0);
            final GestureDescription.StrokeDescription moving = botService.holdStroke;
            botService.holdStroke = null;
            AccessibilityService.GestureResultCallback callback = accepted;
            if (moving != null) {
                final float hx = botService.holdX;
                final float hy = botService.holdY;
                final long hms = botService.holdMs;
                callback = new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription g) {
                        // the finger is still down at the end point: keep it there, then lift with no speed left
                        try {
                            Path still = new Path();
                            still.moveTo(hx, hy);
                            GestureDescription rest = new GestureDescription.Builder().addStroke(moving.continueStroke(still, 0L, hms, false)).build();
                            if (botService.dispatchGesture(rest, accepted, botService.handler)) {
                                return;
                            }
                        } catch (Exception ignored) {
                        }
                        accepted.onCancelled(g);
                    }

                    @Override
                    public void onCancelled(GestureDescription g) {
                        accepted.onCancelled(g);
                    }
                };
            }
            if (botService.dispatchGesture(gestureDescription, callback, botService.handler)) {
                return;
            }
            botService.finishGesture(j, 150L, new Function0() {
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    return BotService.gesture$lambda$24$lambda$22(botService, i2);
                }
            });
        } catch (Exception e) {
            final Exception exc = e;
            botService.finishGesture(j, 150L, new Function0() {
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    return BotService.gesture$lambda$24$lambda$23(botService, i2, exc);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit gesture$lambda$24$lambda$22(BotService botService, int i) {
        if (botService.getEngine().getActive() && botService.getEngine().getEpoch() == i) {
            BotEngine.fail$default(botService.getEngine(), "터치 제스처가 거부되었습니다.", null, 2, null);
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit gesture$lambda$24$lambda$23(BotService botService, int i, Exception exc) {
        if (botService.getEngine().getActive() && botService.getEngine().getEpoch() == i) {
            BotEngine engine = botService.getEngine();
            String message = exc.getMessage();
            if (message == null) {
                message = "터치 제스처 실패";
            }
            BotEngine.fail$default(engine, message, null, 2, null);
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final OcrReader getOcrReader() {
        return (OcrReader) this.ocrReader.getValue();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit ocrNode$lambda$27(Function1 function1, Object result) {
        Object out = result;
        if (ResultCompat.isSuccess(result)) {
            out = ResultCompat.success((OcrReader.Read) CollectionsKt.single((List) ResultCompat.valueOrThrow(result)));
        }
        function1.invoke(out);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void ocrNodes$deliver(BotService botService, Function1<Object, Unit> function1, Object obj) {
        botService.readingImage = false;
        if (botService.panelHiddenForShot) {
            botService.panelHiddenForShot = false;
            if (botService.overlay != null) {
                botService.overlay.setAlpha(1.0f);
            }
        }
        function1.invoke(obj);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void ocrNodes$lambda$28(BotService botService, Runnable runnable) {
        botService.handler.post(runnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final OcrReader ocrReader_delegate$lambda$0(BotService botService) {
        return new OcrReader(botService);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onDestroy$lambda$2(BotService botService) {
        botService.getOcrReader().close();
    }

    private static final void readScreen$walk(List<UiNode> list, BotService botService, AccessibilityNodeInfo accessibilityNodeInfo, int i, String str, Bounds bounds, Integer num) {
        String str2;
        Integer num2;
        List<UiNode> list2;
        Bounds bounds2;
        Integer num3;
        String obj;
        String obj2;
        if (i > 50 || list.size() > 2500) {
            return;
        }
        Rect rect = new Rect();
        accessibilityNodeInfo.getBoundsInScreen(rect);
        UiProfile profile = botService.getProfile();
        String viewIdResourceName = accessibilityNodeInfo.getViewIdResourceName();
        if (viewIdResourceName == null || (str2 = StringsKt.substringAfter(viewIdResourceName, ":id/", viewIdResourceName)) == null) {
            str2 = "";
        }
        String canonicalId = profile.canonicalId(str2);
        Bounds bounds3 = new Bounds(rect.left, rect.top, rect.right, rect.bottom);
        Bounds bounds4 = Intrinsics.areEqual(str, "recycler_view") ? bounds3 : bounds;
        if (Intrinsics.areEqual(str, "recycler_view")) {
            AccessibilityNodeInfo.CollectionItemInfo collectionItemInfo = accessibilityNodeInfo.getCollectionItemInfo();
            if (collectionItemInfo != null) {
                Integer valueOf = Integer.valueOf(collectionItemInfo.getRowIndex());
                if (valueOf.intValue() >= 0) {
                    num2 = valueOf;
                }
            }
            num2 = null;
        } else {
            num2 = num;
        }
        CharSequence packageName = accessibilityNodeInfo.getPackageName();
        if (Intrinsics.areEqual(packageName != null ? packageName.toString() : null, botService.getProfile().getPackageName()) && accessibilityNodeInfo.isVisibleToUser() && !rect.isEmpty()) {
            CharSequence text = accessibilityNodeInfo.getText();
            String str3 = (text == null || (obj2 = text.toString()) == null) ? "" : obj2;
            CharSequence contentDescription = accessibilityNodeInfo.getContentDescription();
            bounds2 = bounds4;
            num3 = num2;
            list2 = list;
            list2.add(new UiNode(canonicalId, str3, bounds3, (contentDescription == null || (obj = contentDescription.toString()) == null) ? "" : obj, accessibilityNodeInfo.isEnabled(), accessibilityNodeInfo.isClickable(), accessibilityNodeInfo.isScrollable(), accessibilityNodeInfo.isSelected(), bounds2, accessibilityNodeInfo.isChecked(), num3, null, 2048, null));
        } else {
            list2 = list;
            bounds2 = bounds4;
            num3 = num2;
        }
        int childCount = accessibilityNodeInfo.getChildCount();
        int i2 = 0;
        while (i2 < childCount) {
            AccessibilityNodeInfo child = accessibilityNodeInfo.getChild(i2);
            if (child != null) {
                String str4 = canonicalId;
                readScreen$walk(list2, botService, child, i + 1, str4, bounds2, num3);
                canonicalId = str4;
                child.recycle();
            }
            i2++;
            list2 = list;
        }
    }

    static /* synthetic */ void readScreen$walk$default(List list, BotService botService, AccessibilityNodeInfo accessibilityNodeInfo, int i, String str, Bounds bounds, Integer num, int i2, Object obj) {
        if ((i2 & 16) != 0) {
            str = "";
        }
        readScreen$walk(list, botService, accessibilityNodeInfo, i, str, (i2 & 32) != 0 ? null : bounds, (i2 & 64) != 0 ? null : num);
    }

    public final void refreshOverlay() {
        if (this.engine == null) {
            return;
        }
        boolean active = getEngine().getActive();
        boolean beforeTest = getEngine().getBeforeTest();
        boolean collecting = getEngine().getCollecting();
        if (!active || getEngine().isPaused()) {
            // 실행이 끝나면 꺼 두었던 UI를 다시 쓸 수 있게 되돌린다.
            // 일시정지 중에는 재개 버튼이 필요하므로 패널을 항상 보여 준다(앱 화면으로 돌아오지 않아도 되게).
            this.overlayDismissed = false;
        }
        updateTouchLock();
        boolean inTestNow = active;
        if (inTestNow && !this.snappedForTest) {
            this.snappedForTest = true;
            snapPanelToEdge();
        } else if (!inTestNow) {
            this.snappedForTest = false;
        }
        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.render(active, this.status, !active || collecting, beforeTest);
            panel.setCalibrating(getEngine().isCalibrating(), calibrationText());
            panel.renderRun(getEngine().isPaused(), getEngine().getUserTouches(), getEngine().getTouchLimit(), getEngine().isInTest(), getEngine().isTouchLocked(), getEngine().getBlockedTouches());
        }
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            linearLayout.setVisibility((UsageConsent.INSTANCE.getAllowed() && (active || getEngine().isCalibrating() || (this.currentStartAllowed && !this.panelOff)) && !this.overlayDismissed) ? 0 : 8);
        }
        WindowManager.LayoutParams layoutParams = this.overlayParams;
        if (layoutParams != null) {
            int i = active ? layoutParams.flags | Uuid.SIZE_BITS : layoutParams.flags & (-129);
            if (i != layoutParams.flags) {
                layoutParams.flags = i;
                LinearLayout linearLayout2 = this.overlay;
                if (linearLayout2 != null) {
                    Object systemService = getSystemService("window");
                    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
                    ((WindowManager) systemService).updateViewLayout(linearLayout2, layoutParams);
                }
            }
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void scroll$default(BotService botService, Bounds bounds, boolean z, Function0 function0, int i, Object obj) {
        if ((i & 4) != 0) {
            function0 = new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda5
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit unit;
                    unit = Unit.INSTANCE;
                    return unit;
                }
            };
        }
        botService.scroll(bounds, z, function0);
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void scrollCollection$default(BotService botService, Bounds bounds, boolean z, Function0 function0, int i, Object obj) {
        if ((i & 4) != 0) {
            function0 = new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda0
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit unit;
                    unit = Unit.INSTANCE;
                    return unit;
                }
            };
        }
        botService.scrollCollection(bounds, z, function0);
    }

    private final void setOverlayTouchable(boolean touchable) {
        WindowManager.LayoutParams layoutParams;
        LinearLayout linearLayout = this.overlay;
        if (linearLayout == null || (layoutParams = this.overlayParams) == null) {
            return;
        }
        FrameLayout blockerView = this.touchBlocker;
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
        if (!touchable && !this.panelPassThrough) {
            // the gesture does not cross the panel: keep the panel clickable so stop/pause answer at once
            return;
        }
        int i = layoutParams.flags;
        layoutParams.flags = touchable ? i & (-17) : i | 16;
        if (!touchable) {
            linearLayout.cancelPendingInputEvents();
            Button button = this.overlayButton;
            if (button != null) {
                button.setPressed(false);
            }
        }
        try {
            Object systemService = getSystemService("window");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
            ((WindowManager) systemService).updateViewLayout(linearLayout, layoutParams);
        } catch (Exception e) {
            layoutParams.flags = i;
            throw e;
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void swipe$default(BotService botService, Bounds bounds, Function0 function0, int i, Object obj) {
        if ((i & 2) != 0) {
            function0 = new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda7
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit unit;
                    unit = Unit.INSTANCE;
                    return unit;
                }
            };
        }
        botService.swipe(bounds, function0);
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ void tap$default(BotService botService, UiNode uiNode, Function0 function0, int i, Object obj) {
        if ((i & 2) != 0) {
            function0 = new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda12
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit unit;
                    unit = Unit.INSTANCE;
                    return unit;
                }
            };
        }
        botService.tap(uiNode, function0);
    }

    private final AccessibilityNodeInfo targetRoot() {
        Object obj;
        CharSequence packageName;
        List<AccessibilityWindowInfo> windows = getWindows();
        Intrinsics.checkNotNullExpressionValue(windows, "getWindows(...)");
        Iterator<?> it = windows.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            AccessibilityWindowInfo accessibilityWindowInfo = (AccessibilityWindowInfo) obj;
            if (accessibilityWindowInfo.getType() == 1 && accessibilityWindowInfo.isActive()) {
                AccessibilityNodeInfo root = accessibilityWindowInfo.getRoot();
                if (Intrinsics.areEqual((root == null || (packageName = root.getPackageName()) == null) ? null : packageName.toString(), getProfile().getPackageName())) {
                    break;
                }
            }
        }
        AccessibilityWindowInfo accessibilityWindowInfo2 = (AccessibilityWindowInfo) obj;
        if (accessibilityWindowInfo2 != null) {
            return accessibilityWindowInfo2.getRoot();
        }
        AccessibilityNodeInfo rootInActiveWindow = getRootInActiveWindow();
        if (rootInActiveWindow != null) {
            CharSequence packageName2 = rootInActiveWindow.getPackageName();
            if (Intrinsics.areEqual(packageName2 != null ? packageName2.toString() : null, getProfile().getPackageName())) {
                return rootInActiveWindow;
            }
        }
        return null;
    }

    public final void arm(String mode) {
        Intrinsics.checkNotNullParameter(mode, "mode");
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException(UsageConsent.INSTANCE.getRequiredMessage().toString());
        }
        if (getEngine().getActive()) {
            throw new IllegalStateException("현재 실행을 먼저 중지하세요.".toString());
        }
        this.panelOff = false;
        getEngine().begin(mode);
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            linearLayout.setVisibility(0);
        }
        long countdown = getStorage().getPrefs().getBoolean("turbo", false) ? 3000L : 8000L;
        long elapsedRealtime = SystemClock.elapsedRealtime() + countdown;
        this.armUntil = elapsedRealtime;
        this.lastActive = elapsedRealtime;
        setStatus((countdown / 1000) + "초 후 시작 · 중지 가능");
    }

    /** The ClassCard screen counts as "seen now"; used after a pause so a stale timestamp cannot stop the run. */
    public final void markScreenSeen() {
        this.lastActive = SystemClock.elapsedRealtime();
    }

    public final void back() {
        performGlobalAction(1);
        hold(350L);
    }

    public final boolean getBusy() {
        return this.busy;
    }

    public final BotEngine getEngine() {
        BotEngine botEngine = this.engine;
        if (botEngine != null) {
            return botEngine;
        }
        Intrinsics.throwUninitializedPropertyAccessException("engine");
        return null;
    }

    public final Handler getHandler() {
        return this.handler;
    }

    public final UiProfile getProfile() {
        UiProfile uiProfile = this.profile;
        if (uiProfile != null) {
            return uiProfile;
        }
        Intrinsics.throwUninitializedPropertyAccessException("profile");
        return null;
    }

    public final String getStatus() {
        return this.status;
    }

    public final Storage getStorage() {
        Storage storage = this.storage;
        if (storage != null) {
            return storage;
        }
        Intrinsics.throwUninitializedPropertyAccessException("storage");
        return null;
    }

    public final ExecutorService getWorker() {
        return this.worker;
    }

    public final void hold(long ms) {
        if (this.engine != null && getEngine().isTurbo()) {
            ms = Math.max(40L, ms / 2);
        }
        this.holdUntil = SystemClock.elapsedRealtime() + ms;
    }

    public final void ocrNode(UiNode target, final Function1<Object, Unit> after) {
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(after, "after");
        ocrNodes(CollectionsKt.listOf(target), new Function1() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda8
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                Unit ocrNode$lambda$27;
                ocrNode$lambda$27 = BotService.ocrNode$lambda$27(after, obj);
                return ocrNode$lambda$27;
            }
        });
    }

    public final void ocrNodes(List<UiNode> targets, Function1<Object, Unit> after) {
        Intrinsics.checkNotNullParameter(targets, "targets");
        Intrinsics.checkNotNullParameter(after, "after");
        if (!UsageConsent.INSTANCE.getAllowed()) {
            after.invoke(ResultCompat.failure(new IllegalStateException(UsageConsent.INSTANCE.getRequiredMessage())));
            return;
        }
        if (Build.VERSION.SDK_INT < 30) {
            after.invoke(ResultCompat.failure(new IllegalStateException("이미지 OCR 수집은 Android 11 이상에서 사용할 수 있습니다.")));
            return;
        }
        if (this.readingImage) {
            after.invoke(ResultCompat.failure(new IllegalStateException("이미지 인식이 진행 중입니다.")));
            return;
        }
        if (targets.isEmpty() || targets.size() > 64) {
            after.invoke(ResultCompat.failure(new IllegalArgumentException("인식 영역 수가 유효하지 않습니다.")));
            return;
        }
        List list = CollectionsKt.toList(targets);
        AccessibilityNodeInfo targetRoot = targetRoot();
        if (targetRoot == null) {
            after.invoke(ResultCompat.failure(new IllegalStateException("클래스카드 화면이 보이지 않습니다.")));
        } else {
            targetRoot.recycle();
            this.readingImage = true;
            final BotService$ocrNodes$2 callback = new BotService$ocrNodes$2(this, after, list);
            Runnable shoot = new Runnable() {
                @Override
                public void run() {
                    BotService.this.takeScreenshot(0, new Executor() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda9
                        @Override // java.util.concurrent.Executor
                        public final void execute(Runnable runnable) {
                            BotService.ocrNodes$lambda$28(BotService.this, runnable);
                        }
                    }, callback);
                }
            };
            // the floating panel would be photographed too: if it covers any area to be read, hide it for the shot
            if (panelCovers(list)) {
                this.overlay.setAlpha(0.0f);
                this.panelHiddenForShot = true;
                this.handler.postDelayed(shoot, 120L);
            } else {
                shoot.run();
            }
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override // android.app.Service
    public void onDestroy() {
        this.destroyed = true;
        this.overlayInputGuard.reset();
        this.busy = false;
        if (this.engine != null) {
            stop("접근성 연결 해제");
        }
        this.handler.removeCallbacksAndMessages(null);
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            Object systemService = getSystemService("window");
            Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.WindowManager");
            ((WindowManager) systemService).removeView(linearLayout);
        }
        FrameLayout blockerView = this.touchBlocker;
        if (blockerView != null) {
            try {
                ((WindowManager) getSystemService("window")).removeView(blockerView);
            } catch (Exception ignored) {
            }
            this.touchBlocker = null;
        }
        View watcher = this.touchWatcher;
        if (watcher != null) {
            ((WindowManager) getSystemService("window")).removeView(watcher);
            this.touchWatcher = null;
        }
        this.worker.execute(new Runnable() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda14
            @Override // java.lang.Runnable
            public final void run() {
                BotService.onDestroy$lambda$2(BotService.this);
            }
        });
        this.worker.shutdown();
        instance = null;
        super.onDestroy();
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
        stop("접근성 서비스 중단");
    }

    /** Writes one failure record for an unexpected exception: its type, message and the app frames it passed through. */
    final void recordCrash(Throwable e) {
        try {
            FailureLog.Record record = new FailureLog.Record();
            record.time = System.currentTimeMillis();
            record.code = "CRASH";
            StringBuilder where = new StringBuilder(e.getClass().getSimpleName());
            if (e.getMessage() != null) {
                where.append(": ").append(e.getMessage().length() > 80 ? e.getMessage().substring(0, 80) : e.getMessage());
            }
            int frames = 0;
            for (StackTraceElement el : e.getStackTrace()) {
                if (el.getClassName().startsWith("net.classcardauto") && frames < 3) {
                    String cls = el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1);
                    where.append(" @ ").append(cls).append('.').append(el.getMethodName()).append(':').append(el.getLineNumber());
                    frames++;
                }
            }
            record.message = where.toString();
            record.phase = this.engine != null ? String.valueOf(getEngine().getPhase()) : "";
            FailureLog.append(getStorage().getRoot(), record);
        } catch (Throwable ignored) {
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        this.destroyed = false;
        instance = this;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                recordCrash(e);
                if (previous != null) {
                    previous.uncaughtException(t, e);
                }
            }
        });
        BotService botService = this;
        setStorage(new Storage(botService));
        setProfile(new UiProfile(botService));
        setEngine(new BotEngine(this, getStorage()));
        createTouchBlocker();
        createSwipeGuide();
        createOverlay();
        createTouchWatcher();
        this.handler.post(this.ticker);
        setStatus("연결됨 · 대기");
    }

    public final List<UiNode> readScreen() {
        AccessibilityNodeInfo targetRoot;
        if (UsageConsent.INSTANCE.getAllowed() && (targetRoot = targetRoot()) != null) {
            ArrayList arrayList = new ArrayList();
            try {
                readScreen$walk$default(arrayList, this, targetRoot, 0, null, null, null, 112, null);
                targetRoot.recycle();
                return CollectionsKt.distinct(arrayList);
            } catch (Throwable th) {
                targetRoot.recycle();
                throw th;
            }
        }
        return CollectionsKt.emptyList();
    }

    public final void scroll(Bounds bounds, boolean forward, Function0<Unit> after) {
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(after, "after");
        float x = bounds.getX();
        gesture(x, bounds.getTop() + (bounds.getHeight() * (forward ? 0.8f : 0.3f)), x, bounds.getTop() + (bounds.getHeight() * (forward ? 0.3f : 0.8f)), 180L, after);
    }

    public final void scrollCollection(Bounds bounds, boolean forward, Function0<Unit> after) {
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(after, "after");
        float x = bounds.getX();
        android.content.SharedPreferences prefs = getStorage().getPrefs();
        float[] span = ScrollSettings.fractions(ScrollSettings.rangePercent(prefs), forward);
        long scrollMs = ScrollSettings.durationMs(prefs);
        float drift = 0.0f;
        if (getEngine().isHuman()) {
            java.util.Random rng = getEngine().getRng();
            scrollMs = HumanTiming.swipeDurationMs(rng, scrollMs);
            drift = HumanTiming.jitter(rng, 12.0f);
        }
        // 끝에서 잠깐 멈춘 뒤 떼면 관성(fling) 없이 스와이프한 만큼만 움직인다
        long endHold = prefs.getBoolean("collect_end_hold", true) ? 150L : 0L;
        this.lastCollectionBounds = bounds;
        float fromY = bounds.getTop() + (bounds.getHeight() * span[0]);
        float toY = bounds.getTop() + (bounds.getHeight() * span[1]);
        Function0<Unit> done = after;
        final SwipeGuide guide = this.swipeGuide;
        if (guide != null && prefs.getBoolean("collect_show_swipe", true)) {
            guide.show(x, fromY, toY, scrollMs + 32L, endHold + 400L, ScrollSettings.rangePercent(prefs) + "%");
            final Function0<Unit> original = after;
            // gone before the engine reads the screen again
            done = new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    guide.dismiss();
                    return original.invoke();
                }
            };
        }
        gesture(x, fromY, x + drift, toY, scrollMs, endHold, done);
    }

    public final void setEngine(BotEngine botEngine) {
        Intrinsics.checkNotNullParameter(botEngine, "<set-?>");
        this.engine = botEngine;
    }

    public final void setProfile(UiProfile uiProfile) {
        Intrinsics.checkNotNullParameter(uiProfile, "<set-?>");
        this.profile = uiProfile;
    }

    public final void setStatus(String text) {
        Intrinsics.checkNotNullParameter(text, "text");
        this.status = text;
        TextView textView = this.overlayText;
        if (textView != null) {
            textView.setText(text);
        }
        refreshOverlay();
    }

    public final void setStorage(Storage storage) {
        Intrinsics.checkNotNullParameter(storage, "<set-?>");
        this.storage = storage;
    }

    public final void stop(String reason) {
        Intrinsics.checkNotNullParameter(reason, "reason");
        if (this.engine != null) {
            getEngine().stop(reason);
        }
        setStatus(reason);
    }

    public final void swipe(Bounds bounds, Function0<Unit> after) {
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(after, "after");
        if (!bounds.getValid()) {
            throw new IllegalStateException("카드 위치를 확인할 수 없습니다.".toString());
        }
        gesture(bounds.getLeft() + (bounds.getWidth() * 0.85f), bounds.getY(), bounds.getLeft() + (bounds.getWidth() * 0.15f), bounds.getY(), 250L, after);
    }

    public final void swipeCard(Bounds bounds, List<UiNode> ns) {
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(ns, "ns");
        SwipePath safeSwipePath = CoreKt.safeSwipePath(bounds, ns);
        BotEngine engine = getEngine();
        QuietJson put = new QuietJson().put("start_x", Float.valueOf(safeSwipePath.getStartX())).put("end_x", Float.valueOf(safeSwipePath.getEndX())).put("y", Float.valueOf(safeSwipePath.getY())).put("duration_ms", 250).put("outside_answer_rows", true);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        engine.log("card_gesture", put);
        float humanDy = 0.0f;
        float humanDx = 0.0f;
        long swipeMs = getEngine().isTurbo() ? 150L : 250L;
        if (getEngine().isHuman()) {
            java.util.Random rng = getEngine().getRng();
            humanDy = HumanTiming.jitter(rng, 6.0f);
            humanDx = HumanTiming.jitter(rng, 10.0f);
            swipeMs = HumanTiming.swipeDurationMs(rng, 250L);
        }
        gesture(safeSwipePath.getStartX(), safeSwipePath.getY(), safeSwipePath.getEndX() + humanDx, safeSwipePath.getY() + humanDy, swipeMs, new Function0() { // from class: net.classcardauto.mobile.BotService$$ExternalSyntheticLambda11
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit unit;
                unit = Unit.INSTANCE;
                return unit;
            }
        });
    }

    public final void tap(UiNode target, Function0<Unit> after) {
        tap(target, after, false);
    }

    /** {@code precise}: a repeated attempt, so no human-like scatter - aim at the middle with a normal press. */
    public final void tap(UiNode target, Function0<Unit> after, boolean precise) {
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(after, "after");
        if (!target.getBounds().getValid() || !target.getEnabled()) {
            throw new IllegalStateException("유효하지 않은 터치 위치입니다.".toString());
        }
        Bounds b = target.getBounds();
        this.verifyTarget = target;
        try {
            if (getEngine().isHuman() && !precise) {
                java.util.Random rng = getEngine().getRng();
                float[] p = HumanTiming.tapPoint(rng, b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
                gesture(p[0], p[1], p[0], p[1], HumanTiming.tapDurationMs(rng), after);
                return;
            }
            gesture(b.getX(), b.getY(), b.getX(), b.getY(), precise ? 70L : 45L, after);
        } catch (RuntimeException e) {
            this.verifyTarget = null;
            throw e;
        }
    }
}
