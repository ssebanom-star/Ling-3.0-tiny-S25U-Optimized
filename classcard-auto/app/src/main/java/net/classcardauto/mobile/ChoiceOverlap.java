package net.classcardauto.mobile;

import java.util.function.IntPredicate;
import kotlin.Metadata;
import kotlin.collections.ArraysKt;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class ChoiceOverlap {
    public static final ChoiceOverlap INSTANCE = new ChoiceOverlap();

    private ChoiceOverlap() {
    }

    private final int[] letters(String text) {
        int[] array = TextRules.INSTANCE.normalize(text).codePoints().filter(new IntPredicate() { // from class: net.classcardauto.mobile.ChoiceOverlap$$ExternalSyntheticLambda0
            @Override // java.util.function.IntPredicate
            public final boolean test(int i) {
                boolean isLetterOrDigit;
                isLetterOrDigit = Character.isLetterOrDigit(i);
                return isLetterOrDigit;
            }
        }).toArray();
        Intrinsics.checkNotNullExpressionValue(array, "toArray(...)");
        return array;
    }

    public final int count(String visible, String expected) {
        Intrinsics.checkNotNullParameter(visible, "visible");
        Intrinsics.checkNotNullParameter(expected, "expected");
        int[] letters = letters(visible);
        int[] letters2 = letters(expected);
        int[] iArr = new int[letters2.length + 1];
        int length = letters.length;
        for (int i = 0; i < length; i++) {
            int i2 = letters[i];
            int length2 = letters2.length;
            int i3 = 0;
            int i4 = 0;
            while (i3 < length2) {
                int i5 = i3 + 1;
                int i6 = iArr[i5];
                iArr[i5] = i2 == letters2[i3] ? i4 + 1 : Math.max(i6, iArr[i3]);
                i3 = i5;
                i4 = i6;
            }
        }
        return ArraysKt.last(iArr);
    }
}
