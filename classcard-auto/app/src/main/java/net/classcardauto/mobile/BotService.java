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
                    }
                    long elapsedRealtime = SystemClock.elapsedRealtime();
                    Object systemService = BotService.this.getSystemService("power");
                    Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.os.PowerManager");
                    Object systemService2 = BotService.this.getSystemService("keyguard");
                    Intrinsics.checkNotNull(systemService2, "null cannot be cast to non-null type android.app.KeyguardManager");
                    if (!((PowerManager) systemService).isInteractive() || ((KeyguardManager) systemService2).isKeyguardLocked()) {
                        BotService.this.stop("화면이 잠겨 중지했습니다.");
                    } else {
                        j2 = BotService.this.armUntil;
                        if (BotService.this.getEngine().isPaused()) {
                            // paused: the user is in control, do not touch the screen
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
                                        if (elapsedRealtime - j4 > 5000) {
                                            BotService.this.stop("클래스카드 화면이 보이지 않아 중지했습니다.");
                                        }
                                    } else {
                                        BotService.this.lastActive = elapsedRealtime;
                                        BotService.this.getEngine().tick(readScreen, elapsedRealtime);
                                    }
                                }
                            }
                        }
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
            } catch (Exception e) {
                BotEngine engine = BotService.this.getEngine();
                String message = e.getMessage();
                if (message == null) {
                    message = e.getClass().getSimpleName();
                }
                Intrinsics.checkNotNull(message);
                AnswerResolutionException answerResolutionException = e instanceof AnswerResolutionException ? (AnswerResolutionException) e : null;
                engine.fail(message, answerResolutionException != null ? answerResolutionException.getFailure() : null);
            }
            BotService.this.getHandler().postDelayed(this, 100L);
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
                if (BotService.this.busy) {
                    return;
                }
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
            if (this.engine != null && getEngine().isInTest()) {
                snapPanelToEdge();
            }
        }
    }

    private boolean snappedForTest;

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
        if (botService.overlayInputGuard.getBlocked() || botService.busy) {
            return;
        }
        try {
            if (botService.getEngine().getActive()) {
                botService.stop("사용자 중지");
            } else if (botService.getStorage().getPrefs().getBoolean("touch_lock", true) && SystemClock.elapsedRealtime() > botService.lockConfirmUntil) {
                // first press: explain the lock; a second press within 10 s starts
                botService.lockConfirmUntil = SystemClock.elapsedRealtime() + 10000;
                botService.setStatus("시작하면 화면 터치가 잠겨요 (일시정지로 해제). 시작하려면 10초 안에 한 번 더");
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
        final GestureDescription build = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0L, duration)).build();
        final long begin = this.overlayInputGuard.begin();
        this.busy = true;
        try {
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
        try {
            BotService$gesture$3$accepted$1 accepted = new BotService$gesture$3$accepted$1(botService, j, i2, function0);
            if (botService.dispatchGesture(gestureDescription, accepted, botService.handler)) {
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
        boolean inTestNow = active && getEngine().isInTest();
        if (inTestNow && !this.snappedForTest) {
            this.snappedForTest = true;
            snapPanelToEdge();
        } else if (!inTestNow) {
            this.snappedForTest = false;
        }
        FloatingPanel panel = this.floating;
        if (panel != null) {
            panel.render(active, this.status, !active || collecting, beforeTest);
            panel.renderRun(getEngine().isPaused(), getEngine().getUserTouches(), getEngine().getTouchLimit(), getEngine().isInTest(), getEngine().isTouchLocked(), getEngine().getBlockedTouches());
        }
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            linearLayout.setVisibility((UsageConsent.INSTANCE.getAllowed() && (active || this.currentStartAllowed) && !this.overlayDismissed) ? 0 : 8);
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
        getEngine().begin(mode);
        LinearLayout linearLayout = this.overlay;
        if (linearLayout != null) {
            linearLayout.setVisibility(0);
        }
        long elapsedRealtime = SystemClock.elapsedRealtime() + 8000;
        this.armUntil = elapsedRealtime;
        this.lastActive = elapsedRealtime;
        setStatus("8초 후 시작 · 중지 가능");
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

    @Override // android.accessibilityservice.AccessibilityService
    protected void onServiceConnected() {
        this.destroyed = false;
        instance = this;
        BotService botService = this;
        setStorage(new Storage(botService));
        setProfile(new UiProfile(botService));
        setEngine(new BotEngine(this, getStorage()));
        createTouchBlocker();
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
        gesture(x, bounds.getTop() + (bounds.getHeight() * span[0]), x + drift, bounds.getTop() + (bounds.getHeight() * span[1]), scrollMs, after);
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
        long swipeMs = 250L;
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
        if (getEngine().isHuman() && !precise) {
            java.util.Random rng = getEngine().getRng();
            float[] p = HumanTiming.tapPoint(rng, b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
            gesture(p[0], p[1], p[0], p[1], HumanTiming.tapDurationMs(rng), after);
            return;
        }
        gesture(b.getX(), b.getY(), b.getX(), b.getY(), precise ? 70L : 45L, after);
    }
}
