package net.classcardauto.mobile;

import kotlin.Metadata;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class SwipePath {
    private final float endX;
    private final float startX;
    private final float y;

    public SwipePath(float f, float f2, float f3) {
        this.startX = f;
        this.endX = f2;
        this.y = f3;
    }

    public static /* synthetic */ SwipePath copy$default(SwipePath swipePath, float f, float f2, float f3, int i, Object obj) {
        if ((i & 1) != 0) {
            f = swipePath.startX;
        }
        if ((i & 2) != 0) {
            f2 = swipePath.endX;
        }
        if ((i & 4) != 0) {
            f3 = swipePath.y;
        }
        return swipePath.copy(f, f2, f3);
    }




    public final SwipePath copy(float startX, float endX, float y) {
        return new SwipePath(startX, endX, y);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SwipePath)) {
            return false;
        }
        SwipePath swipePath = (SwipePath) other;
        return Float.compare(this.startX, swipePath.startX) == 0 && Float.compare(this.endX, swipePath.endX) == 0 && Float.compare(this.y, swipePath.y) == 0;
    }

    public final float getEndX() {
        return this.endX;
    }

    public final float getStartX() {
        return this.startX;
    }

    public final float getY() {
        return this.y;
    }

    public int hashCode() {
        return (((Float.hashCode(this.startX) * 31) + Float.hashCode(this.endX)) * 31) + Float.hashCode(this.y);
    }

    public String toString() {
        return "SwipePath(startX=" + this.startX + ", endX=" + this.endX + ", y=" + this.y + ")";
    }
}
