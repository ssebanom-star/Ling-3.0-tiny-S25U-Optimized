package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: OcrLayout.kt */
/* loaded from: classes.dex */
public final /* data */ class CtcRead {
    private final double confidence;
    private final String text;

    public CtcRead(String text, double d) {
        Intrinsics.checkNotNullParameter(text, "text");
        this.text = text;
        this.confidence = d;
    }

    public static /* synthetic */ CtcRead copy$default(CtcRead ctcRead, String str, double d, int i, Object obj) {
        if ((i & 1) != 0) {
            str = ctcRead.text;
        }
        if ((i & 2) != 0) {
            d = ctcRead.confidence;
        }
        return ctcRead.copy(str, d);
    }



    public final CtcRead copy(String text, double confidence) {
        Intrinsics.checkNotNullParameter(text, "text");
        return new CtcRead(text, confidence);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CtcRead)) {
            return false;
        }
        CtcRead ctcRead = (CtcRead) other;
        return Intrinsics.areEqual(this.text, ctcRead.text) && Double.compare(this.confidence, ctcRead.confidence) == 0;
    }

    public final double getConfidence() {
        return this.confidence;
    }

    public final String getText() {
        return this.text;
    }

    public int hashCode() {
        return (this.text.hashCode() * 31) + Double.hashCode(this.confidence);
    }

    public String toString() {
        return "CtcRead(text=" + this.text + ", confidence=" + this.confidence + ")";
    }
}
