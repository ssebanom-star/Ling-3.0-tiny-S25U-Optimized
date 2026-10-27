package net.classcardauto.mobile;

import kotlin.Metadata;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class Bounds {
    private final int bottom;
    private final int left;
    private final int right;
    private final int top;

    public Bounds(int i, int i2, int i3, int i4) {
        this.left = i;
        this.top = i2;
        this.right = i3;
        this.bottom = i4;
    }

    public static /* synthetic */ Bounds copy$default(Bounds bounds, int i, int i2, int i3, int i4, int i5, Object obj) {
        if ((i5 & 1) != 0) {
            i = bounds.left;
        }
        if ((i5 & 2) != 0) {
            i2 = bounds.top;
        }
        if ((i5 & 4) != 0) {
            i3 = bounds.right;
        }
        if ((i5 & 8) != 0) {
            i4 = bounds.bottom;
        }
        return bounds.copy(i, i2, i3, i4);
    }





    public final Bounds copy(int left, int top, int right, int bottom) {
        return new Bounds(left, top, right, bottom);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Bounds)) {
            return false;
        }
        Bounds bounds = (Bounds) other;
        return this.left == bounds.left && this.top == bounds.top && this.right == bounds.right && this.bottom == bounds.bottom;
    }

    public final int getBottom() {
        return this.bottom;
    }

    public final int getHeight() {
        return this.bottom - this.top;
    }

    public final int getLeft() {
        return this.left;
    }

    public final int getRight() {
        return this.right;
    }

    public final int getTop() {
        return this.top;
    }

    public final boolean getValid() {
        return getWidth() > 0 && getHeight() > 0;
    }

    public final int getWidth() {
        return this.right - this.left;
    }

    public final int getX() {
        return (this.left + this.right) / 2;
    }

    public final int getY() {
        return (this.top + this.bottom) / 2;
    }

    public int hashCode() {
        return (((((Integer.hashCode(this.left) * 31) + Integer.hashCode(this.top)) * 31) + Integer.hashCode(this.right)) * 31) + Integer.hashCode(this.bottom);
    }

    public String toString() {
        return "Bounds(left=" + this.left + ", top=" + this.top + ", right=" + this.right + ", bottom=" + this.bottom + ")";
    }
}
