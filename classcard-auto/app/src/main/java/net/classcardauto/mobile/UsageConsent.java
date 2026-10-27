package net.classcardauto.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.util.Arrays;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.collections.ArraysKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;

/* compiled from: UsageConsent.kt */
/* loaded from: classes.dex */
public final class UsageConsent {
    public static final String POLICY_VERSION = "2026-10-26";
    private static SharedPreferences prefs;
    public static final UsageConsent INSTANCE = new UsageConsent();
    private static final ConsentSession session = new ConsentSession();

    /* renamed from: policyDigest$delegate, reason: from kotlin metadata */
    private static final Lazy policyDigest = LazyKt.lazy(new Function0() { // from class: net.classcardauto.mobile.UsageConsent$$ExternalSyntheticLambda1
        @Override // kotlin.jvm.functions.Function0
        public final Object invoke() {
            String policyDigest_delegate$lambda$1;
            policyDigest_delegate$lambda$1 = UsageConsent.policyDigest_delegate$lambda$1();
            return policyDigest_delegate$lambda$1;
        }
    });
    private static final String permissionDisclosure = "이 앱은 Android 접근성 서비스를 사용합니다.\n\n동의 후에는 클래스카드에서 시작 가능한 화면인지 확인합니다. 사용자가 시작을 누르면 현재 세트의 단어·뜻과 연습 테스트의 문항·보기를 읽고, 수집된 자료를 바탕으로 답 선택·스크롤·화면 전환을 자동으로 수행합니다.\n\n이미지 OCR이 필요한 경우 화면을 일시적으로 캡처해 지정한 단어·뜻 영역을 기기 안에서 처리합니다. 캡처에는 당시 화면의 다른 표시가 함께 포함될 수 있습니다. 캡처 이미지를 파일로 저장하거나 서버로 전송하지 않습니다.\n\n수집한 단어·뜻은 실행 중 메모리에만 두고 완료·중지·오류 때 삭제합니다. 다른 앱의 본문은 읽어 처리하지 않습니다. 자동 실행 중 화면 위 중지 버튼으로 멈출 수 있고 Android 접근성 설정에서 서비스를 끌 수 있습니다.\n\n위 접근성 사용과 자동 조작을 이해하고 동의할 때만 아래 버튼을 누르세요.";
    private static final String notice = "본 프로그램은 독립적으로 개발된 비공식 소프트웨어이며, 클래스카드를 비롯한 제3자 시험 서비스와 제휴하거나 공식적으로 승인받은 제품이 아닙니다.\n\n본 프로그램은 외부 교육 서비스의 시험을 자동으로 응시하거나 제출하지 않습니다. 학교 평가의 대리 수행, 무단 문제 수집 또는 타인의 저작권을 침해하는 용도로 사용할 수 없습니다.\n\n사용자가 불러오는 문항은 직접 제작했거나 적법한 이용 권한을 확보한 자료여야 합니다.\n\n문항 및 이미지의 처리는 기기 내부에서 수행하도록 설계되며, 사용되는 외부 SDK의 데이터 처리 사항은 개인정보처리방침에 별도로 안내합니다.";

    private UsageConsent() {
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final String policyDigest_delegate$lambda$1() {
        MessageDigest messageDigest = Sneaky.sha256();
        byte[] bytes = ("2026-10-26\n" + notice + "\n" + permissionDisclosure).getBytes(Charsets.UTF_8);
        Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
        byte[] digest = messageDigest.digest(bytes);
        Intrinsics.checkNotNullExpressionValue(digest, "digest(...)");
        return ArraysKt.joinToString(digest, (CharSequence) "", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", new Function1() { // from class: net.classcardauto.mobile.UsageConsent$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                CharSequence policyDigest_delegate$lambda$1$lambda$0;
                policyDigest_delegate$lambda$1$lambda$0 = UsageConsent.policyDigest_delegate$lambda$1$lambda$0(((Byte) obj).byteValue());
                return policyDigest_delegate$lambda$1$lambda$0;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final CharSequence policyDigest_delegate$lambda$1$lambda$0(byte b) {
        String format = String.format("%02x", Arrays.copyOf(new Object[]{Byte.valueOf(b)}, 1));
        Intrinsics.checkNotNullExpressionValue(format, "format(...)");
        return format;
    }

    public final void confirmUse() {
        ConsentSession consentSession = session;
        if (consentSession.getStep() != 1 || !AppPassword.INSTANCE.getAllowed() || !Certification.INSTANCE.getAccessAllowed()) {
            throw new IllegalStateException("Check failed.");
        }
        SharedPreferences sharedPreferences = prefs;
        SharedPreferences sharedPreferences2 = null;
        if (sharedPreferences == null) {
            Intrinsics.throwUninitializedPropertyAccessException("prefs");
            sharedPreferences = null;
        }
        if (sharedPreferences.edit().putString("policy_digest", getPolicyDigest()).putString("policy_version", POLICY_VERSION).putLong("accepted_at", System.currentTimeMillis()).putInt("steps", 2).commit()) {
            consentSession.confirmUse();
            return;
        }
        SharedPreferences sharedPreferences3 = prefs;
        if (sharedPreferences3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("prefs");
        } else {
            sharedPreferences2 = sharedPreferences3;
        }
        if (!sharedPreferences2.edit().clear().commit()) {
            throw new IllegalStateException("동의 기록 저장에 실패했고 임시 기록도 지우지 못했습니다.".toString());
        }
        throw new IllegalStateException("동의 기록을 저장하지 못했습니다. 다시 시도해주세요.".toString());
    }

    public final boolean getAllowed() {
        return CertificationPolicy.INSTANCE.useAllowed(Certification.INSTANCE.getRequired(), Certification.INSTANCE.getValid(), session.getAllowed(), AppPassword.INSTANCE.getAllowed());
    }

    public final String getNotice() {
        return notice;
    }

    public final String getPermissionDisclosure() {
        return permissionDisclosure;
    }

    public final String getPolicyDigest() {
        return (String) policyDigest.getValue();
    }

    public final String getRequiredMessage() {
        return !AppPassword.INSTANCE.getAllowed() ? "앱에서 비밀번호를 입력해주세요." : "앱에서 사용 안내에 두 번 동의해주세요.";
    }

    public final ConsentSession getSession() {
        return session;
    }

    public final void initialize(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        prefs = context.getSharedPreferences("usage_consent", 0);
        restore();
    }

    public final ConsentRecord record() {
        SharedPreferences sharedPreferences = prefs;
        if (sharedPreferences == null) {
            return null;
        }
        try {
            if (!Intrinsics.areEqual(sharedPreferences.getString("policy_version", ""), POLICY_VERSION)) {
                throw new IllegalStateException("Check failed.");
            }
            String string = sharedPreferences.getString("policy_digest", "");
            if (string == null) {
                string = "";
            }
            ConsentRecord consentRecord = new ConsentRecord(string, sharedPreferences.getLong("accepted_at", 0L), sharedPreferences.getInt("steps", 0));
            if (!consentRecord.validFor(getPolicyDigest())) {
                return null;
            }
            return consentRecord;
        } catch (Throwable th) {
            return null;
        }
    }

    public final void restore() {
        session.restore(record(), getPolicyDigest());
    }

    public final void revoke() {
        session.reset();
        SharedPreferences sharedPreferences = prefs;
        if (sharedPreferences != null) {
            if (sharedPreferences == null) {
                Intrinsics.throwUninitializedPropertyAccessException("prefs");
                sharedPreferences = null;
            }
            if (!sharedPreferences.edit().clear().commit()) {
                throw new IllegalStateException("동의 기록 삭제 실패".toString());
            }
        }
    }
}
