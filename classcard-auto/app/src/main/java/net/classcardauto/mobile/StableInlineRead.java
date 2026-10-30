package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: InlineCollection.kt */
/* loaded from: classes.dex */
public final class StableInlineRead {
    private List<InlinePair> previous;
    private long since;

    public final boolean ready(List<InlinePair> pairs) {
        Intrinsics.checkNotNullParameter(pairs, "pairs");
        long now = android.os.SystemClock.elapsedRealtime();
        boolean areEqual = Intrinsics.areEqual(this.previous, pairs);
        if (!areEqual) {
            this.since = now;
        }
        this.previous = CollectionsKt.toList(pairs);
        // same rule as StableCollectionRead: unchanged for a minimum time, whatever the polling rate
        return areEqual && now - this.since >= StableCollectionRead.MIN_SPAN_MS;
    }

    public final void reset() {
        this.previous = null;
    }
}
