package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * The three run modes shown to the user. A mode is only a combination of the existing switches ({@code turbo},
 * {@code turbo_scope_*}, {@code human_like}); nothing else reads it, so the engine is unchanged.
 */
public final class ModePreset {
    public static final int CUSTOM = -1;
    public static final int HUMAN = 0;
    public static final int HYBRID = 1;
    public static final int TURBO = 2;

    /** title, one-line description. */
    public static final String[][] INFO = {
            {"사람처럼", "수집·시험 모두 사람처럼 천천히 (대회 평가용)"},
            {"하이브리드", "수집은 초고속, 시험은 사람처럼"},
            {"초고속", "수집·시험 모두 최대한 빠르게"},
    };

    private ModePreset() {
    }

    /** The mode the current switches add up to, or CUSTOM when they match none of them. */
    public static int current(SharedPreferences p) {
        boolean human = p.getBoolean("human_like", false);
        if (!p.getBoolean("turbo", false)) {
            return human ? HUMAN : CUSTOM;
        }
        boolean collect = p.getBoolean(TurboParts.SCOPE_COLLECT, true);
        boolean test = p.getBoolean(TurboParts.SCOPE_TEST, true);
        if (collect && test) {
            return TURBO;
        }
        if (collect && human) {
            return HYBRID;
        }
        return CUSTOM;
    }

    public static void apply(SharedPreferences p, int mode) {
        SharedPreferences.Editor e = p.edit();
        switch (mode) {
            case HUMAN:
                e.putBoolean("turbo", false).putBoolean("human_like", true);
                break;
            case HYBRID:
                e.putBoolean("turbo", true).putBoolean(TurboParts.SCOPE_COLLECT, true).putBoolean(TurboParts.SCOPE_TEST, false).putBoolean("human_like", true);
                break;
            default:
                e.putBoolean("turbo", true).putBoolean(TurboParts.SCOPE_COLLECT, true).putBoolean(TurboParts.SCOPE_TEST, true);
                break;
        }
        e.apply();
    }

    public static String title(SharedPreferences p) {
        int m = current(p);
        return m == CUSTOM ? "직접 설정" : INFO[m][0];
    }
}
