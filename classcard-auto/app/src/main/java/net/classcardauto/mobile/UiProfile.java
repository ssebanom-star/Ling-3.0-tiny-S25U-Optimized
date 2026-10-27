package net.classcardauto.mobile;

import android.content.Context;
import android.content.pm.PackageInfo;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.TuplesKt;
import kotlin.io.CloseableKt;
import kotlin.io.ConstantsKt;
import kotlin.io.TextStreamsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.json.JSONObject;

/* compiled from: UiProfile.kt */
/* loaded from: /tmp/claude-0/work/out/x/classes.dex */
public final class UiProfile {
    private final int choiceCount;
    private final JSONObject data;
    private final String feedbackMessage;
    private final Map<String, String> mapping;
    private final String name;
    private final String packageName;
    private final String readyMessage;

    /* JADX WARN: Illegal instructions before constructor call */
    public UiProfile(Context context) {
        this(load(context));
    }

    private static JSONObject load(Context context) {
        try {
            InputStream open = context.getAssets().open("classcard-profile.json");
            Intrinsics.checkNotNullExpressionValue(open, "open(...)");
            Reader inputStreamReader = new InputStreamReader(open, Charsets.UTF_8);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                String readText = TextStreamsKt.readText(bufferedReader);
                CloseableKt.closeFinally(bufferedReader, null);
                return new JSONObject(readText);
            } catch (Throwable th) {
                CloseableKt.closeFinally(bufferedReader, th);
                throw th;
            }
        } catch (java.io.IOException | org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public UiProfile(JSONObject data) {
        Intrinsics.checkNotNullParameter(data, "data");
        this.data = data;
        String string = Sneaky.getString(data, "name");
        Intrinsics.checkNotNullExpressionValue(string, "getString(...)");
        this.name = string;
        String string2 = Sneaky.getString(data, "package");
        Intrinsics.checkNotNullExpressionValue(string2, "getString(...)");
        this.packageName = string2;
        this.choiceCount = Sneaky.getInt(data, "choice_count");
        String string3 = Sneaky.getString(data, "ready_message");
        Intrinsics.checkNotNullExpressionValue(string3, "getString(...)");
        this.readyMessage = string3;
        String string4 = Sneaky.getString(data, "feedback_message");
        Intrinsics.checkNotNullExpressionValue(string4, "getString(...)");
        this.feedbackMessage = string4;
        JSONObject jSONObject = Sneaky.getJSONObject(data, "ids");
        Iterator<String> keys = jSONObject.keys();
        Intrinsics.checkNotNullExpressionValue(keys, "keys(...)");
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        while (keys.hasNext()) {
            String str = keys.next();
            linkedHashMap.put(Sneaky.getString(jSONObject, str), str);
        }
        this.mapping = linkedHashMap;
    }

    public final String canonicalId(String observed) {
        Intrinsics.checkNotNullParameter(observed, "observed");
        String str = this.mapping.get(observed);
        return str == null ? observed : str;
    }

    public final int getChoiceCount() {
        return this.choiceCount;
    }

    public final String getFeedbackMessage() {
        return this.feedbackMessage;
    }

    public final String getName() {
        return this.name;
    }

    public final String getPackageName() {
        return this.packageName;
    }

    public final String getReadyMessage() {
        return this.readyMessage;
    }

    public final String installedVersion(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        PackageInfo packageInfo = Sneaky.packageInfo(context.getPackageManager(), this.packageName);
        String str = packageInfo.versionName;
        if (str != null) {
            if (StringsKt.isBlank(str)) {
                str = null;
            }
            if (str != null) {
                return str;
            }
        }
        return "code:" + packageInfo.versionCode;
    }
}
