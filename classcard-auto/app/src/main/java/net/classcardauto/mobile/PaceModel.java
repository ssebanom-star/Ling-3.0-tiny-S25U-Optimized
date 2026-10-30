package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns what was observed while the user took a test by hand into the settings of the human-like mode. Pure maths, no
 * Android types, so it can be tested on a JVM. Everything is held in memory only; only the resulting numbers are kept.
 */
public final class PaceModel {
    private PaceModel() {
    }

    /** The screen only changes a moment after a finger lifts; this much of each measured wait is the app's reaction. */
    public static final long TAP_LATENCY_MS = 150;

    /** One answered question: the time to answer and how much text had to be read. */
    public static final class Think {
        public final long ms;
        public final int promptChars;
        public final int choiceChars;

        public Think(long ms, int promptChars, int choiceChars) {
            this.ms = ms;
            this.promptChars = promptChars;
            this.choiceChars = choiceChars;
        }
    }

    /** Values for the settings; -1 means "not measured, keep the built-in rule". */
    public static final class Result {
        public int meanMs;
        public int sigmaPct = HumanProfile.SIGMA_DEFAULT;
        public int hesitatePct = HumanProfile.HESITATE_DEFAULT;
        public int fatiguePermille = HumanProfile.FATIGUE_DEFAULT;
        public int advanceMs = -1;
        public int revealMs = -1;
        public int navMs = -1;
        public int answers;
        public boolean spreadMeasured;

        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("응답 ").append(AppStyleFormat.seconds(meanMs));
            if (advanceMs >= 0) {
                sb.append(" · 넘김 ").append(AppStyleFormat.seconds(advanceMs));
            }
            if (revealMs >= 0) {
                sb.append(" · 보기 열기 ").append(AppStyleFormat.seconds(revealMs));
            }
            if (navMs >= 0) {
                sb.append(" · 시작 누르기 ").append(AppStyleFormat.seconds(navMs));
            }
            if (spreadMeasured) {
                sb.append(" · 변동 ±").append(sigmaPct).append("% · 망설임 ").append(hesitatePct).append("%");
            }
            return sb.toString();
        }
    }

    /** Formats milliseconds as seconds without needing Android (same look as the sliders). */
    static final class AppStyleFormat {
        static String seconds(int ms) {
            double s = ms / 1000.0;
            String t = String.format(java.util.Locale.US, "%.2f", s);
            if (t.endsWith("0")) {
                t = t.substring(0, t.length() - 1);
            }
            if (t.endsWith(".0")) {
                t = t.substring(0, t.length() - 2);
            }
            return t + "초";
        }
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static int clamp(long v, int lo, int hi) {
        return (int) Math.max(lo, Math.min(hi, v));
    }

    private static int medianMs(List<Long> values, int lo, int hi) {
        List<Double> d = new ArrayList<>();
        for (Long v : values) {
            d.add(v.doubleValue());
        }
        return clamp(Math.round(median(d)), lo, hi);
    }

    /**
     * @param think   answered questions (at least 3 to produce anything)
     * @param reveal  waits between the question appearing and its choices being opened
     * @param advance waits between an answer being accepted and the next card
     * @param nav     waits before pressing a start button (set screen, preview)
     */
    public static Result summarize(List<Think> think, List<Long> reveal, List<Long> advance, List<Long> nav) {
        Result r = new Result();
        r.answers = think.size();
        if (think.isEmpty()) {
            r.meanMs = HumanTiming.MEAN_DEFAULT;
            return r;
        }
        // each answer expressed as the "average response time" setting that would have produced it
        List<Double> normalized = new ArrayList<>();
        for (Think t : think) {
            normalized.add(HumanTiming.meanFromObserved(t.ms, t.promptChars, t.choiceChars));
        }
        double med = median(normalized);
        r.meanMs = HumanTiming.snapMean(med);
        int n = normalized.size();
        if (n >= 5) {
            List<Double> deviations = new ArrayList<>();
            int slow = 0;
            for (double v : normalized) {
                deviations.add(Math.abs(Math.log(v / med)));
                if (v > 1.7 * med) {
                    slow++;
                }
            }
            r.sigmaPct = clamp(Math.round(100 * 1.4826 * median(deviations)), 15, 70);
            // blended with the built-in 6% so that one slow answer out of eight does not read as a habit
            r.hesitatePct = clamp(Math.round(100.0 * (slow + HumanProfile.HESITATE_DEFAULT / 100.0 * 4) / (n + 4)), 0, 25);
            r.spreadMeasured = true;
        }
        if (n >= 6) {
            double meanX = (n - 1) / 2.0;
            double meanY = 0;
            for (double v : normalized) {
                meanY += Math.log(v);
            }
            meanY /= n;
            double num = 0;
            double den = 0;
            for (int i = 0; i < n; i++) {
                num += (i - meanX) * (Math.log(normalized.get(i)) - meanY);
                den += (i - meanX) * (i - meanX);
            }
            r.fatiguePermille = clamp(Math.round(1000 * num / den), 0, 8);
        }
        if (advance.size() >= 2) {
            r.advanceMs = medianMs(advance, 120, 6000);
        }
        if (reveal.size() >= 2) {
            r.revealMs = medianMs(reveal, 120, 6000);
        }
        if (nav.size() >= 1) {
            r.navMs = medianMs(nav, 300, 15000);
        }
        return r;
    }
}
