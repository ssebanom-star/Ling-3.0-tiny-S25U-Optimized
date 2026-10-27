package net.classcardauto.mobile;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import org.json.JSONObject;

/**
 * Kotlin does not have checked exceptions; these helpers keep the original exception type propagating
 * unchanged from decompiled call sites that would otherwise need throws clauses.
 */
final class Sneaky {
    private Sneaky() {
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    static java.security.MessageDigest sha256() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    static java.io.File canonical(java.io.File f) {
        try {
            return f.getCanonicalFile();
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    static String getString(JSONObject o, String key) {
        try {
            return o.getString(key);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    static int getInt(JSONObject o, String key) {
        try {
            return o.getInt(key);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    static JSONObject getJSONObject(JSONObject o, String key) {
        try {
            return o.getJSONObject(key);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    static PackageInfo packageInfo(PackageManager pm, String name) {
        try {
            return pm.getPackageInfo(name, 0);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }
}
