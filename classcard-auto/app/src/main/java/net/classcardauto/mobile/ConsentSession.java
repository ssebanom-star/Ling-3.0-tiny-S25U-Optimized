package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: UsageConsent.kt */
/* loaded from: classes.dex */
public final class ConsentSession {
    private int step;

    public final void agreeToNotice() {
        if (this.step != 0) {
            throw new IllegalStateException("Check failed.");
        }
        this.step = 1;
    }

    public final void confirmUse() {
        if (this.step != 1) {
            throw new IllegalStateException("Check failed.");
        }
        this.step = 2;
    }

    public final boolean getAllowed() {
        return this.step == 2;
    }

    public final int getStep() {
        return this.step;
    }

    public final void reset() {
        this.step = 0;
    }

    public final void restore(ConsentRecord record, String currentDigest) {
        Intrinsics.checkNotNullParameter(currentDigest, "currentDigest");
        reset();
        if (record == null || !record.validFor(currentDigest)) {
            return;
        }
        this.step = 2;
    }
}
