package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * User-adjustable values that used to be fixed numbers in the code: how strict answer matching is, and the limits
 * that stop a run. Every default equals the previous hard-coded value, so changing nothing changes nothing.
 */
public final class AdvancedSettings {
    private AdvancedSettings() {
    }

    // ---- answer matching ----
    /** Minimum similarity (percent) for a misread prompt/choice to still count. 100 = only exact after OCR folding. */
    public static final int FUZZY_MIN = 50, FUZZY_MAX = 100, FUZZY_DEFAULT = 80, FUZZY_STEP = 5;
    /** How far (percent points) the best choice must lead the runner-up when their strengths tie. */
    public static final int TIE_MIN = 0, TIE_MAX = 30, TIE_DEFAULT = 8, TIE_STEP = 1;
    /** Letters a cut-off choice must share with the answer to be picked by partial matching. */
    public static final int PARTIAL_MIN = 1, PARTIAL_MAX = 6, PARTIAL_DEFAULT = 2, PARTIAL_STEP = 1;

    // ---- run limits ----
    /** Longest a single run may last, in seconds. */
    public static final int RUN_MIN = 120, RUN_MAX = 3600, RUN_DEFAULT = 600, RUN_STEP = 60;
    /** Longest the app waits for the screen to change to the next step, in seconds. */
    public static final int PHASE_MIN = 10, PHASE_MAX = 120, PHASE_DEFAULT = 35, PHASE_STEP = 5;
    /** Most scroll steps while collecting the word list. */
    public static final int SCROLL_MIN = 40, SCROLL_MAX = 400, SCROLL_DEFAULT = 160, SCROLL_STEP = 20;
    /** How many times a tap that did not register is repeated before giving up. */
    public static final int RETRY_MIN = 1, RETRY_MAX = 5, RETRY_DEFAULT = 2, RETRY_STEP = 1;
    /** How long a card must hold still (ms) before it is swiped. */
    public static final int GATE_MIN = 300, GATE_MAX = 1500, GATE_DEFAULT = 650, GATE_STEP = 50;

    /** How many touches by the user during the test pause the run. */
    public static final int TOUCH_MIN = 1, TOUCH_MAX = 5, TOUCH_DEFAULT = 2, TOUCH_STEP = 1;

    /** How long the run waits on its own (ClassCard out of sight, screen locked) before it gives up, in seconds. */
    public static final int HOLD_MIN = 30, HOLD_MAX = 600, HOLD_DEFAULT = 180, HOLD_STEP = 30;

    // ---- deliberate mistakes ----
    /** Longest time from a question appearing to answering it (s); keep it under ClassCard's own per-question limit. */
    public static final int QCAP_MIN = 2, QCAP_MAX = 20, QCAP_DEFAULT = 5, QCAP_STEP = 1;
    private static final String K_QCAP = "limit_question_seconds";

    public static int questionCapSeconds(SharedPreferences p) {
        return clamp(p.getInt(K_QCAP, QCAP_DEFAULT), QCAP_MIN, QCAP_MAX);
    }

    public static void setQuestionCapSeconds(SharedPreferences p, int v) {
        p.edit().putInt(K_QCAP, clamp(v, QCAP_MIN, QCAP_MAX)).apply();
    }

    public static final int WRONG_MIN = 0, WRONG_MAX = 20, WRONG_DEFAULT = 0, WRONG_STEP = 1;

    private static final String K_FUZZY = "match_fuzzy_percent";
    private static final String K_TIE = "match_tie_percent";
    private static final String K_PARTIAL = "match_partial_min";
    private static final String K_RUN = "limit_run_seconds";
    private static final String K_PHASE = "limit_phase_seconds";
    private static final String K_SCROLL = "limit_scroll_steps";
    private static final String K_RETRY = "limit_tap_retries";
    private static final String K_GATE = "limit_gate_ms";
    private static final String K_WRONG = "wrong_count";
    private static final String K_TOUCH = "limit_touch_pause";
    private static final String K_HOLD = "limit_hold_seconds";

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public static int fuzzyPercent(SharedPreferences p) {
        return clamp(p.getInt(K_FUZZY, FUZZY_DEFAULT), FUZZY_MIN, FUZZY_MAX);
    }

    public static int tiePercent(SharedPreferences p) {
        return clamp(p.getInt(K_TIE, TIE_DEFAULT), TIE_MIN, TIE_MAX);
    }

    public static int partialMin(SharedPreferences p) {
        return clamp(p.getInt(K_PARTIAL, PARTIAL_DEFAULT), PARTIAL_MIN, PARTIAL_MAX);
    }

    public static int runLimitSeconds(SharedPreferences p) {
        return clamp(p.getInt(K_RUN, RUN_DEFAULT), RUN_MIN, RUN_MAX);
    }

    public static int phaseSeconds(SharedPreferences p) {
        return clamp(p.getInt(K_PHASE, PHASE_DEFAULT), PHASE_MIN, PHASE_MAX);
    }

    public static int scrollSteps(SharedPreferences p) {
        return clamp(p.getInt(K_SCROLL, SCROLL_DEFAULT), SCROLL_MIN, SCROLL_MAX);
    }

    public static int tapRetries(SharedPreferences p) {
        return clamp(p.getInt(K_RETRY, RETRY_DEFAULT), RETRY_MIN, RETRY_MAX);
    }

    public static int gateMs(SharedPreferences p) {
        return clamp(p.getInt(K_GATE, GATE_DEFAULT), GATE_MIN, GATE_MAX);
    }

    public static int touchPauseCount(SharedPreferences p) {
        return clamp(p.getInt(K_TOUCH, TOUCH_DEFAULT), TOUCH_MIN, TOUCH_MAX);
    }

    public static void setTouchPauseCount(SharedPreferences p, int v) {
        p.edit().putInt(K_TOUCH, clamp(v, TOUCH_MIN, TOUCH_MAX)).apply();
    }

    public static int holdSeconds(SharedPreferences p) {
        return clamp(p.getInt(K_HOLD, HOLD_DEFAULT), HOLD_MIN, HOLD_MAX);
    }

    public static void setHoldSeconds(SharedPreferences p, int v) {
        p.edit().putInt(K_HOLD, clamp(v, HOLD_MIN, HOLD_MAX)).apply();
    }

    public static int wrongCount(SharedPreferences p) {
        return clamp(p.getInt(K_WRONG, WRONG_DEFAULT), WRONG_MIN, WRONG_MAX);
    }

    public static void setFuzzyPercent(SharedPreferences p, int v) {
        p.edit().putInt(K_FUZZY, clamp(v, FUZZY_MIN, FUZZY_MAX)).apply();
    }

    public static void setTiePercent(SharedPreferences p, int v) {
        p.edit().putInt(K_TIE, clamp(v, TIE_MIN, TIE_MAX)).apply();
    }

    public static void setPartialMin(SharedPreferences p, int v) {
        p.edit().putInt(K_PARTIAL, clamp(v, PARTIAL_MIN, PARTIAL_MAX)).apply();
    }

    public static void setRunLimitSeconds(SharedPreferences p, int v) {
        p.edit().putInt(K_RUN, clamp(v, RUN_MIN, RUN_MAX)).apply();
    }

    public static void setPhaseSeconds(SharedPreferences p, int v) {
        p.edit().putInt(K_PHASE, clamp(v, PHASE_MIN, PHASE_MAX)).apply();
    }

    public static void setScrollSteps(SharedPreferences p, int v) {
        p.edit().putInt(K_SCROLL, clamp(v, SCROLL_MIN, SCROLL_MAX)).apply();
    }

    public static void setTapRetries(SharedPreferences p, int v) {
        p.edit().putInt(K_RETRY, clamp(v, RETRY_MIN, RETRY_MAX)).apply();
    }

    public static void setGateMs(SharedPreferences p, int v) {
        p.edit().putInt(K_GATE, clamp(v, GATE_MIN, GATE_MAX)).apply();
    }

    public static void setWrongCount(SharedPreferences p, int v) {
        p.edit().putInt(K_WRONG, clamp(v, WRONG_MIN, WRONG_MAX)).apply();
    }

    public static void resetMatching(SharedPreferences p) {
        p.edit().remove(K_FUZZY).remove(K_TIE).remove(K_PARTIAL).apply();
    }

    public static void resetLimits(SharedPreferences p) {
        p.edit().remove(K_QCAP).remove(K_RUN).remove(K_PHASE).remove(K_SCROLL).remove(K_RETRY).remove(K_GATE).remove(K_TOUCH).remove(K_HOLD).apply();
    }

    /** Pushes the matching settings into {@link Matcher}; called when a run's solver is created. */
    public static void applyMatching(SharedPreferences p) {
        Matcher.configure(fuzzyPercent(p) / 100.0, tiePercent(p) / 100.0, partialMin(p));
    }
}
