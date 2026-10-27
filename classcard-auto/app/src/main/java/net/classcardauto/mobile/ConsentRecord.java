package net.classcardauto.mobile;

import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: ConsentRecord.kt */
/* loaded from: classes.dex */
public final /* data */ class ConsentRecord {
    private final long acceptedAtMillis;
    private final String policyDigest;
    private final int steps;

    public ConsentRecord(String policyDigest, long j, int i) {
        Intrinsics.checkNotNullParameter(policyDigest, "policyDigest");
        this.policyDigest = policyDigest;
        this.acceptedAtMillis = j;
        this.steps = i;
    }

    public static /* synthetic */ ConsentRecord copy$default(ConsentRecord consentRecord, String str, long j, int i, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            str = consentRecord.policyDigest;
        }
        if ((i2 & 2) != 0) {
            j = consentRecord.acceptedAtMillis;
        }
        if ((i2 & 4) != 0) {
            i = consentRecord.steps;
        }
        return consentRecord.copy(str, j, i);
    }




    public final ConsentRecord copy(String policyDigest, long acceptedAtMillis, int steps) {
        Intrinsics.checkNotNullParameter(policyDigest, "policyDigest");
        return new ConsentRecord(policyDigest, acceptedAtMillis, steps);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConsentRecord)) {
            return false;
        }
        ConsentRecord consentRecord = (ConsentRecord) other;
        return Intrinsics.areEqual(this.policyDigest, consentRecord.policyDigest) && this.acceptedAtMillis == consentRecord.acceptedAtMillis && this.steps == consentRecord.steps;
    }

    public final long getAcceptedAtMillis() {
        return this.acceptedAtMillis;
    }

    public final String getPolicyDigest() {
        return this.policyDigest;
    }

    public final int getSteps() {
        return this.steps;
    }

    public int hashCode() {
        return (((this.policyDigest.hashCode() * 31) + Long.hashCode(this.acceptedAtMillis)) * 31) + Integer.hashCode(this.steps);
    }

    public String toString() {
        return "ConsentRecord(policyDigest=" + this.policyDigest + ", acceptedAtMillis=" + this.acceptedAtMillis + ", steps=" + this.steps + ")";
    }

    public final boolean validFor(String currentDigest) {
        Intrinsics.checkNotNullParameter(currentDigest, "currentDigest");
        return Intrinsics.areEqual(this.policyDigest, currentDigest) && !StringsKt.isBlank(this.policyDigest) && this.acceptedAtMillis > 0 && this.steps == 2;
    }
}
