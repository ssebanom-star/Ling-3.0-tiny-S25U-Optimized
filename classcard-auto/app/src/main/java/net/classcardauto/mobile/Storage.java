package net.classcardauto.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import kotlin.Metadata;
import kotlin.io.FilesKt;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Storage.kt */
/* loaded from: classes.dex */
public final class Storage {
    private final SharedPreferences prefs;
    private final File root;

    public Storage(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        this.root = context.getFilesDir();
        this.prefs = context.getSharedPreferences("settings", 0);
    }

    public final SharedPreferences getPrefs() {
        return this.prefs;
    }

    public final File getRoot() {
        return this.root;
    }

    public final String latestSummary() {
        String readText$default;
        File file = new File(this.root, "last-summary.json");
        if (!file.exists()) {
            file = null;
        }
        return (file == null || (readText$default = FilesKt.readText(file, kotlin.text.Charsets.UTF_8)) == null) ? "아직 실행 기록이 없습니다." : readText$default;
    }
}
