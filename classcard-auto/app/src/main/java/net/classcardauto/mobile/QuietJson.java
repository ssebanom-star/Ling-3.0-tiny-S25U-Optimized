package net.classcardauto.mobile;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSONObject whose mutators do not declare the checked JSONException. Kotlin has no checked exceptions, so the
 * decompiled call sites (which come from Kotlin) are written against this type instead.
 */
public final class QuietJson extends JSONObject {
    @Override
    public QuietJson put(String name, Object value) {
        try {
            super.put(name, value);
            return this;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public QuietJson put(String name, boolean value) {
        return put(name, (Object) Boolean.valueOf(value));
    }

    @Override
    public QuietJson put(String name, double value) {
        return put(name, (Object) Double.valueOf(value));
    }

    @Override
    public QuietJson put(String name, int value) {
        return put(name, (Object) Integer.valueOf(value));
    }

    @Override
    public QuietJson put(String name, long value) {
        return put(name, (Object) Long.valueOf(value));
    }

    @Override
    public String toString(int indentSpaces) {
        try {
            return super.toString(indentSpaces);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }
}
