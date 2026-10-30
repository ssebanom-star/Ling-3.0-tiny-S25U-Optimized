package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.Triple;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class StableCollectionRead {
    /** A read counts as settled only after it stayed the same for this long (not just for two polls in a row). */
    public static final long MIN_SPAN_MS = 90;

    private Triple<String, String, Bounds> last;
    private long since;

    public final boolean ready(String side, UiNode node) {
        Intrinsics.checkNotNullParameter(side, "side");
        Intrinsics.checkNotNullParameter(node, "node");
        long now = android.os.SystemClock.elapsedRealtime();
        Triple<String, String, Bounds> triple = new Triple<>(side, node.getText(), node.getBounds());
        boolean areEqual = Intrinsics.areEqual(triple, this.last);
        if (!areEqual) {
            this.since = now;
        }
        this.last = triple;
        return areEqual && now - this.since >= MIN_SPAN_MS;
    }

    public final void reset() {
        this.last = null;
    }
}
