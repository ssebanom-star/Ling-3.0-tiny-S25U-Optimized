package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Does measuring a person (here: simulated with known numbers) give those numbers back? */
public class PaceModelTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean cond) { if (cond) pass++; else { fail++; System.out.println("FAIL " + name); } }

    static List<PaceModel.Think> person(Random r, int n, int mean, double sigma, double hesitation, double fatigue) {
        List<PaceModel.Think> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int p = 4 + r.nextInt(20);
            int c = 20 + r.nextInt(60);
            out.add(new PaceModel.Think(HumanTiming.thinkMillis(r, mean, p, c, i, sigma, hesitation, fatigue), p, c));
        }
        return out;
    }

    static List<Long> around(Random r, int n, double median) {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(HumanTiming.pauseMillis(r, median));
        return out;
    }

    public static void main(String[] a) {
        Random r = new Random(3);
        List<Long> none = new ArrayList<>();

        // with plenty of answers the numbers come back
        PaceModel.Result big = PaceModel.summarize(person(r, 300, 2000, 0.30, 0.0, 0.0), around(r, 100, 800), around(r, 100, 500), around(new Random(9), 1, 2500));
        ok("mean recovered (300 answers)", Math.abs(big.meanMs - 2000) <= 250);
        ok("spread recovered", Math.abs(big.sigmaPct - 30) <= 8);
        ok("no hesitation measured", big.hesitatePct <= 8);
        ok("reveal pause recovered", Math.abs(big.revealMs - 800) <= 120);
        ok("advance pause recovered", Math.abs(big.advanceMs - 500) <= 100);
        ok("start wait kept", big.navMs >= 300);

        // hesitation and tiredness are picked up
        PaceModel.Result hes = PaceModel.summarize(person(new Random(4), 400, 1500, 0.30, 0.20, 0.0), none, none, none);
        ok("hesitation noticed", hes.hesitatePct >= 10);
        PaceModel.Result tired = PaceModel.summarize(person(new Random(5), 40, 1500, 0.15, 0.0, 0.006), none, none, none);
        ok("slowing down noticed", tired.fatiguePermille >= 3);

        // the 8 answers of a real measurement: sensible, bounded numbers
        for (int seed = 0; seed < 50; seed++) {
            PaceModel.Result small = PaceModel.summarize(person(new Random(seed), 8, 1800, 0.35, 0.06, 0.003), around(new Random(seed), 7, 700), around(new Random(seed + 1), 7, 600), around(new Random(seed + 2), 1, 2000));
            boolean sane = small.meanMs >= HumanTiming.MEAN_MIN && small.meanMs <= HumanTiming.MEAN_MAX
                    && small.sigmaPct >= 15 && small.sigmaPct <= 70 && small.hesitatePct >= 0 && small.hesitatePct <= 25
                    && small.fatiguePermille >= 0 && small.fatiguePermille <= 8;
            ok("8-answer result sane (seed " + seed + ")", sane);
        }

        // too little data leaves the built-in rules alone
        PaceModel.Result three = PaceModel.summarize(person(new Random(6), 3, 1500, 0.3, 0, 0), around(r, 1, 700), around(r, 1, 500), none);
        ok("3 answers: spread not claimed", !three.spreadMeasured && three.sigmaPct == HumanProfile.SIGMA_DEFAULT);
        ok("3 answers: fatigue kept", three.fatiguePermille == HumanProfile.FATIGUE_DEFAULT);
        ok("one pause sample is not enough", three.revealMs == -1 && three.advanceMs == -1);
        ok("no start press measured", three.navMs == -1);
        PaceModel.Result empty = PaceModel.summarize(new ArrayList<PaceModel.Think>(), none, none, none);
        ok("nothing measured", empty.answers == 0 && empty.meanMs == HumanTiming.MEAN_DEFAULT);

        // the description is short and readable
        PaceModel.Result d = new PaceModel.Result();
        d.meanMs = 1500; d.advanceMs = 800; d.revealMs = 600; d.navMs = 2000; d.spreadMeasured = true; d.sigmaPct = 35; d.hesitatePct = 8;
        ok("description", d.describe().equals("응답 1.5초 · 넘김 0.8초 · 보기 열기 0.6초 · 시작 누르기 2초 · 변동 ±35% · 망설임 8%"));

        // the bot side: new parameters change the timing, defaults leave it alone
        Random x1 = new Random(1), x2 = new Random(1);
        long same1 = HumanTiming.thinkMillis(x1, 2000, 8, 48, 5);
        long same2 = HumanTiming.thinkMillis(x2, 2000, 8, 48, 5, 0.35, 0.06, 0.003);
        ok("defaults equal the old rule", same1 == same2);
        long wide = 0, narrow = 0;
        Random w = new Random(21), n = new Random(22);
        for (int i = 0; i < 3000; i++) {
            wide += Math.abs(HumanTiming.thinkMillis(w, 2000, 8, 48, 0, 0.7, 0.0, 0.0) - 1700);
            narrow += Math.abs(HumanTiming.thinkMillis(n, 2000, 8, 48, 0, 0.1, 0.0, 0.0) - 1700);
        }
        ok("a wider spread varies more", wide > narrow * 2);
        Random stream = new Random(77);
        long[] pauses = new long[3001];
        for (int i = 0; i < pauses.length; i++) pauses[i] = HumanTiming.pauseMillis(stream, 900);
        java.util.Arrays.sort(pauses);
        ok("pause median respected", Math.abs(pauses[1500] - 900) < 60);
        ok("no start wait when 0", HumanTiming.navMillis(new Random(1), 0) == 0);
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
