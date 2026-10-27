package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: OcrLayout.kt */
/* loaded from: classes.dex */
public final class CtcDecode {
    public static final CtcDecode INSTANCE = new CtcDecode();

    private CtcDecode() {
    }

    public final CtcRead decode(int[] indices, float[] probabilities, List<String> characters) {
        Intrinsics.checkNotNullParameter(indices, "indices");
        Intrinsics.checkNotNullParameter(probabilities, "probabilities");
        Intrinsics.checkNotNullParameter(characters, "characters");
        if (indices.length != probabilities.length) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        StringBuilder sb = new StringBuilder();
        int length = indices.length;
        int i = 0;
        int i2 = 0;
        int i3 = -1;
        double d = 0.0d;
        int i4 = 0;
        while (i < length) {
            int i5 = indices[i];
            int i6 = i2 + 1;
            if (i5 < 0 || i5 >= characters.size()) {
                throw new IllegalArgumentException("Failed requirement.".toString());
            }
            if (i5 != 0 && i5 != i3) {
                sb.append(characters.get(i5));
                d += probabilities[i2];
                i4++;
            }
            i++;
            i3 = i5;
            i2 = i6;
        }
        return new CtcRead(sb.toString(), i4 > 0 ? d / i4 : 0.0d);
    }
}
