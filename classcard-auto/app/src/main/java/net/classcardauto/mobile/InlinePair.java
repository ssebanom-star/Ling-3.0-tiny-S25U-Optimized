package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: InlineCollection.kt */
/* loaded from: classes.dex */
public final /* data */ class InlinePair {
    private final UiNode front;
    private final UiNode meaning;

    public InlinePair(UiNode front, UiNode meaning) {
        Intrinsics.checkNotNullParameter(front, "front");
        Intrinsics.checkNotNullParameter(meaning, "meaning");
        this.front = front;
        this.meaning = meaning;
    }

    public static /* synthetic */ InlinePair copy$default(InlinePair inlinePair, UiNode uiNode, UiNode uiNode2, int i, Object obj) {
        if ((i & 1) != 0) {
            uiNode = inlinePair.front;
        }
        if ((i & 2) != 0) {
            uiNode2 = inlinePair.meaning;
        }
        return inlinePair.copy(uiNode, uiNode2);
    }



    public final InlinePair copy(UiNode front, UiNode meaning) {
        Intrinsics.checkNotNullParameter(front, "front");
        Intrinsics.checkNotNullParameter(meaning, "meaning");
        return new InlinePair(front, meaning);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InlinePair)) {
            return false;
        }
        InlinePair inlinePair = (InlinePair) other;
        return Intrinsics.areEqual(this.front, inlinePair.front) && Intrinsics.areEqual(this.meaning, inlinePair.meaning);
    }

    public final UiNode getFront() {
        return this.front;
    }

    public final UiNode getMeaning() {
        return this.meaning;
    }

    public int hashCode() {
        return (this.front.hashCode() * 31) + this.meaning.hashCode();
    }

    public String toString() {
        return "InlinePair(front=" + this.front + ", meaning=" + this.meaning + ")";
    }
}
