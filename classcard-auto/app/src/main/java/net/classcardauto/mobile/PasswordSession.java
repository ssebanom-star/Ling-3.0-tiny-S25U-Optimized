package net.classcardauto.mobile;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.CharsKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;

/* compiled from: AppPassword.kt */
/* loaded from: classes.dex */
public final class PasswordSession {
    private static final String PRESET_DIGEST = "61ffd0d60f810499aab9a064b9bff1d636b67ef2aa3f531d017652f5a8caa833";
    private final String expectedDigest;
    private final boolean required;
    private boolean unlocked;

    public PasswordSession(boolean z, String expectedDigest) {
        Intrinsics.checkNotNullParameter(expectedDigest, "expectedDigest");
        this.required = z;
        this.expectedDigest = expectedDigest;
        this.unlocked = !z;
    }

    public /* synthetic */ PasswordSession(boolean z, String str, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(z, (i & 2) != 0 ? PRESET_DIGEST : str);
    }

    public static /* synthetic */ void enter$default(PasswordSession passwordSession, boolean z, int i, Object obj) {
        if ((i & 1) != 0) {
            z = false;
        }
        passwordSession.enter(z);
    }

    public final void enter(boolean newLaunch) {
        if (newLaunch) {
            lock();
        }
    }

    public final boolean getUnlocked() {
        return this.unlocked;
    }

    public final void lock() {
        this.unlocked = !this.required;
    }

    public final boolean unlock(String input) {
        Intrinsics.checkNotNullParameter(input, "input");
        if (!this.required) {
            return true;
        }
        MessageDigest messageDigest = Sneaky.sha256();
        byte[] bytes = input.getBytes(Charsets.UTF_8);
        Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
        byte[] digest = messageDigest.digest(bytes);
        List<String> chunked = StringsKt.chunked(this.expectedDigest, 2);
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(chunked, 10));
        Iterator<?> it = chunked.iterator();
        while (it.hasNext()) {
            arrayList.add(Byte.valueOf((byte) Integer.parseInt((String) it.next(), CharsKt.checkRadix(16))));
        }
        boolean isEqual = MessageDigest.isEqual(CollectionsKt.toByteArray(arrayList), digest);
        this.unlocked = isEqual;
        return isEqual;
    }
}
