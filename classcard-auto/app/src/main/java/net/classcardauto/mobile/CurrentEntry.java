package net.classcardauto.mobile;

import java.util.Arrays;
import java.util.List;

/* compiled from: Core.kt */
public enum CurrentEntry {
    SET,
    PREVIEW,
    ACTIVE_TEST,
    UNKNOWN;

    public static List<CurrentEntry> getEntries() {
        return Arrays.asList(values());
    }
}
