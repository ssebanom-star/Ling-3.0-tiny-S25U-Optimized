package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: InlineCollection.kt */
/* loaded from: classes.dex */
public final class InlineCollectionKt {
    public static final List<InlinePair> inlinePairs(List<UiNode> fronts, List<UiNode> nodes, Bounds viewport) {
        int i;
        Intrinsics.checkNotNullParameter(fronts, "fronts");
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        Intrinsics.checkNotNullParameter(viewport, "viewport");
        ArrayList arrayList = new ArrayList();
        for (UiNode uiNode : (List<UiNode>) CollectionsKt.sortedWith(fronts, new Comparator() { // from class: net.classcardauto.mobile.InlineCollectionKt$inlinePairs$$inlined$sortedBy$1
            /* JADX WARN: Multi-variable type inference failed */
            @Override // java.util.Comparator
            public final int compare(Object t, Object t2) {
                return ComparisonsKt.compareValues(Integer.valueOf(((UiNode) t).getBounds().getTop()), Integer.valueOf(((UiNode) t2).getBounds().getTop()));
            }
        })) {
            if (inlinePairs$inside(uiNode.getBounds(), viewport) && uiNode.getBounds().getTop() >= viewport.getTop() + 16 && uiNode.getBounds().getBottom() <= viewport.getBottom() - 16 && uiNode.getRowBounds() == null) {
                return null;
            }
            if (inlinePairs$safe(viewport, uiNode)) {
                List<UiNode> list = nodes;
                if ((list instanceof Collection) && list.isEmpty()) {
                    i = 0;
                } else {
                    i = 0;
                    for (UiNode uiNode2 : list) {
                        if ((Intrinsics.areEqual(uiNode2.getId(), "txt_front") && Intrinsics.areEqual(uiNode2.getRowBounds(), uiNode.getRowBounds())) && (i = i + 1) < 0) {
                            CollectionsKt.throwCountOverflow();
                        }
                    }
                }
                if (i != 1) {
                    return null;
                }
                ArrayList arrayList2 = new ArrayList();
                for (Object obj : list) {
                    UiNode uiNode3 = (UiNode) obj;
                    if (Intrinsics.areEqual(uiNode3.getId(), "txt_inline_back") && Intrinsics.areEqual(uiNode3.getRowBounds(), uiNode.getRowBounds())) {
                        arrayList2.add(obj);
                    }
                }
                ArrayList arrayList3 = arrayList2;
                if (arrayList3.size() != 1) {
                    return null;
                }
                UiNode uiNode4 = (UiNode) CollectionsKt.single((List) arrayList3);
                if (inlinePairs$safe(viewport, uiNode4)) {
                    arrayList.add(new InlinePair(uiNode, uiNode4));
                }
            }
        }
        return arrayList;
    }

    private static final boolean inlinePairs$inside(Bounds bounds, Bounds bounds2) {
        return bounds.getValid() && bounds2.getValid() && bounds.getLeft() >= bounds2.getLeft() && bounds.getRight() <= bounds2.getRight() && bounds.getTop() >= bounds2.getTop() && bounds.getBottom() <= bounds2.getBottom();
    }

    private static final boolean inlinePairs$safe(Bounds bounds, UiNode uiNode) {
        Bounds rowBounds;
        return inlinePairs$inside(uiNode.getBounds(), bounds) && uiNode.getBounds().getTop() >= bounds.getTop() + 16 && uiNode.getBounds().getBottom() <= bounds.getBottom() + (-16) && (rowBounds = uiNode.getRowBounds()) != null && inlinePairs$inside(rowBounds, bounds) && inlinePairs$inside(uiNode.getBounds(), rowBounds);
    }
}
