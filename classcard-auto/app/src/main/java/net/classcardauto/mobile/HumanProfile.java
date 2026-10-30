package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * The tunable numbers of the human-like mode beyond the average response time. Every default equals the rule that was
 * built in before they could be set, so changing nothing changes nothing.
 */
public final class HumanProfile {
    private HumanProfile() {
    }

    /** Spread of response times (log-normal sigma, percent). */
    public static final int SIGMA_MIN = 10, SIGMA_MAX = 80, SIGMA_DEFAULT = 35, SIGMA_STEP = 5;
    /** Share of questions with an extra hesitation, percent. */
    public static final int HESITATE_MIN = 0, HESITATE_MAX = 30, HESITATE_DEFAULT = 6, HESITATE_STEP = 1;
    /** Slow-down per question as the run goes on, per mille (capped at +20% in total). */
    public static final int FATIGUE_MIN = 0, FATIGUE_MAX = 10, FATIGUE_DEFAULT = 3, FATIGUE_STEP = 1;
    /** Pause before the card is swiped on / before the choices are opened, ms; -1 = derived from the average response time. */
    public static final int PAUSE_MIN = 100, PAUSE_MAX = 5000, PAUSE_STEP = 100;
    /** Wait before pressing a start button, ms; -1 = no wait. */
    public static final int NAV_MIN = 0, NAV_MAX = 15000, NAV_STEP = 250;

    public static final String K_SIGMA = "human_sigma_pct";
    public static final String K_HESITATE = "human_hesitate_pct";
    public static final String K_FATIGUE = "human_fatigue_permille";
    public static final String K_ADVANCE = "human_advance_ms";
    public static final String K_REVEAL = "human_reveal_ms";
    public static final String K_NAV = "human_nav_ms";
    public static final String K_TEXT = "human_profile_text";

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static double sigma(SharedPreferences p) {
        return clamp(p.getInt(K_SIGMA, SIGMA_DEFAULT), SIGMA_MIN, SIGMA_MAX) / 100.0;
    }

    public static double hesitation(SharedPreferences p) {
        return clamp(p.getInt(K_HESITATE, HESITATE_DEFAULT), HESITATE_MIN, HESITATE_MAX) / 100.0;
    }

    public static double fatiguePerQuestion(SharedPreferences p) {
        return clamp(p.getInt(K_FATIGUE, FATIGUE_DEFAULT), FATIGUE_MIN, FATIGUE_MAX) / 1000.0;
    }

    /** Pause after an accepted answer; the built-in rule is a quarter of the average response time. */
    public static long advanceMs(SharedPreferences p, int meanMs) {
        int v = p.getInt(K_ADVANCE, -1);
        return v < 0 ? Math.round(0.25 * meanMs) : clamp(v, PAUSE_MIN, PAUSE_MAX);
    }

    /** Pause before the choices are opened; the built-in rule shares the advance pause. */
    public static long revealMs(SharedPreferences p, int meanMs) {
        int v = p.getInt(K_REVEAL, -1);
        return v < 0 ? advanceMs(p, meanMs) : clamp(v, PAUSE_MIN, PAUSE_MAX);
    }

    public static long navMs(SharedPreferences p) {
        int v = p.getInt(K_NAV, -1);
        return v < 0 ? 0 : clamp(v, NAV_MIN, NAV_MAX);
    }

    /** Stores a measurement. Values left at -1 keep the built-in rule. */
    public static void apply(SharedPreferences p, PaceModel.Result r) {
        SharedPreferences.Editor e = p.edit();
        e.putInt("human_mean_ms", r.meanMs);
        e.putInt(K_SIGMA, r.sigmaPct);
        e.putInt(K_HESITATE, r.hesitatePct);
        e.putInt(K_FATIGUE, r.fatiguePermille);
        if (r.advanceMs >= 0) {
            e.putInt(K_ADVANCE, r.advanceMs);
        }
        if (r.revealMs >= 0) {
            e.putInt(K_REVEAL, r.revealMs);
        }
        if (r.navMs >= 0) {
            e.putInt(K_NAV, r.navMs);
        }
        e.putString(K_TEXT, r.describe());
        e.apply();
    }

    public static String describe(SharedPreferences p) {
        return p.getString(K_TEXT, "");
    }

    /** Back to the built-in rules (the average response time is left as it is). */
    public static void reset(SharedPreferences p) {
        p.edit().remove(K_SIGMA).remove(K_HESITATE).remove(K_FATIGUE).remove(K_ADVANCE).remove(K_REVEAL).remove(K_NAV).remove(K_TEXT).apply();
    }
}
