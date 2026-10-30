package net.classcardauto.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import kotlin.Deprecated;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.io.ConstantsKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import org.json.JSONObject;

/* compiled from: MainActivity.kt */
/* loaded from: /tmp/claude-0/work/out/x/classes.dex */
public final class MainActivity extends Activity {
    private Button accessibilityButton;
    private Dialog messageDialog;
    private Screens.Home home;
    private boolean inSettings;
    private Screens.Link lastLink;
    private Screens.Activation activation;
    private final Runnable activationPoll = new Runnable() {
        @Override
        public void run() {
            MainActivity.this.pollActivation();
        }
    };
    private Button primary;
    private boolean returned;
    private Dialog settingsDialog;
    private TextView status;
    private Button stopButton;
    private Storage storage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() { // from class: net.classcardauto.mobile.MainActivity$refresh$1
        @Override // java.lang.Runnable
        public void run() {
            Handler handler;
            MainActivity.this.refreshStatus();
            handler = MainActivity.this.handler;
            handler.postDelayed(this, 500L);
        }
    };

    private final void beginEntry(boolean newLaunch) {
        Dialog alertDialog = this.settingsDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
        Dialog alertDialog2 = this.messageDialog;
        if (alertDialog2 != null) {
            alertDialog2.dismiss();
        }
        this.inSettings = false;
        this.settingsDialog = null;
        this.messageDialog = null;
        this.activation = null;
        this.handler.removeCallbacks(this.activationPoll);
        AppPassword.INSTANCE.getSession().enter(newLaunch);
        UsageConsent.INSTANCE.restore();
        Certification.INSTANCE.refresh();
        if (!Certification.INSTANCE.getAccessAllowed()) {
            BotService companion = BotService.INSTANCE.getInstance();
            if (companion != null) {
                companion.stop("기기 인증이 필요합니다.");
            }
            showUncertified();
            return;
        }
        if (AppPassword.INSTANCE.getAllowed()) {
            continueEntry();
            return;
        }
        BotService companion2 = BotService.INSTANCE.getInstance();
        if (companion2 != null) {
            companion2.stop("앱 비밀번호 확인이 필요합니다.");
        }
        showPassword();
    }

    static /* synthetic */ void beginEntry$default(MainActivity mainActivity, boolean z, int i, Object obj) {
        if ((i & 1) != 0) {
            z = false;
        }
        mainActivity.beginEntry(z);
    }

    private final void brand(LinearLayout body) {
        MainActivity mainActivity = this;
        LinearLayout linearLayout = new LinearLayout(mainActivity);
        linearLayout.setGravity(16);
        ImageView imageView = new ImageView(mainActivity);
        imageView.setImageResource(R.drawable.app_mark);
        imageView.setImportantForAccessibility(2);
        linearLayout.addView(imageView, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView label$default = label$default(this, "MiMiC", 16.0f, false, true, 4, null);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.leftMargin = dp(10);
        Unit unit = Unit.INSTANCE;
        linearLayout.addView(label$default, layoutParams);
        body.addView(linearLayout);
    }

    private final Button button(String text, boolean main, final Function0<Unit> action) {
        return AppStyle.INSTANCE.button(this, text, main, new Function0() { // from class: net.classcardauto.mobile.MainActivity$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit button$lambda$0;
                button$lambda$0 = MainActivity.button$lambda$0(action, MainActivity.this);
                return button$lambda$0;
            }
        });
    }

    static /* synthetic */ Button button$default(MainActivity mainActivity, String str, boolean z, Function0 function0, int i, Object obj) {
        if ((i & 2) != 0) {
            z = true;
        }
        return mainActivity.button(str, z, function0);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit button$lambda$0(Function0 function0, MainActivity mainActivity) {
        try {
            function0.invoke();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "잠시 후 다시 시도해주세요.";
            }
            message$default(mainActivity, message, null, 2, null);
        }
        return Unit.INSTANCE;
    }

    private final void closeApp() {
        AppPassword.INSTANCE.lock();
        BotService companion = BotService.INSTANCE.getInstance();
        if (companion != null) {
            companion.stop("앱을 닫았습니다.");
        }
        finish();
    }

    private final void continueEntry() {
        if (!AppPassword.INSTANCE.getAllowed()) {
            showPassword();
            return;
        }
        if (UsageConsent.INSTANCE.getAllowed()) {
            showHome();
            return;
        }
        BotService companion = BotService.INSTANCE.getInstance();
        if (companion != null) {
            companion.stop("사용 안내 동의가 필요합니다.");
        }
        showConsent();
    }

    private final void decline() {
        UsageConsent.INSTANCE.revoke();
        BotService companion = BotService.INSTANCE.getInstance();
        if (companion != null) {
            companion.stop("사용 안내에 동의하지 않아 종료합니다.");
        }
        AppPassword.INSTANCE.lock();
        finish();
    }

    private final int dp(int value) {
        return AppStyle.INSTANCE.dp(this, value);
    }

    private final TextView label(String text, float size, boolean muted, boolean bold) {
        return AppStyle.INSTANCE.label(this, text, size, muted ? AppStyle.INSTANCE.getMuted() : AppStyle.INSTANCE.getInk(), bold);
    }

    static /* synthetic */ TextView label$default(MainActivity mainActivity, String str, float f, boolean z, boolean z2, int i, Object obj) {
        if ((i & 2) != 0) {
            f = 15.0f;
        }
        if ((i & 4) != 0) {
            z = false;
        }
        if ((i & 8) != 0) {
            z2 = false;
        }
        return mainActivity.label(str, f, z, z2);
    }

    private final void message(String text, String title) {
        Dialog dialog = AppStyle.INSTANCE.sheet(this, title, Screens.textBody(this, text), "닫기", null, null, null);
        this.messageDialog = dialog;
        dialog.show();
    }

    static /* synthetic */ void message$default(MainActivity mainActivity, String str, String str2, int i, Object obj) {
        if ((i & 2) != 0) {
            str2 = "안내";
        }
        mainActivity.message(str, str2);
    }

    private final LinearLayout page() {
        LinearLayout stack$default = AppStyle.stack$default(AppStyle.INSTANCE, this, 0, 2, null);
        stack$default.setBackgroundColor(AppStyle.INSTANCE.getBackground());
        stack$default.setFocusableInTouchMode(true);
        return stack$default;
    }

    private final void privacy() {
        message("개인정보처리방침\n적용일: 2026년 10월 26일\n\n1. 기기 내부 처리\n화면 글자와 이미지는 실행 중 기기 메모리에서 처리합니다. 수집한 단어·뜻은 완료·중지·오류 종료 시 삭제하며 파일로 저장하지 않습니다.\n\n2. 외부 SDK\nMicrosoft ONNX Runtime Android 1.31.0과 Qota-OCR 모델을 이용해 기기 내부에서 이미지를 인식합니다. 모델은 앱에 포함됩니다. 앱은 인터넷 권한을 요청하지 않으며, 인식 입력·결과를 외부 서버에 전송하는 코드는 없습니다. 분석·광고 SDK는 사용하지 않습니다.\n\n3. 실행 기록\n실행 중 일어난 일은 파일이나 시스템 로그에 기록하지 않습니다. 마지막 실행 결과 요약만 앱이 켜져 있는 동안 메모리에 두고 앱이 종료되면 사라지며, 세트 이름·코드도 포함하지 않습니다. 문항·단어·뜻 원문이나 OCR 이미지는 기록하지 않습니다. 실행 중 화면 터치 잠금을 켜면 눈에 보이지 않는 투명한 막이 화면을 덮어 플로팅 패널 외의 터치를 받아 버리며, 막은 횟수만 세고 위치·내용은 기록하지 않습니다. 시험 중에는 사용자가 화면을 직접 터치한 횟수(위치·내용 제외)만 세어 일시정지 판단에 쓰며 저장하지 않습니다. 실행이 멈춘 시각·이유·문항 번호는 개발 참고용으로 이 휴대폰 안에 최대 100건 저장하며 외부로 전송하지 않습니다. 실패 기록 화면에서 언제든 지울 수 있습니다. 앱 데이터 삭제 또는 앱 제거로 통계를 지울 수 있습니다.\n\n4. 접근성 권한\n사용자가 시작한 실행 중 선택한 클래스카드 화면을 읽고 필요한 화면 전환을 수행합니다. 설정에서 권한을 끌 수 있습니다. 클래스카드 자체의 네트워크 처리에는 해당 서비스의 개인정보처리방침이 적용됩니다.\n\n5. 동의\n두 단계 동의를 모두 마친 시각과 약관 버전 확인값을 앱 내부에 저장합니다. 같은 약관에는 다시 동의하지 않아도 됩니다. 약관이 변경되면 다시 동의를 받습니다. 설정의 약관 동의 기록에서 확인하거나 철회할 수 있습니다. 앱 제거 또는 데이터 삭제 시 기록도 삭제됩니다.\n\n무인증 버전의 비밀번호 입력값과 잠금 해제 상태는 파일로 저장하거나 외부로 전송하지 않습니다. 다른 앱·설정에 갔다 돌아오면 잠금 해제를 유지하고, 앱을 닫고 새로 실행하거나 프로세스가 재시작되면 비밀번호를 다시 입력해야 합니다.".concat(Certification.INSTANCE.getRequired() ? "\n\n6. 기기 인증서\n인증 시각·번호·인증 컴퓨터의 이름·기종·식별 번호·휴대폰 식별 번호와 전자서명을 앱 내부에 저장합니다. 인터넷으로 전송하지 않습니다. 앱 데이터 삭제 또는 앱 제거 시 인증서도 삭제됩니다." : ""), "개인정보처리방침");
    }

    public final void refreshStatus() {
        BotEngine engine;
        Screens.Home h = this.home;
        if (h == null || !UsageConsent.INSTANCE.getAllowed()) {
            return;
        }
        BotService companion = BotService.INSTANCE.getInstance();
        boolean running = (companion == null || (engine = companion.getEngine()) == null || !engine.getActive()) ? false : true;
        boolean paused = running && companion.getEngine().isPaused();
        Screens.Link link = companion == null ? Screens.Link.NO_SERVICE : (paused ? Screens.Link.PAUSED : (running ? Screens.Link.RUNNING : Screens.Link.READY));
        if (link == Screens.Link.RUNNING || link == Screens.Link.PAUSED) {
            BotEngine eng = companion.getEngine();
            long eta = eng.getEtaSeconds();
            String note = eng.isInTest() ? (eta >= 0 ? "예상 완료까지 약 " + AppStyle.formatClock(eta) + " · " : "") + "직접 터치 " + eng.getUserTouches() + " / " + eng.getTouchLimit() + "회 · 한도에 닿으면 자동으로 일시정지해요." : null;
            if (link == Screens.Link.PAUSED) {
                note = "직접 터치 " + eng.getUserTouches() + "회. 클래스카드에서 직접 조작한 뒤 화면의 플로팅 버튼 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요.";
            }
            h.render(link, companion.getStatus(), eng.getRemainingSeconds(), note);
        } else {
            h.render(link, null);
        }
        if (link != this.lastLink) {
            this.lastLink = link;
            updateFailureBanner(h, link);
        }
    }

    private final void settings() {
        showSettings();
    }

    private final Function0<Unit> guard(final Function0<Unit> action) {
        return new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.button$lambda$0(action, MainActivity.this);
            }
        };
    }

    private final void updateFailureBanner(Screens.Home h, Screens.Link link) {
        String text = null;
        if (link != Screens.Link.RUNNING && this.storage != null) {
            java.util.List<FailureLog.Record> all = FailureLog.newestFirst(this.storage.getRoot());
            if (!all.isEmpty()) {
                FailureLog.Record last = all.get(0);
                if (System.currentTimeMillis() - last.time < 10 * 60 * 1000L) {
                    text = "방금 실행이 멈췄어요 · " + FailureLog.label(last.code);
                }
            }
        }
        h.showFailure(text);
    }

    private final void showFailures() {
        this.inSettings = true;
        this.home = null;
        final java.util.List<FailureLog.Record> records = FailureLog.newestFirst(this.storage.getRoot());
        setContentView(Screens.failures(this, records, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("실패 기록", FailureLog.report(records));
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.confirmClearFailures();
                return Unit.INSTANCE;
            }
        })));
    }

    private final void confirmClearFailures() {
        Dialog dialog = AppStyle.INSTANCE.sheet(this, "기록을 모두 지울까요?", Screens.textBody(this, "저장된 실패 기록이 이 휴대폰에서 삭제되고 되돌릴 수 없어요."), "지우기", new Runnable() {
            @Override
            public void run() {
                FailureLog.clear(MainActivity.this.storage.getRoot());
                MainActivity.this.showFailures();
            }
        }, "취소", null);
        this.messageDialog = dialog;
        dialog.show();
    }

    private final void showSettings() {
        this.inSettings = true;
        this.home = null;
        java.util.List<Screens.Nav> help = new java.util.ArrayList<>();
        help.add(new Screens.Nav("실패 기록", "멈춘 이유와 시각 확인", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        })));
        help.add(new Screens.Nav("접근성 설정", "‘MiMiC’ 권한을 켜거나 끕니다", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$38(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("사용 안내 다시 보기", null, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$39(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("약관 동의 기록", "동의 시각 확인 · 동의 철회", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$40(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("개인정보처리방침", null, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$41(MainActivity.this);
            }
        })));
        if (Certification.INSTANCE.getRequired()) {
            help.add(new Screens.Nav("인증서 보기", null, guard(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    return MainActivity.settings$lambda$42(MainActivity.this);
                }
            })));
        }
        help.add(new Screens.Nav("최근 실행", "마지막 실행의 결과와 오류 요약", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$47(MainActivity.this);
            }
        })));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "";
        }
        SharedPreferences prefs = this.storage.getPrefs();
        setContentView(Screens.settings(this, prefs, version, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, help));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void settings$lambda$35$lambda$34(MainActivity mainActivity, CompoundButton compoundButton, boolean z) {
        Storage storage = mainActivity.storage;
        if (storage == null) {
            Intrinsics.throwUninitializedPropertyAccessException("storage");
            storage = null;
        }
        storage.getPrefs().edit().putBoolean("image_collection", z).putBoolean("ocr_only", z).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void settings$lambda$37$lambda$36(MainActivity mainActivity, CompoundButton compoundButton, boolean z) {
        Storage storage = mainActivity.storage;
        if (storage == null) {
            Intrinsics.throwUninitializedPropertyAccessException("storage");
            storage = null;
        }
        storage.getPrefs().edit().putBoolean("partial_choices", z).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit settings$lambda$38(MainActivity mainActivity) {
        mainActivity.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit settings$lambda$39(MainActivity mainActivity) {
        mainActivity.message(UsageConsent.INSTANCE.getNotice(), "사용 안내");
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit settings$lambda$40(MainActivity mainActivity) {
        mainActivity.showConsentRecord();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit settings$lambda$41(MainActivity mainActivity) {
        mainActivity.privacy();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit settings$lambda$42(MainActivity mainActivity) {
        Certification.INSTANCE.refresh();
        CertifiedDocument document = Certification.INSTANCE.getDocument();
        if (document == null) {
            beginEntry$default(mainActivity, false, 1, null);
        } else {
            mainActivity.message(document.display(), "기기 인증서");
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:8:0x002c, code lost:
    
        if (kotlin.text.StringsKt.isBlank(r1) != false) goto L9;
     */
    public static final Unit settings$lambda$47(MainActivity mainActivity) {
        String str;
        Object obj;
        String str2;
        try {
            Storage storage = mainActivity.storage;
            if (storage == null) {
                Intrinsics.throwUninitializedPropertyAccessException("storage");
                storage = null;
            }
            JSONObject jSONObject = new JSONObject(storage.latestSummary());
            String optString = jSONObject.optString("score");
            if (Intrinsics.areEqual(optString, "null") || StringsKt.isBlank(optString)) {
                optString = null;
            }
            Iterator<?> it = AnswerFailure.getEntries().iterator();
            while (true) {
                if (!it.hasNext()) {
                    obj = null;
                    break;
                }
                obj = it.next();
                if (Intrinsics.areEqual(((AnswerFailure) obj).name(), jSONObject.optString("error_code"))) {
                    break;
                }
            }
            AnswerFailure answerFailure = (AnswerFailure) obj;
            if (!Intrinsics.areEqual(jSONObject.optString("status"), "error")) {
                str2 = null;
            } else if (answerFailure == null || (str2 = answerFailure.getExplanation()) == null) {
                str2 = "화면 처리 중 오류로 종료됐습니다.";
            }
            String optString2 = jSONObject.optString("last_progress");
            Intrinsics.checkNotNull(optString2);
            if (StringsKt.isBlank(optString2)) {
                optString2 = null;
            }
            String[] strArr = new String[6];
            strArr[0] = optString;
            strArr[1] = str2;
            strArr[2] = optString2 != null ? "마지막 문항 " + optString2 : null;
            strArr[3] = "확인한 자료 " + jSONObject.optInt("collected_cards") + "개";
            String format = String.format("%.1f", Arrays.copyOf(new Object[]{Double.valueOf(jSONObject.optDouble("total_seconds"))}, 1));
            Intrinsics.checkNotNullExpressionValue(format, "format(...)");
            strArr[4] = "실행 시간 " + format + "초";
            strArr[5] = "수집 자료 삭제 완료";
            str = CollectionsKt.joinToString(CollectionsKt.listOfNotNull((Object[]) strArr), (CharSequence) "\n", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", null);
        } catch (Exception unused) {
            str = "아직 실행 기록이 없습니다.";
        }
        mainActivity.message(str, "최근 실행");
        return Unit.INSTANCE;
    }

    private final void showConsent() {
        ScrollView scrollView;
        LinearLayout linearLayout;
        final MainActivity mainActivity = this;
        if (!Certification.INSTANCE.getAccessAllowed()) {
            showUncertified();
            return;
        }
        if (!AppPassword.INSTANCE.getAllowed()) {
            showPassword();
            return;
        }
        getWindow().setStatusBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().setNavigationBarColor(AppStyle.INSTANCE.getBackground());
        if (Build.VERSION.SDK_INT >= 28) {
            Window window = getWindow();
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            attributes.layoutInDisplayCutoutMode = 0;
            window.setAttributes(attributes);
        }
        getWindow().getDecorView().setSystemUiVisibility(8208);
        this.status = null;
        this.primary = null;
        this.stopButton = null;
        final boolean z = UsageConsent.INSTANCE.getSession().getStep() == 1;
        this.home = null;
        Screens.Consent consent = Screens.consent(this, z, UsageConsent.INSTANCE.getNotice(), UsageConsent.INSTANCE.getPermissionDisclosure(), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$19(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$20(z, mainActivity);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$21(MainActivity.this);
            }
        }));
        setContentView(consent.root);
        consent.root.requestFocus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showConsent$lambda$19(MainActivity mainActivity) {
        mainActivity.privacy();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showConsent$lambda$20(boolean z, MainActivity mainActivity) {
        if (z) {
            UsageConsent.INSTANCE.confirmUse();
            mainActivity.showHome();
        } else {
            UsageConsent.INSTANCE.getSession().agreeToNotice();
            mainActivity.showConsent();
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showConsent$lambda$21(MainActivity mainActivity) {
        mainActivity.decline();
        return Unit.INSTANCE;
    }

    private final void showConsentRecord() {
        String str;
        ConsentRecord record = UsageConsent.INSTANCE.record();
        if (record == null) {
            str = "저장된 동의 기록이 없습니다.";
        } else {
            str = "두 단계 동의 완료\n\n동의 시각: " + DateFormat.getDateTimeInstance().format(new Date(record.getAcceptedAtMillis())) + "\n약관 버전: 2026-10-26\n\n이 휴대폰의 앱 내부에 저장된 기록입니다.";
        }
        final MainActivity self = this;
        Dialog dialog = AppStyle.INSTANCE.sheet(this, "약관 동의 기록", Screens.textBody(this, str), "닫기", null, "동의 철회", new Runnable() {
            @Override
            public void run() {
                MainActivity.showConsentRecord$lambda$50(self, null, 0);
            }
        });
        this.messageDialog = dialog;
        dialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void showConsentRecord$lambda$50(MainActivity mainActivity, DialogInterface dialogInterface, int i) {
        try {
            UsageConsent.INSTANCE.revoke();
            BotService companion = BotService.INSTANCE.getInstance();
            if (companion != null) {
                companion.stop("사용 안내 동의를 철회했습니다.");
            }
            beginEntry$default(mainActivity, false, 1, null);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "동의 기록 삭제 실패";
            }
            message$default(mainActivity, message, null, 2, null);
        }
    }

    private final void showHome() {
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException("Check failed.");
        }
        this.inSettings = false;
        Screens.Home h = Screens.home(this, this.storage.getPrefs(), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$32(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$26(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$28();
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$30(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        }));
        this.home = h;
        this.lastLink = null;
        setContentView(h.root);
        refreshStatus();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showHome$lambda$26(MainActivity mainActivity) {
        mainActivity.start();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showHome$lambda$28() {
        BotService companion = BotService.INSTANCE.getInstance();
        if (companion != null) {
            companion.stop("사용자 중지");
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showHome$lambda$30(MainActivity mainActivity) {
        mainActivity.startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showHome$lambda$32(MainActivity mainActivity) {
        mainActivity.settings();
        return Unit.INSTANCE;
    }

    private final void showPassword() {
        this.home = null;
        this.status = null;
        this.primary = null;
        this.stopButton = null;
        this.accessibilityButton = null;
        getWindow().addFlags(ConstantsKt.DEFAULT_BUFFER_SIZE);
        final Screens.Password[] holder = new Screens.Password[1];
        holder[0] = Screens.password(this, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.showPassword$unlock(holder[0].input, MainActivity.this, holder[0].error);
                return Unit.INSTANCE;
            }
        }, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.closeApp();
                return Unit.INSTANCE;
            }
        });
        holder[0].input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != 6) {
                    return false;
                }
                MainActivity.showPassword$unlock(holder[0].input, MainActivity.this, holder[0].error);
                return true;
            }
        });
        setContentView(holder[0].root);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showPassword$lambda$2(EditText editText, MainActivity mainActivity, TextView textView) {
        showPassword$unlock(editText, mainActivity, textView);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit showPassword$lambda$3(MainActivity mainActivity) {
        mainActivity.closeApp();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final boolean showPassword$lambda$4(EditText editText, MainActivity mainActivity, TextView textView, TextView textView2, int i, KeyEvent keyEvent) {
        if (i != 6) {
            return false;
        }
        showPassword$unlock(editText, mainActivity, textView);
        return true;
    }

    private static final void showPassword$unlock(EditText editText, MainActivity mainActivity, TextView textView) {
        String obj = editText.getText().toString();
        editText.getText().clear();
        if (!AppPassword.INSTANCE.getSession().unlock(obj)) {
            textView.setText("비밀번호가 맞지 않습니다. 다시 입력해주세요.");
            editText.requestFocus();
            return;
        }
        Object systemService = mainActivity.getSystemService("input_method");
        Intrinsics.checkNotNull(systemService, "null cannot be cast to non-null type android.view.inputmethod.InputMethodManager");
        ((InputMethodManager) systemService).hideSoftInputFromWindow(editText.getWindowToken(), 0);
        mainActivity.getWindow().clearFlags(ConstantsKt.DEFAULT_BUFFER_SIZE);
        mainActivity.continueEntry();
    }

    private final void showUncertified() {
        this.status = null;
        this.primary = null;
        this.stopButton = null;
        this.accessibilityButton = null;
        this.home = null;
        getWindow().setStatusBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().setNavigationBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().getDecorView().setSystemUiVisibility(8208);
        final String code = Certification.INSTANCE.deviceId();
        final String folder = Certification.INSTANCE.dropPathLabel();
        Screens.Activation a = Screens.activation(this, code, folder, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.checkActivation(true);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("기기 코드", code);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("폴더 경로", folder);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.submitAuthCode();
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.closeApp();
                return Unit.INSTANCE;
            }
        }));
        this.activation = a;
        setContentView(a.root);
        this.handler.removeCallbacks(this.activationPoll);
        this.handler.postDelayed(this.activationPoll, 2000L);
    }

    /** Checks the code typed from the authenticator app. */
    private final void submitAuthCode() {
        Screens.Activation a = this.activation;
        if (a == null) {
            return;
        }
        String message = AuthChallenge.submit(this, a.codeInput.getText().toString());
        if (message != null) {
            a.status.setText(AppStyle.keepWords(message));
            return;
        }
        this.activation = null;
        this.handler.removeCallbacks(this.activationPoll);
        beginEntry$default(this, false, 1, null);
    }

    private final void copyText(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService("clipboard");
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            android.widget.Toast.makeText(this, label + " 복사 완료", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private final void pollActivation() {
        if (this.activation == null) {
            return;
        }
        checkActivation(false);
        if (this.activation != null) {
            this.handler.postDelayed(this.activationPoll, 2000L);
        }
    }

    /** Looks for a key file in the drop folder; moves on to the normal entry flow once authorised. */
    private final void checkActivation(boolean manual) {
        Screens.Activation a = this.activation;
        if (a == null) {
            return;
        }
        if (Certification.INSTANCE.scanDropFolder()) {
            this.activation = null;
            this.handler.removeCallbacks(this.activationPoll);
            beginEntry$default(this, false, 1, null);
            return;
        }
        String message = Certification.INSTANCE.lastDropMessage();
        if (message.isEmpty() && manual) {
            message = "폴더에서 키 파일을 찾지 못했어요. 파일을 넣었는지, 이름이 달라도 괜찮으니 폴더 위치가 맞는지 확인하세요.";
        }
        a.status.setText(AppStyle.keepWords(message));
    }

    private final void start() {
        Storage st = this.storage;
        if (st != null && st.getPrefs().getBoolean("touch_lock", true) && BotService.INSTANCE.getInstance() != null && !BotService.INSTANCE.getInstance().getEngine().getActive()) {
            Dialog dialog = AppStyle.INSTANCE.sheet(this, "화면이 잠겨요", Screens.textBody(this, "시작하면 클래스카드 화면의 터치가 막히고, 플로팅 패널의 버튼만 눌려요.\n\n• 직접 조작하려면 패널의 ‘일시정지’를 먼저 누르세요. 일시정지 중에는 잠금이 풀려요.\n• 끝나거나 멈추면 자동으로 풀려요.\n• 상태바 내리기, 홈·뒤로 제스처, 전원·볼륨 버튼은 막지 못해요.\n• 설정에서 ‘실행 중 화면 터치 잠금’을 끌 수 있어요."), "잠그고 시작", new Runnable() {
                @Override
                public void run() {
                    try {
                        MainActivity.this.startNow();
                    } catch (Exception e) {
                        android.widget.Toast.makeText(MainActivity.this, String.valueOf(e.getMessage()), android.widget.Toast.LENGTH_LONG).show();
                    }
                }
            }, "취소", null);
            dialog.show();
            return;
        }
        startNow();
    }

    private final void startNow() {
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException(UsageConsent.INSTANCE.getRequiredMessage().toString());
        }
        BotService companion = BotService.INSTANCE.getInstance();
        if (companion == null) {
            throw new IllegalStateException("접근성 설정에서 MiMiC를 켜주세요.".toString());
        }
        Storage storage = this.storage;
        if (storage == null) {
            Intrinsics.throwUninitializedPropertyAccessException("storage");
            storage = null;
        }
        storage.getPrefs().edit().putBoolean("natural", false).putBoolean("replace", false).apply();
        companion.arm("screen_auto");
        Intent launchIntentForPackage = getPackageManager().getLaunchIntentForPackage("classcard.net");
        if (launchIntentForPackage == null) {
            throw new IllegalStateException("클래스카드가 설치되지 않았습니다.".toString());
        }
        startActivity(launchIntentForPackage);
    }

    public void onBackPressed() {
        if (this.inSettings && UsageConsent.INSTANCE.getAllowed()) {
            showHome();
            return;
        }
        closeApp();
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        this.storage = new Storage(this);
        getWindow().setStatusBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().setNavigationBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().getDecorView().setSystemUiVisibility(8208);
        beginEntry(state == null);
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        Intrinsics.checkNotNullParameter(intent, "intent");
        super.onNewIntent(intent);
        setIntent(intent);
        beginEntry$default(this, false, 1, null);
    }

    @Override // android.app.Activity
    protected void onPause() {
        this.handler.removeCallbacks(this.refresh);
        super.onPause();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        BotService panelService = BotService.INSTANCE.getInstance();
        if (panelService != null) {
            panelService.showPanel();
        }
        this.handler.post(this.refresh);
    }

    @Override // android.app.Activity
    protected void onStart() {
        super.onStart();
        if (this.returned) {
            this.returned = false;
            beginEntry$default(this, false, 1, null);
        }
    }

    @Override // android.app.Activity
    protected void onStop() {
        this.returned = true;
        super.onStop();
    }
}
