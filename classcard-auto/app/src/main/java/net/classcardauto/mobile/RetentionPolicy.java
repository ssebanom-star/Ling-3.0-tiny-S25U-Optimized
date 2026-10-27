package net.classcardauto.mobile;

import java.io.File;
import java.util.Iterator;
import java.util.Set;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.io.FilesKt;
import kotlin.jvm.internal.Intrinsics;
import org.json.JSONObject;

/* compiled from: RetentionPolicy.kt */
/* loaded from: classes.dex */
public final class RetentionPolicy {
    public static final RetentionPolicy INSTANCE = new RetentionPolicy();
    private static final Set<String> statisticKeys = new java.util.LinkedHashSet<String>(java.util.Arrays.asList(new String[]{"type", "seconds", "mode", "runtime", "day", "device", "android_sdk", "started_at", "requires_adb", "pace", "ocr_mode", "ocr_model", "ocr_only", "classcard_version", "phase", "cards", "answers", "score", "progress", "attempt", "read_to_tap_ms", "side", "used_screen_text", "confidence", "inference_ms", "page", "unchanged", "indexed", "strategy", "anchor_visible", "skipped_completed", "visible", "traversed_to_end", "ignores_saved_day_and_title", "action", "start_x", "end_x", "y", "duration_ms", "outside_answer_rows", "error_code", "pairs", "ocr_captures", "inline_pairs", "collection_mode"}));

    private RetentionPolicy() {
    }

    public final void removeLegacyData(File root) {
        Intrinsics.checkNotNullParameter(root, "root");
        File canonicalFile = Sneaky.canonical(root);
        Iterator it = CollectionsKt.listOf((Object[]) new String[]{"vocabulary.json", "vocabulary.new", "sets", "runs", "diagnostic.json", "diagnostic.png", "ocr-verification"}).iterator();
        while (it.hasNext()) {
            File file = new File(canonicalFile, (String) it.next());
            if (file.exists()) {
                if (!Sneaky.canonical(file).toPath().startsWith(canonicalFile.toPath())) {
                    throw new IllegalStateException("Check failed.");
                }
                if (!FilesKt.deleteRecursively(file)) {
                    throw new IllegalStateException("이전 수집 자료 삭제 실패".toString());
                }
            }
        }
        File file2 = new File(canonicalFile, "last-summary.json");
        if (!file2.exists()) {
            file2 = null;
        }
        if (file2 != null && !file2.delete()) {
            throw new IllegalStateException("이전 실행 기록 삭제 실패".toString());
        }
    }

    public final JSONObject statistics(JSONObject data) {
        Intrinsics.checkNotNullParameter(data, "data");
        QuietJson jSONObject = new QuietJson();
        for (String str : statisticKeys) {
            Object opt = data.opt(str);
            if ((opt instanceof String) || (opt instanceof Number) || (opt instanceof Boolean)) {
                jSONObject.put(str, opt);
            }
        }
        return jSONObject;
    }
}
