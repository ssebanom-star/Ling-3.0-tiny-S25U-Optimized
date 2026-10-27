package net.classcardauto.mobile;

import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: InlineCollection.kt */
/* loaded from: classes.dex */
public final class CollectionDisplayMode {
    private boolean forcedFlip;
    private boolean inline;

    public final void fallbackToFlip() {
        this.forcedFlip = true;
        this.inline = false;
    }

    public final boolean getInline() {
        return this.inline;
    }

    public final void observe(List<UiNode> nodes) {
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        if (this.forcedFlip) {
            return;
        }
        List<UiNode> list = nodes;
        if ((list instanceof Collection) && list.isEmpty()) {
            return;
        }
        for (UiNode uiNode : list) {
            if (Intrinsics.areEqual(uiNode.getId(), "switch_show_back") || Intrinsics.areEqual(uiNode.getId(), "txt_inline_back")) {
                this.inline = true;
                return;
            }
        }
    }

    public final void reset() {
        this.forcedFlip = false;
        this.inline = false;
    }
}
