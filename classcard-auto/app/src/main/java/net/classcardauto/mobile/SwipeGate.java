package net.classcardauto.mobile;

import java.util.LinkedHashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final class SwipeGate {
    private final Map<String, Integer> attempts;
    private final long cooldownMs;
    private final Map<String, Long> last;
    private final int maxAttempts;
    private Pair<String, Bounds> observation;

    public SwipeGate() {
        this(0L, 0, 3, null);
    }

    public SwipeGate(long j, int i) {
        this.cooldownMs = j;
        this.maxAttempts = i;
        this.last = new LinkedHashMap();
        this.attempts = new LinkedHashMap();
    }

    public /* synthetic */ SwipeGate(long j, int i, int i2, DefaultConstructorMarker defaultConstructorMarker) {
        this((i2 & 1) != 0 ? 450L : j, (i2 & 2) != 0 ? 8 : i);
    }

    public static /* synthetic */ boolean ready$default(SwipeGate swipeGate, String str, Bounds bounds, long j, boolean z, int i, int i2, Object obj) {
        if ((i2 & 8) != 0) {
            z = true;
        }
        boolean z2 = z;
        if ((i2 & 16) != 0) {
            i = swipeGate.maxAttempts;
        }
        return swipeGate.ready(str, bounds, j, z2, i);
    }

    public final boolean ready(String key, Bounds bounds, long now, boolean requireStable, int limit) {
        Long l;
        Intrinsics.checkNotNullParameter(key, "key");
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Pair<String, Bounds> pair = new Pair<>(key, bounds);
        boolean areEqual = Intrinsics.areEqual(pair, this.observation);
        this.observation = pair;
        if (!requireStable || areEqual) {
            Integer num = this.attempts.get(key);
            if ((num != null ? num.intValue() : 0) < Math.min(limit, this.maxAttempts) && ((l = this.last.get(key)) == null || now - l.longValue() >= this.cooldownMs)) {
                this.last.put(key, Long.valueOf(now));
                Map<String, Integer> map = this.attempts;
                Integer num2 = map.get(key);
                map.put(key, Integer.valueOf((num2 != null ? num2.intValue() : 0) + 1));
                return true;
            }
        }
        return false;
    }

    public final void resetObservation() {
        this.observation = null;
    }
}
