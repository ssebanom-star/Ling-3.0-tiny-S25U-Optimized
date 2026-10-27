package net.classcardauto.mobile;

import kotlin.Metadata;

/* compiled from: CertificationPolicy.kt */
/* loaded from: classes.dex */
public final class CertificationPolicy {
    public static final CertificationPolicy INSTANCE = new CertificationPolicy();

    private CertificationPolicy() {
    }

    public static /* synthetic */ boolean useAllowed$default(CertificationPolicy certificationPolicy, boolean z, boolean z2, boolean z3, boolean z4, int i, Object obj) {
        if ((i & 8) != 0) {
            z4 = true;
        }
        return certificationPolicy.useAllowed(z, z2, z3, z4);
    }

    public final boolean accessAllowed(boolean required, boolean valid) {
        return !required || valid;
    }

    public final boolean useAllowed(boolean required, boolean valid, boolean agreed, boolean unlocked) {
        return agreed && unlocked && accessAllowed(required, valid);
    }
}
