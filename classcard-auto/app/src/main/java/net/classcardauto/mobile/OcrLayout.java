package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import kotlin.KotlinVersion;
import kotlin.Metadata;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.collections.IntIterator;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.IntProgression;
import kotlin.ranges.IntRange;
import kotlin.ranges.RangesKt;

/* compiled from: OcrLayout.kt */
/* loaded from: classes.dex */
public final class OcrLayout {
    public static final OcrLayout INSTANCE = new OcrLayout();

    private OcrLayout() {
    }

    private final int background(int[] pixels) {
        IntProgression step = RangesKt.step(ArraysKt.getIndices(pixels), Math.max(1, pixels.length / 2000));
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(step, 10));
        Iterator<Integer> it = step.iterator();
        while (it.hasNext()) {
            arrayList.add(Integer.valueOf(INSTANCE.light(pixels[((IntIterator) it).nextInt()])));
        }
        List sorted = CollectionsKt.sorted(arrayList);
        return ((Number) sorted.get(sorted.size() / 2)).intValue();
    }

    private final int light(int color) {
        return (((((color >> 16) & KotlinVersion.MAX_COMPONENT_VALUE) * 299) + (((color >> 8) & KotlinVersion.MAX_COMPONENT_VALUE) * 587)) + ((color & KotlinVersion.MAX_COMPONENT_VALUE) * 114)) / 1000;
    }

    private final boolean textRow(int count, int width) {
        return count >= Math.max(1, width / 1000) && count < Math.max(2, (width * 95) / 100);
    }

    public final List<Bounds> lines(int width, int height, int[] pixels) {
        int i;
        Intrinsics.checkNotNullParameter(pixels, "pixels");
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        int background = background(pixels);
        int length = pixels.length;
        boolean[] zArr = new boolean[length];
        int i2 = 0;
        while (true) {
            boolean z = true;
            if (i2 >= length) {
                break;
            }
            if (Math.abs(INSTANCE.light(pixels[i2]) - background) < 45) {
                z = false;
            }
            zArr[i2] = z;
            i2++;
        }
        IntRange until = RangesKt.until(0, height);
        ArrayList arrayList = new ArrayList();
        for (Integer num : until) {
            int intValue = num.intValue();
            OcrLayout ocrLayout = INSTANCE;
            Iterable until2 = RangesKt.until(0, width);
            if ((until2 instanceof Collection) && ((Collection) until2).isEmpty()) {
                i = 0;
            } else {
                Iterator it = until2.iterator();
                i = 0;
                while (it.hasNext()) {
                    if (zArr[(intValue * width) + ((IntIterator) it).nextInt()] && (i = i + 1) < 0) {
                        CollectionsKt.throwCountOverflow();
                    }
                }
            }
            if (ocrLayout.textRow(i, width)) {
                arrayList.add(num);
            }
        }
        ArrayList arrayList2 = arrayList;
        if (arrayList2.isEmpty()) {
            return CollectionsKt.emptyList();
        }
        ArrayList<Bounds> arrayList3 = new ArrayList();
        int intValue2 = ((Number) CollectionsKt.first((List) arrayList2)).intValue();
        Iterator it2 = CollectionsKt.drop(arrayList2, 1).iterator();
        int i3 = intValue2;
        while (it2.hasNext()) {
            int intValue3 = ((Number) it2.next()).intValue();
            int i4 = i3 + 1;
            if (intValue3 > i4) {
                arrayList3.add(new Bounds(0, intValue2, width, i4));
                intValue2 = intValue3;
            }
            i3 = intValue3;
        }
        arrayList3.add(new Bounds(0, intValue2, width, i3 + 1));
        ArrayList arrayList4 = new ArrayList();
        for (Bounds bounds : arrayList3) {
            Bounds bounds2 = (Bounds) CollectionsKt.lastOrNull((List) arrayList4);
            if (bounds2 == null || bounds.getTop() - bounds2.getBottom() > Math.max(2, Math.max(bounds2.getHeight(), bounds.getHeight()) / 4)) {
                arrayList4.add(bounds);
            } else {
                arrayList4.set(CollectionsKt.getLastIndex(arrayList4), Bounds.copy$default(bounds2, 0, 0, 0, bounds.getBottom(), 7, null));
            }
        }
        ArrayList<Bounds> arrayList5 = arrayList4;
        ArrayList arrayList6 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList5, 10));
        for (Bounds bounds3 : arrayList5) {
            int bottom = bounds3.getBottom();
            int i5 = width;
            int i6 = 0;
            for (int top = bounds3.getTop(); top < bottom; top++) {
                for (int i7 = 0; i7 < width; i7++) {
                    if (zArr[(top * width) + i7]) {
                        i5 = Math.min(i5, i7);
                        i6 = Math.max(i6, i7 + 1);
                    }
                }
            }
            int max = Math.max(2, bounds3.getHeight() / 32);
            arrayList6.add(new Bounds(Math.max(0, i5 - max), Math.max(0, bounds3.getTop() - max), Math.min(width, i6 + max), Math.min(height, bounds3.getBottom() + max)));
        }
        return arrayList6;
    }

    public final boolean verticallyClipped(int width, int height, int[] pixels) {
        int i;
        Intrinsics.checkNotNullParameter(pixels, "pixels");
        if (width <= 0 || height <= 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        int background = background(pixels);
        List listOf = CollectionsKt.listOf((Object[]) new Integer[]{0, Integer.valueOf(height - 1)});
        if ((listOf instanceof Collection) && listOf.isEmpty()) {
            return false;
        }
        Iterator it = listOf.iterator();
        while (it.hasNext()) {
            int intValue = ((Number) it.next()).intValue();
            OcrLayout ocrLayout = INSTANCE;
            Iterable until = RangesKt.until(0, width);
            if ((until instanceof Collection) && ((Collection) until).isEmpty()) {
                i = 0;
            } else {
                Iterator it2 = until.iterator();
                i = 0;
                while (it2.hasNext()) {
                    if (Math.abs(INSTANCE.light(pixels[(intValue * width) + ((IntIterator) it2).nextInt()]) - background) >= 45 && (i = i + 1) < 0) {
                        CollectionsKt.throwCountOverflow();
                    }
                }
            }
            if (ocrLayout.textRow(i, width)) {
                return true;
            }
        }
        return false;
    }
}
