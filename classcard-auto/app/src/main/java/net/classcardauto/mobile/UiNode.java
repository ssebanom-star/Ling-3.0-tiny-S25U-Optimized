package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.io.ConstantsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.uuid.Uuid;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class UiNode {
    private final Bounds bounds;
    private final boolean checked;
    private final boolean clickable;
    private final Integer collectionIndex;
    private final String description;
    private final boolean enabled;
    private final String id;
    private final Bounds rowBounds;
    private final boolean scrollable;
    private final boolean selected;
    private final String text;
    private final List<String> textAlternatives;

    public UiNode(String id, String text, Bounds bounds, String description, boolean z, boolean z2, boolean z3, boolean z4, Bounds bounds2, boolean z5, Integer num, List<String> textAlternatives) {
        Intrinsics.checkNotNullParameter(id, "id");
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(description, "description");
        Intrinsics.checkNotNullParameter(textAlternatives, "textAlternatives");
        this.id = id;
        this.text = text;
        this.bounds = bounds;
        this.description = description;
        this.enabled = z;
        this.clickable = z2;
        this.scrollable = z3;
        this.selected = z4;
        this.rowBounds = bounds2;
        this.checked = z5;
        this.collectionIndex = num;
        this.textAlternatives = textAlternatives;
    }

    public /* synthetic */ UiNode(String str, String str2, Bounds bounds, String str3, boolean z, boolean z2, boolean z3, boolean z4, Bounds bounds2, boolean z5, Integer num, List list, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, str2, bounds, (i & 8) != 0 ? "" : str3, (i & 16) != 0 ? true : z, (i & 32) != 0 ? false : z2, (i & 64) != 0 ? false : z3, (i & Uuid.SIZE_BITS) != 0 ? false : z4, (i & 256) != 0 ? null : bounds2, (i & ConstantsKt.MINIMUM_BLOCK_SIZE) != 0 ? false : z5, (i & 1024) != 0 ? null : num, (i & 2048) != 0 ? CollectionsKt.emptyList() : list);
    }

    public static /* synthetic */ UiNode copy$default(UiNode uiNode, String str, String str2, Bounds bounds, String str3, boolean z, boolean z2, boolean z3, boolean z4, Bounds bounds2, boolean z5, Integer num, List list, int i, Object obj) {
        if ((i & 1) != 0) {
            str = uiNode.id;
        }
        if ((i & 2) != 0) {
            str2 = uiNode.text;
        }
        if ((i & 4) != 0) {
            bounds = uiNode.bounds;
        }
        if ((i & 8) != 0) {
            str3 = uiNode.description;
        }
        if ((i & 16) != 0) {
            z = uiNode.enabled;
        }
        if ((i & 32) != 0) {
            z2 = uiNode.clickable;
        }
        if ((i & 64) != 0) {
            z3 = uiNode.scrollable;
        }
        if ((i & Uuid.SIZE_BITS) != 0) {
            z4 = uiNode.selected;
        }
        if ((i & 256) != 0) {
            bounds2 = uiNode.rowBounds;
        }
        if ((i & ConstantsKt.MINIMUM_BLOCK_SIZE) != 0) {
            z5 = uiNode.checked;
        }
        if ((i & 1024) != 0) {
            num = uiNode.collectionIndex;
        }
        if ((i & 2048) != 0) {
            list = uiNode.textAlternatives;
        }
        Integer num2 = num;
        List list2 = list;
        Bounds bounds3 = bounds2;
        boolean z6 = z5;
        boolean z7 = z3;
        boolean z8 = z4;
        boolean z9 = z;
        boolean z10 = z2;
        return uiNode.copy(str, str2, bounds, str3, z9, z10, z7, z8, bounds3, z6, num2, list2);
    }




    public final List<String> component12() {
        return this.textAlternatives;
    }









    public final UiNode copy(String id, String text, Bounds bounds, String description, boolean enabled, boolean clickable, boolean scrollable, boolean selected, Bounds rowBounds, boolean checked, Integer collectionIndex, List<String> textAlternatives) {
        Intrinsics.checkNotNullParameter(id, "id");
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(bounds, "bounds");
        Intrinsics.checkNotNullParameter(description, "description");
        Intrinsics.checkNotNullParameter(textAlternatives, "textAlternatives");
        return new UiNode(id, text, bounds, description, enabled, clickable, scrollable, selected, rowBounds, checked, collectionIndex, textAlternatives);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UiNode)) {
            return false;
        }
        UiNode uiNode = (UiNode) other;
        return Intrinsics.areEqual(this.id, uiNode.id) && Intrinsics.areEqual(this.text, uiNode.text) && Intrinsics.areEqual(this.bounds, uiNode.bounds) && Intrinsics.areEqual(this.description, uiNode.description) && this.enabled == uiNode.enabled && this.clickable == uiNode.clickable && this.scrollable == uiNode.scrollable && this.selected == uiNode.selected && Intrinsics.areEqual(this.rowBounds, uiNode.rowBounds) && this.checked == uiNode.checked && Intrinsics.areEqual(this.collectionIndex, uiNode.collectionIndex) && Intrinsics.areEqual(this.textAlternatives, uiNode.textAlternatives);
    }

    public final Bounds getBounds() {
        return this.bounds;
    }

    public final boolean getChecked() {
        return this.checked;
    }

    public final boolean getClickable() {
        return this.clickable;
    }

    public final Integer getCollectionIndex() {
        return this.collectionIndex;
    }

    public final String getDescription() {
        return this.description;
    }

    public final boolean getEnabled() {
        return this.enabled;
    }

    public final String getId() {
        return this.id;
    }

    public final Bounds getRowBounds() {
        return this.rowBounds;
    }

    public final boolean getScrollable() {
        return this.scrollable;
    }

    public final boolean getSelected() {
        return this.selected;
    }

    public final String getText() {
        return this.text;
    }

    public final List<String> getTextAlternatives() {
        return this.textAlternatives;
    }

    public int hashCode() {
        int hashCode = ((((((((((((((this.id.hashCode() * 31) + this.text.hashCode()) * 31) + this.bounds.hashCode()) * 31) + this.description.hashCode()) * 31) + Boolean.hashCode(this.enabled)) * 31) + Boolean.hashCode(this.clickable)) * 31) + Boolean.hashCode(this.scrollable)) * 31) + Boolean.hashCode(this.selected)) * 31;
        Bounds bounds = this.rowBounds;
        int hashCode2 = (((hashCode + (bounds == null ? 0 : bounds.hashCode())) * 31) + Boolean.hashCode(this.checked)) * 31;
        Integer num = this.collectionIndex;
        return ((hashCode2 + (num != null ? num.hashCode() : 0)) * 31) + this.textAlternatives.hashCode();
    }

    public String toString() {
        return "UiNode(id=" + this.id + ", text=" + this.text + ", bounds=" + this.bounds + ", description=" + this.description + ", enabled=" + this.enabled + ", clickable=" + this.clickable + ", scrollable=" + this.scrollable + ", selected=" + this.selected + ", rowBounds=" + this.rowBounds + ", checked=" + this.checked + ", collectionIndex=" + this.collectionIndex + ", textAlternatives=" + this.textAlternatives + ")";
    }
}
