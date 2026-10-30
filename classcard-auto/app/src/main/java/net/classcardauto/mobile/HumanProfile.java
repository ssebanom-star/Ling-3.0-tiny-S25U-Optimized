package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * The tunable numbers of the human-like mode, kept in two separate sets that are used one at a time:
 * <ul>
 *   <li><b>default</b> ("평균·랜덤"): the average response time and details set by hand (keys {@code human_*});</li>
 *   <li><b>mine</b> ("내 패턴"): what was measured from the user's own play (keys {@code mine_*}).</li>
 * </ul>
 * {@code human_source} says which set is in use; switching never overwrites the other set. Every default equals the
 * rule that was built in before these could be set, so changing nothing changes nothing.
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

    // ---- the set made by hand ("평균·랜덤")
    public static final String K_MEAN = "human_mean_ms";
    public static final String K_SIGMA = "human_sigma_pct";
    public static final String K_HESITATE = "human_hesitate_pct";
    public static final String K_FATIGUE = "human_fatigue_permille";
    public static final String K_ADVANCE = "human_advance_ms";
    public static final String K_REVEAL = "human_reveal_ms";
    public static final String K_NAV = "human_nav_ms";

    // ---- the measured set ("내 패턴"), never touched by the sliders above
    public static final String M_SAVED = "mine_saved";
    public static final String M_MEAN = "mine_mean_ms";
    public static final String M_SIGMA = "mine_sigma_pct";
    public static final String M_HESITATE = "mine_hesitate_pct";
    public static final String M_FATIGUE = "mine_fatigue_permille";
    public static final String M_ADVANCE = "mine_advance_ms";
    public static final String M_REVEAL = "mine_reveal_ms";
    public static final String M_NAV = "mine_nav_ms";
    public static final String M_TEXT = "mine_profile_text";

    /** "default" or "mine". */
    public static final String K_SOURCE = "human_source";

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ which set is in use

    public static boolean hasMine(SharedPreferences p) {
        return p.getBoolean(M_SAVED, false);
    }

    /** True when the measured set is chosen and exists. */
    public static boolean usingMine(SharedPreferences p) {
        return "mine".equals(p.getString(K_SOURCE, "default")) && hasMine(p);
    }

    /** Chooses the set; asking for the measured set when there is none is refused (returns false). */
    public static boolean setUseMine(SharedPreferences p, boolean mine) {
        if (mine && !hasMine(p)) {
            return false;
        }
        p.edit().putString(K_SOURCE, mine ? "mine" : "default").apply();
        return true;
    }

    // ------------------------------------------------------------------ values of one set

    public static int meanMs(SharedPreferences p, boolean mine) {
        return clamp(p.getInt(mine ? M_MEAN : K_MEAN, HumanTiming.MEAN_DEFAULT), HumanTiming.MEAN_MIN, HumanTiming.MEAN_MAX);
    }

    public static int sigmaPct(SharedPreferences p, boolean mine) {
        return clamp(p.getInt(mine ? M_SIGMA : K_SIGMA, SIGMA_DEFAULT), SIGMA_MIN, SIGMA_MAX);
    }

    public static int hesitatePct(SharedPreferences p, boolean mine) {
        return clamp(p.getInt(mine ? M_HESITATE : K_HESITATE, HESITATE_DEFAULT), HESITATE_MIN, HESITATE_MAX);
    }

    public static int fatiguePermille(SharedPreferences p, boolean mine) {
        return clamp(p.getInt(mine ? M_FATIGUE : K_FATIGUE, FATIGUE_DEFAULT), FATIGUE_MIN, FATIGUE_MAX);
    }

    /** Pause after an accepted answer; unset, it is a quarter of that set's average response time. */
    public static long advanceMs(SharedPreferences p, boolean mine) {
        int v = p.getInt(mine ? M_ADVANCE : K_ADVANCE, -1);
        return v < 0 ? Math.round(0.25 * meanMs(p, mine)) : clamp(v, PAUSE_MIN, PAUSE_MAX);
    }

    /** Pause before the choices are opened; unset, it shares the advance pause. */
    public static long revealMs(SharedPreferences p, boolean mine) {
        int v = p.getInt(mine ? M_REVEAL : K_REVEAL, -1);
        return v < 0 ? advanceMs(p, mine) : clamp(v, PAUSE_MIN, PAUSE_MAX);
    }

    public static long navMs(SharedPreferences p, boolean mine) {
        int v = p.getInt(mine ? M_NAV : K_NAV, -1);
        return v < 0 ? 0 : clamp(v, NAV_MIN, NAV_MAX);
    }

    // ------------------------------------------------------------------ values of the set in use (what the bot reads)

    public static int meanMs(SharedPreferences p) {
        return meanMs(p, usingMine(p));
    }

    public static double sigma(SharedPreferences p) {
        return sigmaPct(p, usingMine(p)) / 100.0;
    }

    public static double hesitation(SharedPreferences p) {
        return hesitatePct(p, usingMine(p)) / 100.0;
    }

    public static double fatiguePerQuestion(SharedPreferences p) {
        return fatiguePermille(p, usingMine(p)) / 1000.0;
    }

    public static long advanceMs(SharedPreferences p) {
        return advanceMs(p, usingMine(p));
    }

    public static long revealMs(SharedPreferences p) {
        return revealMs(p, usingMine(p));
    }

    public static long navMs(SharedPreferences p) {
        return navMs(p, usingMine(p));
    }

    // ------------------------------------------------------------------ saving / clearing

    /** Stores a measurement as the measured set (the hand-made set is left alone) and switches to it. */
    public static void saveMine(SharedPreferences p, PaceModel.Result r) {
        SharedPreferences.Editor e = p.edit();
        e.putBoolean(M_SAVED, true);
        e.putInt(M_MEAN, r.meanMs);
        e.putInt(M_SIGMA, r.sigmaPct);
        e.putInt(M_HESITATE, r.hesitatePct);
        e.putInt(M_FATIGUE, r.fatiguePermille);
        for (String key : new String[]{M_ADVANCE, M_REVEAL, M_NAV}) {
            e.remove(key);
        }
        if (r.advanceMs >= 0) {
            e.putInt(M_ADVANCE, r.advanceMs);
        }
        if (r.revealMs >= 0) {
            e.putInt(M_REVEAL, r.revealMs);
        }
        if (r.navMs >= 0) {
            e.putInt(M_NAV, r.navMs);
        }
        e.putString(M_TEXT, r.describe());
        e.putString(K_SOURCE, "mine");
        e.apply();
    }

    public static String describeMine(SharedPreferences p) {
        return hasMine(p) ? p.getString(M_TEXT, "") : "";
    }

    /** Deletes the measured set and goes back to the hand-made one. */
    public static void clearMine(SharedPreferences p) {
        p.edit().remove(M_SAVED).remove(M_MEAN).remove(M_SIGMA).remove(M_HESITATE).remove(M_FATIGUE).remove(M_ADVANCE).remove(M_REVEAL).remove(M_NAV).remove(M_TEXT)
                .putString(K_SOURCE, "default").apply();
    }

    /** The hand-made details back to the built-in rules (the average response time is left as it is). */
    public static void resetDefaults(SharedPreferences p) {
        p.edit().remove(K_SIGMA).remove(K_HESITATE).remove(K_FATIGUE).remove(K_ADVANCE).remove(K_REVEAL).remove(K_NAV).apply();
    }
}
