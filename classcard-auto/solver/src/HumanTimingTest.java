package net.classcardauto.mobile;

import java.util.Arrays;
import java.util.Random;

public class HumanTimingTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    public static void main(String[] a) {
        Random r = new Random(42);
        int n = 20000;
        long[] t = new long[n];
        for (int i = 0; i < n; i++) {
            t[i] = HumanTiming.thinkMillis(r, 3000, 8, 48, 0);
        }
        Arrays.sort(t);
        long median = t[n / 2], p10 = t[n / 10], p90 = t[n * 9 / 10];
        System.out.println("think median=" + median + " p10=" + p10 + " p90=" + p90 + " min=" + t[0] + " max=" + t[n - 1]);
        ok("median near 0.85*mean", Math.abs(median - 2550) < 200);
        ok("right skewed (p90-med > med-p10)", (p90 - median) > (median - p10));
        ok("bounded below", t[0] >= HumanTiming.THINK_MIN_MS);
        ok("bounded above", t[n - 1] <= HumanTiming.THINK_MAX_MS);
        ok("not constant", p90 - p10 > 600);

        long shortQ = 0, longQ = 0;
        for (int i = 0; i < 5000; i++) {
            shortQ += HumanTiming.thinkMillis(r, 3000, 3, 20, 0);
            longQ += HumanTiming.thinkMillis(r, 3000, 20, 120, 0);
        }
        ok("longer text takes longer", longQ > shortQ * 1.3);

        long early = 0, late = 0;
        for (int i = 0; i < 5000; i++) {
            early += HumanTiming.thinkMillis(r, 3000, 8, 48, 0);
            late += HumanTiming.thinkMillis(r, 3000, 8, 48, 60);
        }
        ok("slows slightly with fatigue", late > early && late < early * 1.35);

        boolean inside = true;
        double sx = 0;
        for (int i = 0; i < 10000; i++) {
            float[] p = HumanTiming.tapPoint(r, 100, 500, 900, 620);
            inside &= p[0] > 100 + 0.1 * 800 && p[0] < 900 - 0.1 * 800 && p[1] > 500 + 0.1 * 120 && p[1] < 620 - 0.1 * 120;
            sx += p[0];
        }
        ok("taps stay inside the row, away from edges", inside);
        ok("taps centred", Math.abs(sx / 10000 - 500) < 10);

        boolean durOk = true;
        for (int i = 0; i < 5000; i++) {
            long d = HumanTiming.tapDurationMs(r);
            durOk &= d >= 55 && d <= 130;
            long s = HumanTiming.swipeDurationMs(r, 250);
            durOk &= s >= 187 && s <= 325;
        }
        ok("tap/swipe durations bounded", durOk);

        long adv = 0;
        for (int i = 0; i < 5000; i++) {
            long v = HumanTiming.advanceMillis(r, 3000);
            ok("advance bounded", v >= 120 && v <= 4000);
            adv += v;
        }
        ok("advance around 0.75s", Math.abs(adv / 5000.0 - 800) < 150);
        {
            long[] lo = new long[3000];
            long[] fast = new long[3000];
            boolean bounded = true;
            for (int i = 0; i < 3000; i++) {
                lo[i] = HumanTiming.thinkMillis(r, 500, 8, 48, 0);
                fast[i] = HumanTiming.thinkMillis(r, 1000, 8, 48, 0);
                bounded &= lo[i] >= 200 && fast[i] >= 200;
            }
            java.util.Arrays.sort(lo);
            java.util.Arrays.sort(fast);
            ok("0.5s setting: median under 0.8s", lo[1500] < 800);
            ok("1s setting: median under 1.3s", fast[1500] < 1300);
            ok("fast settings bounded below", bounded);
            ok("0.5s faster than 1s", lo[1500] < fast[1500]);
        }
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
