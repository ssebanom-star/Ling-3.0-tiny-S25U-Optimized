package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.Triple;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class StableCollectionRead {
    private Triple<String, String, Bounds> last;

    public final boolean ready(String side, UiNode node) {
        Intrinsics.checkNotNullParameter(side, "side");
        Intrinsics.checkNotNullParameter(node, "node");
        Triple<String, String, Bounds> triple = new Triple<>(side, node.getText(), node.getBounds());
        boolean areEqual = Intrinsics.areEqual(triple, this.last);
        this.last = triple;
        return areEqual;
    }

    public final void reset() {
        this.last = null;
    }
}
