package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class AnswerResolutionException extends IllegalArgumentException {
    private final AnswerFailure failure;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public AnswerResolutionException(AnswerFailure failure) {
        super(failure.getExplanation());
        Intrinsics.checkNotNullParameter(failure, "failure");
        this.failure = failure;
    }

    public final AnswerFailure getFailure() {
        return this.failure;
    }
}
