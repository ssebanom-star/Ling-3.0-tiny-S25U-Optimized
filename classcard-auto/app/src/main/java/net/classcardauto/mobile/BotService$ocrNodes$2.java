package net.classcardauto.mobile;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import net.classcardauto.mobile.OcrReader;

/* compiled from: BotService.kt */
/* loaded from: classes.dex */
public final class BotService$ocrNodes$2 implements AccessibilityService.TakeScreenshotCallback {
    final /* synthetic */ Function1<Object, Unit> $after;
    final /* synthetic */ List<UiNode> $requested;
    final /* synthetic */ BotService this$0;

    /* JADX INFO: Access modifiers changed from: package-private */
    /* JADX WARN: Multi-variable type inference failed */
    public BotService$ocrNodes$2(BotService botService, Function1<Object, Unit> function1, List<UiNode> list) {
        this.this$0 = botService;
        this.$after = function1;
        this.$requested = list;
    }

    public static final void onSuccess$lambda$4(BotService$ocrNodes$2 self, Bitmap bitmap, final BotService botService, List list, final Function1 function1) {
        Object outcome;
        try {
            ArrayList arrayList = new ArrayList();
            for (Object item : list) {
                Bounds bounds = ((UiNode) item).getBounds();
                if (!bounds.getValid() || bounds.getLeft() < 0 || bounds.getTop() < 0 || bounds.getRight() > bitmap.getWidth() || bounds.getBottom() > bitmap.getHeight()) {
                    throw new IllegalArgumentException("카드 이미지 위치가 화면 밖입니다.".toString());
                }
                Bitmap createBitmap = Bitmap.createBitmap(bitmap, bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight());
                Intrinsics.checkNotNullExpressionValue(createBitmap, "createBitmap(...)");
                try {
                    arrayList.add(botService.getOcrReader().readDetailed(createBitmap));
                } finally {
                    if (createBitmap != bitmap) {
                        createBitmap.recycle();
                    }
                }
            }
            outcome = ResultCompat.success(arrayList);
        } catch (Throwable th) {
            outcome = ResultCompat.failure(th);
        } finally {
            bitmap.recycle();
        }
        final Object delivered = outcome;
        botService.getHandler().post(new Runnable() {
            @Override
            public final void run() {
                BotService.ocrNodes$deliver(botService, function1, delivered);
            }
        });
    }

    @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
    public void onFailure(int errorCode) {
        BotService botService = this.this$0;
        Function1<Object, Unit> function1 = this.$after;
        BotService.ocrNodes$deliver(botService, function1, ResultCompat.failure(new IllegalStateException("화면 이미지 캡처 실패: " + errorCode)));
    }

    @Override // android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
    public void onSuccess(AccessibilityService.ScreenshotResult result) {
        Intrinsics.checkNotNullParameter(result, "result");
        HardwareBuffer hardwareBuffer = result.getHardwareBuffer();
        Intrinsics.checkNotNullExpressionValue(hardwareBuffer, "getHardwareBuffer(...)");
        Bitmap wrapHardwareBuffer = Bitmap.wrapHardwareBuffer(hardwareBuffer, result.getColorSpace());
        final Bitmap copy = wrapHardwareBuffer != null ? wrapHardwareBuffer.copy(Bitmap.Config.ARGB_8888, false) : null;
        hardwareBuffer.close();
        if (copy == null) {
            BotService botService = this.this$0;
            Function1<Object, Unit> function1 = this.$after;
            BotService.ocrNodes$deliver(botService, function1, ResultCompat.failure(new IllegalStateException("이미지를 복사하지 못했습니다.")));
            return;
        }
        ExecutorService worker = this.this$0.getWorker();
        final BotService botService2 = this.this$0;
        final List<UiNode> list = this.$requested;
        final Function1<Object, Unit> function12 = this.$after;
        worker.execute(new Runnable() { // from class: net.classcardauto.mobile.BotService$ocrNodes$2$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                BotService$ocrNodes$2.onSuccess$lambda$4(BotService$ocrNodes$2.this, copy, botService2, list, function12);
            }
        });
    }
}
