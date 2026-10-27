package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * User-adjustable scrolling used while collecting the word list (and when returning to its top).
 * Defaults reproduce the previously hard-coded behaviour. The range is capped so consecutive pages still
 * overlap; the collector relies on that overlap to avoid skipping rows.
 */
public final class ScrollSettings {
    public static final int RANGE_MIN = 20;
    public static final int RANGE_MAX = 60;
    public static final int RANGE_DEFAULT = 45;
    public static final int RANGE_STEP = 5;

    public static final int DURATION_MIN = 200;
    public static final int DURATION_MAX = 1500;
    public static final int DURATION_DEFAULT = 450;
    public static final int DURATION_STEP = 50;

    public static final int INTERVAL_MIN = 300;
    public static final int INTERVAL_MAX = 2000;
    public static final int INTERVAL_DEFAULT = 500;
    public static final int INTERVAL_STEP = 100;

    private static final String KEY_RANGE = "scroll_range_percent";
    private static final String KEY_DURATION = "scroll_duration_ms";
    private static final String KEY_INTERVAL = "scroll_interval_ms";

    private ScrollSettings() {
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Swipe length as a percentage of the list height. */
    public static int rangePercent(SharedPreferences prefs) {
        return clamp(prefs.getInt(KEY_RANGE, RANGE_DEFAULT), RANGE_MIN, RANGE_MAX);
    }

    /** How long one scroll swipe takes; longer is slower. */
    public static int durationMs(SharedPreferences prefs) {
        return clamp(prefs.getInt(KEY_DURATION, DURATION_DEFAULT), DURATION_MIN, DURATION_MAX);
    }

    /** Minimum wait between two scroll swipes. */
    public static int intervalMs(SharedPreferences prefs) {
        return clamp(prefs.getInt(KEY_INTERVAL, INTERVAL_DEFAULT), INTERVAL_MIN, INTERVAL_MAX);
    }

    public static void setRangePercent(SharedPreferences prefs, int value) {
        prefs.edit().putInt(KEY_RANGE, clamp(value, RANGE_MIN, RANGE_MAX)).apply();
    }

    public static void setDurationMs(SharedPreferences prefs, int value) {
        prefs.edit().putInt(KEY_DURATION, clamp(value, DURATION_MIN, DURATION_MAX)).apply();
    }

    public static void setIntervalMs(SharedPreferences prefs, int value) {
        prefs.edit().putInt(KEY_INTERVAL, clamp(value, INTERVAL_MIN, INTERVAL_MAX)).apply();
    }

    public static void reset(SharedPreferences prefs) {
        prefs.edit().remove(KEY_RANGE).remove(KEY_DURATION).remove(KEY_INTERVAL).apply();
    }

    /** Vertical start/end of a forward scroll as fractions of the list height, centred where the old fixed one was. */
    public static float[] fractions(int rangePercent, boolean forward) {
        float half = rangePercent / 200.0f;
        float centre = 0.55f;
        float high = centre + half;
        float low = centre - half;
        return forward ? new float[]{high, low} : new float[]{low, high};
    }
}
