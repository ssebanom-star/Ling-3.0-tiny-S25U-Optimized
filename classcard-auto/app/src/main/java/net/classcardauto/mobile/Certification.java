package net.classcardauto.mobile;

import android.content.Context;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;

/* compiled from: Certification.kt */
/* loaded from: classes.dex */
public final class Certification {
    public static final String BLOCKED_TEXT = "Uncertified - Please Check your Lisence";
    private static CertifiedDocument document;
    private static File root;
    public static final Certification INSTANCE = new Certification();
    private static String targetDeviceId = "";
    private static String targetPackage = "";

    private Certification() {
    }

    public final File certificateFile() {
        File file = root;
        if (file == null) {
            Intrinsics.throwUninitializedPropertyAccessException("root");
            file = null;
        }
        return new File(file, "certification/certificate.json");
    }

    public final String deviceId() {
        return targetDeviceId;
    }

    public final boolean getAccessAllowed() {
        return CertificationPolicy.INSTANCE.accessAllowed(getRequired(), getValid());
    }

    public final CertifiedDocument getDocument() {
        return document;
    }

    /** The "auth" flavor only runs with an authorisation file issued for the phone (see issuer/ tools); "noauth" does not. */
    public final boolean getRequired() {
        return BuildConfig.AUTH_REQUIRED;
    }

    // ---- authorisation file dropped in by the owner from a computer ----

    private static File dropDir;
    private static String lastDropMessage = "";

    private static final String DROP_README = "이 폴더에 컴퓨터에서 만든 인증 키 파일(classcard-auth.key)을 넣으면 앱이 읽고 확인한 뒤 지웁니다.\n"
            + "인증 키 파일 만드는 법: issuer 폴더의 issue.py 에 앱 화면의 기기 코드를 넣어 실행하세요.\n";

    /** Folder reachable from a computer over USB (Android/data/<package>/files/auth). May be null without storage. */
    public final File dropDir() {
        return dropDir;
    }

    /** Human-readable location, e.g. "내장 메모리/Android/data/<package>/files/auth". */
    public final String dropPathLabel() {
        File dir = dropDir;
        if (dir == null) {
            return "(저장 공간을 사용할 수 없습니다)";
        }
        String path = dir.getAbsolutePath();
        for (String prefix : new String[]{"/storage/emulated/0", "/sdcard"}) {
            if (path.startsWith(prefix)) {
                return "내장 메모리" + path.substring(prefix.length());
            }
        }
        return path;
    }

    /** Message about the last scan ("" when nothing to report), for the activation screen. */
    public final String lastDropMessage() {
        return lastDropMessage;
    }

    private final void ensureDropDir() {
        File dir = dropDir;
        if (dir == null) {
            return;
        }
        try {
            dir.mkdirs();
            File readme = new File(dir, "README.txt");
            if (!readme.exists()) {
                java.io.FileWriter w = new java.io.FileWriter(readme);
                w.write(DROP_README);
                w.close();
            }
        } catch (Exception ignored) {
            // the folder is a convenience; activation can still be retried
        }
    }

    /**
     * Looks for authorisation files in the drop folder and installs the first valid one (deleting it afterwards).
     * Returns true when the app is authorised after the scan.
     */
    public final boolean scanDropFolder() {
        ensureDropDir();
        lastDropMessage = "";
        if (getValid()) {
            return true;
        }
        File dir = dropDir;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null || files.length == 0) {
            return false;
        }
        java.util.Arrays.sort(files);
        for (File file : files) {
            if (!file.isFile() || file.getName().equalsIgnoreCase("README.txt")) {
                continue;
            }
            if (file.length() > 16384) {
                lastDropMessage = file.getName() + ": 인증 키 파일이 아닙니다(너무 큼).";
                continue;
            }
            try {
                String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), Charsets.UTF_8);
                install(text);
                if (!file.delete()) {
                    lastDropMessage = "인증은 완료됐지만 키 파일을 지우지 못했습니다. 직접 지워 주세요.";
                }
                return true;
            } catch (Throwable t) {
                lastDropMessage = file.getName() + ": " + describe(t);
            }
        }
        return false;
    }

    /** Short Korean explanation of why a file was rejected. */
    public static String describe(Throwable t) {
        String m = t.getMessage() == null ? "" : t.getMessage();
        if (m.contains("Invalid signature")) {
            return "서명이 올바르지 않아요. 이 앱을 위해 발급한 파일인지 확인하세요.";
        }
        if (m.contains("Wrong device")) {
            return "다른 휴대폰(또는 다른 앱)용으로 발급된 파일이에요.";
        }
        if (m.contains("Wrong issuing")) {
            return "이 앱이 신뢰하는 발급자가 만든 파일이 아니에요.";
        }
        if (m.contains("expired")) {
            return "만료된 인증 키 파일이에요. 새로 발급하세요.";
        }
        if (m.contains("Not an issuer")) {
            return "발급자 인증서가 아니에요.";
        }
        if (t instanceof org.json.JSONException || t instanceof IllegalArgumentException || t instanceof java.time.format.DateTimeParseException) {
            return "파일 형식이 올바르지 않아요.";
        }
        return "확인하지 못했어요(" + t.getClass().getSimpleName() + ").";
    }

    public final boolean getValid() {
        return document != null;
    }

    public final void initialize(Context context) {
        String string;
        Intrinsics.checkNotNullParameter(context, "context");
        root = context.getFilesDir();
        String packageName = context.getPackageName();
        Intrinsics.checkNotNullExpressionValue(packageName, "getPackageName(...)");
        targetPackage = packageName;
        String str = "";
        if (getRequired() && (string = Settings.Secure.getString(context.getContentResolver(), "android_id")) != null) {
            str = string;
        }
        targetDeviceId = str.toLowerCase(java.util.Locale.ROOT);
        dropDir = context.getExternalFilesDir("auth");
        ensureDropDir();
        refresh();
    }

    /* JADX WARN: Multi-variable type inference failed */
    public final synchronized CertifiedDocument install(String envelope) {
        try {
            return install0(envelope);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final CertifiedDocument install0(String envelope) throws Exception {
        CertifiedDocument verify;
        Intrinsics.checkNotNullParameter(envelope, "envelope");
        if (!getRequired()) {
            throw new IllegalStateException("Certification is disabled in this build".toString());
        }
        verify = new CertificateVerifier().verify(envelope, deviceId(), targetPackage);
        File certificateFile = certificateFile();
        File parentFile = certificateFile.getParentFile();
        Intrinsics.checkNotNull(parentFile);
        if (!parentFile.isDirectory()) {
            File parentFile2 = certificateFile.getParentFile();
            Intrinsics.checkNotNull(parentFile2);
            if (!parentFile2.mkdirs()) {
                throw new IllegalStateException("Check failed.");
            }
        }
        AtomicFile atomicFile = new AtomicFile(certificateFile);
        FileOutputStream startWrite = atomicFile.startWrite();
        try {
            byte[] bytes = envelope.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
            startWrite.write(bytes);
            atomicFile.finishWrite(startWrite);
            document = verify;
            UsageConsent.INSTANCE.getSession().reset();
            BotService companion = BotService.INSTANCE.getInstance();
            if (companion != null) {
                companion.stop("새 인증서가 설치되었습니다. 앱에서 확인해주세요.");
            }
        } catch (Exception e) {
            atomicFile.failWrite(startWrite);
            throw e;
        }
        return verify;
    }

    public final void refresh() {
        if (!getRequired()) {
            document = null;
            return;
        }
        try {
            AtomicFile atomicFile = new AtomicFile(certificateFile());
            CertificateVerifier certificateVerifier = new CertificateVerifier();
            byte[] readFully = atomicFile.readFully();
            Intrinsics.checkNotNullExpressionValue(readFully, "readFully(...)");
            document = certificateVerifier.verify(new String(readFully, Charsets.UTF_8), deviceId(), targetPackage);
        } catch (Throwable th) {
            document = null;
        }
    }
}
