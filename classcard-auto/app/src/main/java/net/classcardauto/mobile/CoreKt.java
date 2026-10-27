package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class CoreKt {

    /* compiled from: Core.kt */
    @Metadata(k = 3, mv = {2, 1, 0}, xi = 48)
    /* loaded from: classes.dex */
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] iArr = new int[CurrentEntry.values().length];
            try {
                iArr[CurrentEntry.SET.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[CurrentEntry.PREVIEW.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            $EnumSwitchMapping$0 = iArr;
        }
    }

    public static final Card collectedCard(UiNode front, UiNode back) {
        Intrinsics.checkNotNullParameter(front, "front");
        Intrinsics.checkNotNullParameter(back, "back");
        return new Card(front.getText(), back.getText(), back.getTextAlternatives(), front.getTextAlternatives());
    }

    public static final UiNode collectedText(UiNode node, String recognized, boolean z) {
        String str;
        Intrinsics.checkNotNullParameter(node, "node");
        Intrinsics.checkNotNullParameter(recognized, "recognized");
        if (z) {
            str = recognized;
        } else {
            String text = node.getText();
            if (StringsKt.isBlank(text)) {
                text = recognized;
            }
            str = text;
        }
        String obj = StringsKt.trim((CharSequence) str).toString();
        if (StringsKt.isBlank(obj)) {
            throw new IllegalArgumentException("수집할 글자가 없습니다.".toString());
        }
        List plus = CollectionsKt.plus((Collection) CollectionsKt.listOf((Object[]) new String[]{node.getText(), recognized}), (Iterable) node.getTextAlternatives());
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(plus, 10));
        Iterator it = plus.iterator();
        while (it.hasNext()) {
            arrayList.add(StringsKt.trim((CharSequence) it.next()).toString());
        }
        ArrayList arrayList2 = new ArrayList();
        for (Object obj2 : arrayList) {
            String str2 = (String) obj2;
            if (!StringsKt.isBlank(str2) && !Intrinsics.areEqual(TextRules.INSTANCE.normalize(str2), TextRules.INSTANCE.normalize(obj))) {
                arrayList2.add(obj2);
            }
        }
        HashSet hashSet = new HashSet();
        ArrayList arrayList3 = new ArrayList();
        for (Object obj3 : arrayList2) {
            if (hashSet.add(TextRules.INSTANCE.normalize((String) obj3))) {
                arrayList3.add(obj3);
            }
        }
        return UiNode.copy$default(node, null, obj, null, null, false, false, false, false, null, false, null, arrayList3, 2045, null);
    }

    public static final CurrentEntry currentEntry(List<UiNode> nodes) {
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        List<UiNode> list = nodes;
        boolean z = list instanceof Collection;
        if (!z || !list.isEmpty()) {
            Iterator<?> it = list.iterator();
            while (it.hasNext()) {
                if (Intrinsics.areEqual(((UiNode) it.next()).getId(), "txt_current_num")) {
                    return CurrentEntry.ACTIVE_TEST;
                }
            }
        }
        if (!z || !list.isEmpty()) {
            Iterator<?> it2 = list.iterator();
            while (true) {
                if (!it2.hasNext()) {
                    break;
                }
                UiNode uiNode = (UiNode) it2.next();
                if (Intrinsics.areEqual(uiNode.getId(), "set_name") && !StringsKt.isBlank(uiNode.getText())) {
                    if (!z || !list.isEmpty()) {
                        Iterator<?> it3 = list.iterator();
                        while (it3.hasNext()) {
                            if (Intrinsics.areEqual(((UiNode) it3.next()).getId(), "recycler_view")) {
                                return CurrentEntry.SET;
                            }
                        }
                    }
                }
            }
        }
        if (!z || !list.isEmpty()) {
            Iterator<?> it4 = list.iterator();
            while (true) {
                if (!it4.hasNext()) {
                    break;
                }
                if (Intrinsics.areEqual(((UiNode) it4.next()).getId(), "pager")) {
                    if (!z || !list.isEmpty()) {
                        Iterator<?> it5 = list.iterator();
                        while (it5.hasNext()) {
                            if (SetsKt.setOf((Object[]) new String[]{"txt_title", "btn_test_start"}).contains(((UiNode) it5.next()).getId())) {
                                return CurrentEntry.PREVIEW;
                            }
                        }
                    }
                }
            }
        }
        return CurrentEntry.UNKNOWN;
    }

    public static final SwipePath safeSwipePath(Bounds card, List<UiNode> nodes) {
        Integer num;
        Intrinsics.checkNotNullParameter(card, "card");
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        if (!card.getValid()) {
            throw new IllegalArgumentException("카드 위치가 유효하지 않습니다.".toString());
        }
        ArrayList arrayList = new ArrayList();
        for (Object obj : nodes) {
            UiNode uiNode = (UiNode) obj;
            if (!StringsKt.isBlank(uiNode.getText()) && uiNode.getBounds().getTop() >= card.getTop() && uiNode.getBounds().getBottom() <= card.getBottom() && uiNode.getBounds().getLeft() >= card.getLeft() && uiNode.getBounds().getRight() <= card.getRight()) {
                arrayList.add(obj);
            }
        }
        Iterator it = arrayList.iterator();
        if (it.hasNext()) {
            Integer valueOf = Integer.valueOf(((UiNode) it.next()).getBounds().getBottom());
            while (it.hasNext()) {
                Integer valueOf2 = Integer.valueOf(((UiNode) it.next()).getBounds().getBottom());
                if (valueOf.compareTo(valueOf2) < 0) {
                    valueOf = valueOf2;
                }
            }
            num = valueOf;
        } else {
            num = null;
        }
        Integer num2 = num;
        if (card.getBottom() - (num2 != null ? num2.intValue() : card.getTop()) >= 12) {
            return new SwipePath(card.getLeft() + (card.getWidth() * 0.88f), card.getLeft() + (card.getWidth() * 0.12f), ((num2 != null ? num2.intValue() : card.getTop()) + card.getBottom()) / 2.0f);
        }
        throw new IllegalArgumentException("선택지 밖의 카드 여백을 확인할 수 없습니다.".toString());
    }

    public static final void validateEntryStructure(List<UiNode> nodes, CurrentEntry entry) {
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        Intrinsics.checkNotNullParameter(entry, "entry");
        int i = WhenMappings.$EnumSwitchMapping$0[entry.ordinal()];
        if (i == 1) {
            List<UiNode> list = nodes;
            if (!(list instanceof Collection) || !list.isEmpty()) {
                Iterator<?> it = list.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    UiNode uiNode = (UiNode) it.next();
                    if (Intrinsics.areEqual(uiNode.getId(), "set_name") && uiNode.getBounds().getValid() && !StringsKt.isBlank(uiNode.getText())) {
                        if (validateEntryStructure$visible(nodes, "recycler_view")) {
                            return;
                        }
                    }
                }
            }
        } else if (i == 2 && validateEntryStructure$visible(nodes, "pager")) {
            if (validateEntryStructure$visible(nodes, "txt_title")) {
                return;
            }
            List<UiNode> list2 = nodes;
            if (!(list2 instanceof Collection) || !list2.isEmpty()) {
                for (UiNode uiNode2 : list2) {
                    if (Intrinsics.areEqual(uiNode2.getId(), "btn_test_start") && uiNode2.getBounds().getValid() && uiNode2.getEnabled()) {
                        return;
                    }
                }
            }
        }
        throw new IllegalArgumentException("클래스카드 화면 구조를 확인할 수 없습니다. 단어 목록 또는 테스트 시작 전 화면을 열어 주세요.".toString());
    }

    private static final boolean validateEntryStructure$visible(List<UiNode> list, String str) {
        List<UiNode> list2 = list;
        if ((list2 instanceof Collection) && list2.isEmpty()) {
            return false;
        }
        for (UiNode uiNode : list2) {
            if (Intrinsics.areEqual(uiNode.getId(), str) && uiNode.getBounds().getValid()) {
                return true;
            }
        }
        return false;
    }
}
