package net.classcardauto.mobile;

import kotlin.Metadata;

/* compiled from: OverlayInputGuard.kt */
/* loaded from: classes.dex */
public final class OverlayInputGuard {
    private Long current;
    private long generation;

    public final long begin() {
        if (getBlocked()) {
            throw new IllegalStateException("다른 제스처가 진행 중입니다.".toString());
        }
        long j = this.generation + 1;
        this.generation = j;
        this.current = Long.valueOf(j);
        return j;
    }

    public final boolean finish(long token) {
        if (!owns(token)) {
            return false;
        }
        this.current = null;
        return true;
    }

    public final boolean getBlocked() {
        return this.current != null;
    }

    public final boolean owns(long token) {
        Long l = this.current;
        return l != null && l.longValue() == token;
    }

    public final void reset() {
        this.generation++;
        this.current = null;
    }
}
