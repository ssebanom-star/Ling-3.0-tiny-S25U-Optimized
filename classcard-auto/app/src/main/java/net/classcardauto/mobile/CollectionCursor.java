package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.IntRange;
import kotlin.text.StringsKt;

/* compiled from: CollectionCursor.kt */
/* loaded from: classes.dex */
public final class CollectionCursor {
    private boolean indexed;
    private Pair<Integer, String> last;
    private int nextKey;
    private boolean rejected;
    private final LinkedHashMap<Integer, String> observed = new LinkedHashMap<>();
    private final Set<Integer> completed = new LinkedHashSet();
    private final Set<Integer> overlapCompleted = new LinkedHashSet();
    private List<UiNode> page = CollectionsKt.emptyList();
    private List<Integer> pageKeys = CollectionsKt.emptyList();
    private String strategy = "reread";

    private final Integer key(UiNode node) {
        if (this.indexed) {
            return node.getCollectionIndex();
        }
        Iterator<UiNode> it = this.page.iterator();
        int i = 0;
        while (true) {
            if (!it.hasNext()) {
                i = -1;
                break;
            }
            UiNode next = it.next();
            if (Intrinsics.areEqual(next.getText(), node.getText()) && Intrinsics.areEqual(next.getBounds(), node.getBounds())) {
                break;
            }
            i++;
        }
        return (Integer) CollectionsKt.getOrNull(this.pageKeys, i);
    }

    public static /* synthetic */ void observe$default(CollectionCursor collectionCursor, List list, boolean z, int i, Object obj) {
        if ((i & 2) != 0) {
            z = false;
        }
        collectionCursor.observe(list, z);
    }

    private final void observeOverlap(List<UiNode> nodes, boolean moved) {
        List emptyList;
        if (Intrinsics.areEqual(this.strategy, "item-index")) {
            this.last = null;
        }
        List<UiNode> list = nodes;
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(list, 10));
        Iterator<?> it = list.iterator();
        while (it.hasNext()) {
            arrayList.add(((UiNode) it.next()).getText());
        }
        ArrayList arrayList2 = arrayList;
        List<UiNode> list2 = this.page;
        ArrayList arrayList3 = new ArrayList(CollectionsKt.collectionSizeOrDefault(list2, 10));
        Iterator<?> it2 = list2.iterator();
        while (it2.hasNext()) {
            arrayList3.add(((UiNode) it2.next()).getText());
        }
        ArrayList arrayList4 = arrayList3;
        ArrayList<String> arrayList5 = arrayList2;
        if (!(arrayList5 instanceof Collection) || !arrayList5.isEmpty()) {
            Iterator it3 = arrayList5.iterator();
            while (it3.hasNext()) {
                if (StringsKt.isBlank((String) it3.next())) {
                    emptyList = CollectionsKt.emptyList();
                    break;
                }
            }
        }
        if (this.page.isEmpty()) {
            ArrayList arrayList6 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList5, 10));
            for (String str : arrayList5) {
                int i = this.nextKey;
                this.nextKey = i + 1;
                arrayList6.add(Integer.valueOf(i));
            }
            emptyList = arrayList6;
        } else if (!moved && Intrinsics.areEqual(arrayList2, arrayList4)) {
            emptyList = this.pageKeys;
        } else if (moved || !Intrinsics.areEqual(CollectionsKt.take(arrayList5, arrayList4.size()), arrayList4)) {
            IntRange intRange = new IntRange(1, Math.min(arrayList4.size(), arrayList2.size()));
            ArrayList arrayList7 = new ArrayList();
            for (Integer num : intRange) {
                int intValue = num.intValue();
                if (Intrinsics.areEqual(CollectionsKt.takeLast(arrayList4, intValue), CollectionsKt.take(arrayList5, intValue))) {
                    arrayList7.add(num);
                }
            }
            ArrayList arrayList8 = arrayList7;
            if (arrayList8.size() != 1 || ((Number) CollectionsKt.single((List) arrayList8)).intValue() < 2 || CollectionsKt.distinct(CollectionsKt.take(arrayList5, ((Number) CollectionsKt.single((List) arrayList8)).intValue())).size() < 2 || this.pageKeys.size() != arrayList4.size()) {
                ArrayList arrayList9 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList5, 10));
                for (String str2 : arrayList5) {
                    int i2 = this.nextKey;
                    this.nextKey = i2 + 1;
                    arrayList9.add(Integer.valueOf(i2));
                }
                emptyList = arrayList9;
            } else {
                int intValue2 = ((Number) CollectionsKt.single((List) arrayList8)).intValue();
                List takeLast = CollectionsKt.takeLast(this.pageKeys, intValue2);
                List<String> drop = CollectionsKt.drop(arrayList5, intValue2);
                ArrayList arrayList10 = new ArrayList(CollectionsKt.collectionSizeOrDefault(drop, 10));
                for (String str3 : drop) {
                    int i3 = this.nextKey;
                    this.nextKey = i3 + 1;
                    arrayList10.add(Integer.valueOf(i3));
                }
                emptyList = CollectionsKt.plus((Collection) takeLast, (Iterable) arrayList10);
            }
        } else {
            List<Integer> list3 = this.pageKeys;
            List<String> drop2 = CollectionsKt.drop(arrayList5, arrayList4.size());
            ArrayList arrayList11 = new ArrayList(CollectionsKt.collectionSizeOrDefault(drop2, 10));
            for (String str4 : drop2) {
                int i4 = this.nextKey;
                this.nextKey = i4 + 1;
                arrayList11.add(Integer.valueOf(i4));
            }
            emptyList = CollectionsKt.plus((Collection) list3, (Iterable) arrayList11);
        }
        this.page = nodes;
        this.pageKeys = emptyList;
        this.strategy = emptyList.size() == nodes.size() ? "ordered-overlap" : "reread";
    }

    public final void complete(UiNode node) {
        Intrinsics.checkNotNullParameter(node, "node");
        Integer key = key(node);
        if (key != null) {
            int intValue = key.intValue();
            (this.indexed ? this.completed : this.overlapCompleted).add(Integer.valueOf(intValue));
            Pair<Integer, String> pair = this.last;
            if (pair != null) {
                Intrinsics.checkNotNull(pair);
                if (intValue < pair.getFirst().intValue()) {
                    return;
                }
            }
            this.last = TuplesKt.to(Integer.valueOf(intValue), node.getText());
        }
    }

    public final boolean contains(UiNode node) {
        Intrinsics.checkNotNullParameter(node, "node");
        Integer key = key(node);
        if (key != null) {
            if ((this.indexed ? this.completed : this.overlapCompleted).contains(Integer.valueOf(key.intValue()))) {
                return true;
            }
        }
        return false;
    }

    public final boolean getIndexed() {
        return this.indexed;
    }

    public final Pair<Integer, String> getLast() {
        return this.last;
    }

    public final String getStrategy() {
        return this.strategy;
    }

    public final boolean isAnchor(UiNode node) {
        Intrinsics.checkNotNullParameter(node, "node");
        Pair<Integer, String> pair = this.last;
        if (pair != null) {
            Integer key = key(node);
            int intValue = pair.getFirst().intValue();
            if (key != null && key.intValue() == intValue && Intrinsics.areEqual(node.getText(), pair.getSecond())) {
                return true;
            }
        }
        return false;
    }

    public final List<Integer> missing() {
        if (!this.indexed) {
            return CollectionsKt.emptyList();
        }
        Set<Integer> keySet = this.observed.keySet();
        Intrinsics.checkNotNullExpressionValue(keySet, "<get-keys>(...)");
        ArrayList arrayList = new ArrayList();
        for (Object obj : keySet) {
            if (!this.completed.contains((Integer) obj)) {
                arrayList.add(obj);
            }
        }
        return arrayList;
    }

    public final void observe(List<UiNode> nodes, boolean moved) {
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        if (nodes.isEmpty()) {
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (UiNode node : nodes) {
            indices.add(node.getCollectionIndex());
        }
        if (!this.rejected) {
            boolean allIndexed = true;
            for (Integer num : indices) {
                if (num == null || num.intValue() < 0) {
                    allIndexed = false;
                    break;
                }
            }
            if (allIndexed) {
                boolean increasing = true;
                for (int i = 1; i < indices.size(); i++) {
                    if (indices.get(i).intValue() <= indices.get(i - 1).intValue()) {
                        increasing = false;
                        break;
                    }
                }
                if (!increasing) {
                    this.rejected = true;
                    this.indexed = false;
                    observeOverlap(nodes, moved);
                    return;
                }
                for (UiNode node : nodes) {
                    String previous = this.observed.get(node.getCollectionIndex());
                    if (previous != null && !Intrinsics.areEqual(previous, node.getText())) {
                        this.rejected = true;
                        this.indexed = false;
                        observeOverlap(nodes, moved);
                        return;
                    }
                }
                for (UiNode node : nodes) {
                    LinkedHashMap<Integer, String> linkedHashMap = this.observed;
                    Integer collectionIndex = node.getCollectionIndex();
                    Intrinsics.checkNotNull(collectionIndex);
                    linkedHashMap.put(collectionIndex, node.getText());
                }
                if (!Intrinsics.areEqual(this.strategy, "item-index")) {
                    this.last = null;
                }
                this.indexed = true;
                this.strategy = "item-index";
                return;
            }
        }
        this.indexed = false;
        observeOverlap(nodes, moved);
    }

    public final void reset() {
        this.observed.clear();
        this.completed.clear();
        this.overlapCompleted.clear();
        this.rejected = false;
        this.indexed = false;
        this.last = null;
        this.page = CollectionsKt.emptyList();
        this.pageKeys = CollectionsKt.emptyList();
        this.nextKey = 0;
        this.strategy = "reread";
    }
}
