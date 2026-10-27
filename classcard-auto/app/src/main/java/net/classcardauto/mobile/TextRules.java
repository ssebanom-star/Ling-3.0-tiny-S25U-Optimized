package net.classcardauto.mobile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.MatchResult;
import kotlin.text.Regex;
import kotlin.text.RegexOption;
import kotlin.text.StringsKt;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class TextRules {
    public static final TextRules INSTANCE = new TextRules();
    private static Function1<? super String, String> fold = new Function1() { // from class: net.classcardauto.mobile.TextRules$$ExternalSyntheticLambda0
        @Override // kotlin.jvm.functions.Function1
        public final Object invoke(Object obj) {
            String fold$lambda$0;
            fold$lambda$0 = TextRules.fold$lambda$0((String) obj);
            return fold$lambda$0;
        }
    };
    private static final Regex pos = new Regex("\\[(?:명|동|형|부|전|접|대|감|관)(?:사)?]");
    private static final Regex spaces = new Regex("\\s+");

    private TextRules() {
    }

    private final String base(String s) {
        Function1<? super String, String> function1 = fold;
        String normalize = Normalizer.normalize(s, Normalizer.Form.NFKC);
        Intrinsics.checkNotNullExpressionValue(normalize, "normalize(...)");
        return function1.invoke(normalize);
    }

    private final int definitionStrength(String text, String meaning) {
        String normalize = normalize(text);
        if (Intrinsics.areEqual(normalize, normalize(meaning))) {
            return 3;
        }
        Set<String> parts = parts(meaning);
        if (Intrinsics.areEqual(parts(text), parts)) {
            return 3;
        }
        Set<String> parts2 = parts(new Regex("[\\r\\n]+").replace(meaning, " "));
        if (Intrinsics.areEqual(parts(text), parts2)) {
            return 3;
        }
        return (parts.contains(normalize) || parts2.contains(normalize)) ? 2 : 0;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final String fold$lambda$0(String it) {
        Intrinsics.checkNotNullParameter(it, "it");
        Locale ROOT = Locale.ROOT;
        Intrinsics.checkNotNullExpressionValue(ROOT, "ROOT");
        String lowerCase = it.toLowerCase(ROOT);
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)");
        return lowerCase;
    }

    public final boolean dayMatches(String title, int day) {
        List<String> groupValues;
        String str;
        Integer intOrNull;
        Intrinsics.checkNotNullParameter(title, "title");
        MatchResult find$default = new Regex("\\bDAY\\s*0*(\\d+)\\b", RegexOption.IGNORE_CASE).find(title, 0);
        return (find$default == null || (groupValues = find$default.getGroupValues()) == null || (str = groupValues.get(1)) == null || (intOrNull = StringsKt.toIntOrNull(str)) == null || intOrNull.intValue() != day) ? false : true;
    }

    public final Function1<String, String> getFold() {
        return (Function1<String, String>) (Function1) fold;
    }

    public final String normalize(String s) {
        Intrinsics.checkNotNullParameter(s, "s");
        return StringsKt.trim((CharSequence) spaces.replace(pos.replace(base(s), ""), " ")).toString();
    }

    public final Set<String> parts(String s) {
        Intrinsics.checkNotNullParameter(s, "s");
        List<String> split = new Regex("[,;\\r\\n]|\\s*/\\s*").split(new Regex("(?<![\\p{L}\\p{N}_])\\d+\\.\\s*(?=[가-힣(~∼])").replace(pos.replace(base(s), ","), ","), 0);
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(split, 10));
        Iterator<?> it = split.iterator();
        while (it.hasNext()) {
            arrayList.add(INSTANCE.normalize((String) it.next()));
        }
        ArrayList arrayList2 = new ArrayList();
        for (Object obj : arrayList) {
            if (((String) obj).length() > 0) {
                arrayList2.add(obj);
            }
        }
        return CollectionsKt.toSet(arrayList2);
    }

    public final void setFold(Function1<? super String, String> function1) {
        Intrinsics.checkNotNullParameter(function1, "<set-?>");
        fold = function1;
    }

    public final int strength(String text, Card card) {
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(card, "card");
        List plus = CollectionsKt.plus((Collection) CollectionsKt.listOf(card.getMeaning()), (Iterable) card.getAliases());
        ArrayList arrayList = new ArrayList();
        for (Object obj : plus) {
            if (!StringsKt.isBlank((String) obj)) {
                arrayList.add(obj);
            }
        }
        Iterator it = arrayList.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        int definitionStrength = INSTANCE.definitionStrength(text, (String) it.next());
        while (it.hasNext()) {
            int definitionStrength2 = INSTANCE.definitionStrength(text, (String) it.next());
            if (definitionStrength < definitionStrength2) {
                definitionStrength = definitionStrength2;
            }
        }
        return definitionStrength;
    }
}
