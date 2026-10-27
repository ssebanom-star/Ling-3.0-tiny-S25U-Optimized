package net.classcardauto.mobile;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/* compiled from: BotService.kt */
/* loaded from: classes.dex */
public final class BotService$gesture$3$accepted$1 extends AccessibilityService.GestureResultCallback {
    final /* synthetic */ Function0<Unit> $after;
    final /* synthetic */ int $epoch;
    final /* synthetic */ long $token;
    final /* synthetic */ BotService this$0;

    /* JADX INFO: Access modifiers changed from: package-private */
    public BotService$gesture$3$accepted$1(BotService botService, long j, int i, Function0<Unit> function0) {
        this.this$0 = botService;
        this.$token = j;
        this.$epoch = i;
        this.$after = function0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit onCancelled$lambda$1(BotService botService, int i) {
        if (botService.getEngine().getActive() && botService.getEngine().getEpoch() == i) {
            BotEngine.log$default(botService.getEngine(), "gesture_cancelled", null, 2, null);
        }
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit onCompleted$lambda$0(BotService botService, int i, Function0 function0) {
        if (botService.getEngine().getActive() && botService.getEngine().getEpoch() == i) {
            function0.invoke();
        }
        return Unit.INSTANCE;
    }

    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
    public void onCancelled(GestureDescription gestureDescription) {
        final BotService botService = this.this$0;
        long j = this.$token;
        final int i = this.$epoch;
        botService.finishGesture(j, 150L, new Function0() { // from class: net.classcardauto.mobile.BotService$gesture$3$accepted$1$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit onCancelled$lambda$1;
                onCancelled$lambda$1 = BotService$gesture$3$accepted$1.onCancelled$lambda$1(botService, i);
                return onCancelled$lambda$1;
            }
        });
    }

    @Override // android.accessibilityservice.AccessibilityService.GestureResultCallback
    public void onCompleted(GestureDescription gestureDescription) {
        final BotService botService = this.this$0;
        long j = this.$token;
        final int i = this.$epoch;
        final Function0<Unit> function0 = this.$after;
        botService.finishGesture(j, 45L, new Function0() { // from class: net.classcardauto.mobile.BotService$gesture$3$accepted$1$$ExternalSyntheticLambda1
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit onCompleted$lambda$0;
                onCompleted$lambda$0 = BotService$gesture$3$accepted$1.onCompleted$lambda$0(botService, i, function0);
                return onCompleted$lambda$0;
            }
        });
    }
}
