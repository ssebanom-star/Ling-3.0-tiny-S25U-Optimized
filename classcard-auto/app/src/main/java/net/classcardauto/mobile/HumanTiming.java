package net.classcardauto.mobile;

import java.util.Random;

/**
 * Timing and touch variation that makes the automated input resemble a person answering by hand.
 * Pure functions over an injected {@link Random}, so the distributions can be tested without a device.
 *
 * Model (all values are choices made for plausibility, not measurements of real users):
 *  - Thinking time before an answer is log-normal (right-skewed like human response times), scaled by how much text
 *    has to be read, slowing slightly as the run goes on, with occasional longer hesitations.
 *  - A tap lands somewhere inside the answer row (gaussian around its centre) and lasts 55-130 ms.
 *  - Swipes vary in length of time and in the exact path.
 */
public final class HumanTiming {
    public static final int MEAN_MIN = 500;
    public static final int MEAN_MAX = 8000;
    public static final int MEAN_DEFAULT = 1000;
    public static final int MEAN_STEP = 250;

    static final long THINK_MIN_MS = 200;
    static final long THINK_MAX_MS = 20000;
    private static final double THINK_SIGMA = 0.35;
    private static final double HESITATION_CHANCE = 0.06;

    private HumanTiming() {
    }

    public static double gauss(Random r) {
        return r.nextGaussian();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Delay between the choices appearing and the tap.
     *
     * @param meanMs        typical response time for an average-length question
     * @param promptChars   characters in the prompt
     * @param choiceChars   characters across all choices
     * @param questionIndex 0-based index of the question in this run
     */
    public static long thinkMillis(Random r, int meanMs, int promptChars, int choiceChars, int questionIndex) {
        double lengthFactor = lengthFactor(promptChars, choiceChars);
        double median = meanMs * 0.85 * lengthFactor;
        double value = median * Math.exp(THINK_SIGMA * gauss(r));
        value *= 1.0 + Math.min(0.2, 0.003 * Math.max(0, questionIndex));
        if (r.nextDouble() < HESITATION_CHANCE) {
            value += 800 + r.nextDouble() * 1400;
        }
        return (long) clamp(value, THINK_MIN_MS, THINK_MAX_MS);
    }

    /** How much longer a question takes to read than an average one (1.0): grows with the text, 0.7..1.8. */
    public static double lengthFactor(int promptChars, int choiceChars) {
        return clamp(0.6 + 0.25 * (promptChars / 8.0) + 0.15 * (choiceChars / 48.0), 0.7, 1.8);
    }

    /**
     * The "average response time" setting that would make {@link #thinkMillis} give about {@code observedMs} for a
     * question of this length, i.e. the inverse of the median formula (the 1.063 is the log-normal mean/median ratio).
     */
    public static double meanFromObserved(long observedMs, int promptChars, int choiceChars) {
        return observedMs / (0.85 * lengthFactor(promptChars, choiceChars) * 1.063);
    }

    /** A setting value: rounded to the slider step and kept inside its range. */
    public static int snapMean(double mean) {
        long steps = Math.round(mean / MEAN_STEP);
        return (int) clamp(steps * MEAN_STEP, MEAN_MIN, MEAN_MAX);
    }

    /**
     * Rough time one question takes from the first look to the next card: thinking, the pause before the swipe, and
     * the two waits for the card to hold still plus screen changes. The constants are estimates, not measurements.
     */
    public static long estimatePerQuestionMs(boolean human, int meanMs, int gateMs) {
        double overhead = 2.0 * gateMs + 500;
        if (!human) {
            return (long) overhead;
        }
        double think = 1.03 * (0.904 * meanMs + 90);
        double advance = 0.27 * meanMs;
        return (long) (think + advance + overhead);
    }

    /** Pause after the answer was accepted before the card is swiped on. */
    public static long advanceMillis(Random r, int meanMs) {
        double median = 0.25 * meanMs;
        double value = median * Math.exp(0.4 * gauss(r));
        return (long) clamp(value, 120, 4000);
    }

    /** A touch point inside the rectangle, clustered around the middle and never within the outer ~12% of the edge. */
    public static float[] tapPoint(Random r, float left, float top, float right, float bottom) {
        float w = right - left;
        float h = bottom - top;
        double dx = clamp(gauss(r) * 0.16 * w, -0.38 * w, 0.38 * w);
        double dy = clamp(gauss(r) * 0.16 * h, -0.38 * h, 0.38 * h);
        return new float[]{(float) (left + w / 2 + dx), (float) (top + h / 2 + dy)};
    }

    public static long tapDurationMs(Random r) {
        return (long) clamp(85 + gauss(r) * 20, 55, 130);
    }

    /** A swipe lasting roughly {@code baseMs}, plus or minus about 15%. */
    public static long swipeDurationMs(Random r, long baseMs) {
        return (long) clamp(baseMs * (1.0 + gauss(r) * 0.08), baseMs * 0.75, baseMs * 1.3);
    }

    /** Offset in [-amplitude, amplitude], bell-shaped. */
    public static float jitter(Random r, float amplitude) {
        return (float) clamp(gauss(r) * amplitude / 2.5, -amplitude, amplitude);
    }
}
