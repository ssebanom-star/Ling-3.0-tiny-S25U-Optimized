package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: InlineCollection.kt */
/* loaded from: classes.dex */
public final class StableInlineRead {
    private List<InlinePair> previous;

    public final boolean ready(List<InlinePair> pairs) {
        Intrinsics.checkNotNullParameter(pairs, "pairs");
        boolean areEqual = Intrinsics.areEqual(this.previous, pairs);
        this.previous = CollectionsKt.toList(pairs);
        return areEqual;
    }

    public final void reset() {
        this.previous = null;
    }
}
