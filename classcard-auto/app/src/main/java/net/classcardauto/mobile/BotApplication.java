package net.classcardauto.mobile;

import android.app.Application;
import android.icu.lang.UCharacter;
import java.io.File;
import kotlin.Metadata;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Storage.kt */
/* loaded from: classes.dex */
public final class BotApplication extends Application {
    /* JADX INFO: Access modifiers changed from: private */
    public static final String onCreate$lambda$0(String it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return UCharacter.foldCase(it, true);
    }

    @Override // android.app.Application
    public void onCreate() {
        super.onCreate();
        RetentionPolicy retentionPolicy = RetentionPolicy.INSTANCE;
        File filesDir = getFilesDir();
        Intrinsics.checkNotNullExpressionValue(filesDir, "getFilesDir(...)");
        retentionPolicy.removeLegacyData(filesDir);
        FailureLog.scrub(filesDir);
        BotApplication botApplication = this;
        Certification.INSTANCE.initialize(botApplication);
        AppPassword.INSTANCE.lock();
        UsageConsent.INSTANCE.initialize(botApplication);
        TextRules.INSTANCE.setFold(new Function1() { // from class: net.classcardauto.mobile.BotApplication$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                String onCreate$lambda$0;
                onCreate$lambda$0 = BotApplication.onCreate$lambda$0((String) obj);
                return onCreate$lambda$0;
            }
        });
    }
}
