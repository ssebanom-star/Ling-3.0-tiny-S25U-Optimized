package net.classcardauto.mobile;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** The two parameter sets (hand-made "평균·랜덤" and measured "내 패턴") are stored apart and used one at a time. */
public class HumanProfileTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean cond) { if (cond) pass++; else { fail++; System.out.println("FAIL " + name); } }

    static class Prefs implements SharedPreferences {
        final Map<String, Object> map = new HashMap<>();
        public Map<String, ?> getAll() { return map; }
        public String getString(String k, String d) { Object v = map.get(k); return v instanceof String ? (String) v : d; }
        public java.util.Set<String> getStringSet(String k, java.util.Set<String> d) { return d; }
        public int getInt(String k, int d) { Object v = map.get(k); return v instanceof Integer ? (Integer) v : d; }
        public long getLong(String k, long d) { return d; }
        public float getFloat(String k, float d) { return d; }
        public boolean getBoolean(String k, boolean d) { Object v = map.get(k); return v instanceof Boolean ? (Boolean) v : d; }
        public boolean contains(String k) { return map.containsKey(k); }
        public Editor edit() {
            return new Editor() {
                public Editor putString(String k, String v) { map.put(k, v); return this; }
                public Editor putStringSet(String k, java.util.Set<String> v) { return this; }
                public Editor putInt(String k, int v) { map.put(k, v); return this; }
                public Editor putLong(String k, long v) { return this; }
                public Editor putFloat(String k, float v) { return this; }
                public Editor putBoolean(String k, boolean v) { map.put(k, v); return this; }
                public Editor remove(String k) { map.remove(k); return this; }
                public Editor clear() { map.clear(); return this; }
                public boolean commit() { return true; }
                public void apply() { }
            };
        }
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    static PaceModel.Result sample() {
        PaceModel.Result r = new PaceModel.Result();
        r.meanMs = 2500; r.sigmaPct = 50; r.hesitatePct = 15; r.fatiguePermille = 6; r.advanceMs = 900; r.revealMs = 700; r.navMs = 3000; r.spreadMeasured = true;
        return r;
    }

    public static void main(String[] a) {
        Prefs p = new Prefs();
        // nothing set: the built-in rules
        ok("default mean", HumanProfile.meanMs(p) == HumanTiming.MEAN_DEFAULT);
        ok("default sigma", Math.abs(HumanProfile.sigma(p) - 0.35) < 1e-9);
        ok("default advance is a quarter of the mean", HumanProfile.advanceMs(p) == Math.round(0.25 * HumanTiming.MEAN_DEFAULT));
        ok("no wait before start buttons", HumanProfile.navMs(p) == 0);
        ok("no measurement yet", !HumanProfile.hasMine(p) && !HumanProfile.usingMine(p));
        ok("choosing 내 패턴 without one is refused", !HumanProfile.setUseMine(p, true) && !HumanProfile.usingMine(p));

        // hand-made values
        p.edit().putInt(HumanProfile.K_MEAN, 3000).putInt(HumanProfile.K_SIGMA, 20).putInt(HumanProfile.K_ADVANCE, 400).apply();
        ok("hand-made mean used", HumanProfile.meanMs(p) == 3000);

        // a measurement is stored apart and switches to it
        HumanProfile.saveMine(p, sample());
        ok("measured set in use after saving", HumanProfile.usingMine(p));
        ok("measured mean used", HumanProfile.meanMs(p) == 2500);
        ok("measured sigma used", Math.abs(HumanProfile.sigma(p) - 0.50) < 1e-9);
        ok("measured hesitation used", Math.abs(HumanProfile.hesitation(p) - 0.15) < 1e-9);
        ok("measured fatigue used", Math.abs(HumanProfile.fatiguePerQuestion(p) - 0.006) < 1e-9);
        ok("measured pauses used", HumanProfile.advanceMs(p) == 900 && HumanProfile.revealMs(p) == 700);
        ok("measured start wait used", HumanProfile.navMs(p) == 3000);
        ok("the hand-made values were not overwritten", p.getInt(HumanProfile.K_MEAN, -1) == 3000 && p.getInt(HumanProfile.K_SIGMA, -1) == 20 && p.getInt(HumanProfile.K_ADVANCE, -1) == 400);
        ok("description kept", HumanProfile.describeMine(p).startsWith("응답 2.5초"));

        // switching back and forth changes nothing stored
        ok("back to 평균·랜덤", HumanProfile.setUseMine(p, false) && !HumanProfile.usingMine(p));
        ok("hand-made mean again", HumanProfile.meanMs(p) == 3000 && Math.abs(HumanProfile.sigma(p) - 0.20) < 1e-9 && HumanProfile.advanceMs(p) == 400);
        ok("measured set still there", HumanProfile.hasMine(p) && p.getInt(HumanProfile.M_MEAN, -1) == 2500);
        ok("and on again", HumanProfile.setUseMine(p, true) && HumanProfile.meanMs(p) == 2500);

        // resetting one set does not touch the other
        HumanProfile.resetDefaults(p);
        ok("reset of hand-made details keeps the measurement", HumanProfile.hasMine(p) && HumanProfile.meanMs(p) == 2500 && HumanProfile.sigmaPct(p, false) == HumanProfile.SIGMA_DEFAULT);
        ok("reset keeps the hand-made mean", HumanProfile.meanMs(p, false) == 3000);

        // a measurement without some values leaves those at the rule, not at the hand-made numbers
        p.edit().putInt(HumanProfile.K_REVEAL, 1234).apply();
        PaceModel.Result partial = new PaceModel.Result();
        partial.meanMs = 1000;
        HumanProfile.saveMine(p, partial);
        ok("unmeasured reveal pause is the rule, not the hand-made value", HumanProfile.revealMs(p) == 250);
        ok("unmeasured start wait is none", HumanProfile.navMs(p) == 0);

        // deleting the measurement returns to 평균·랜덤
        HumanProfile.clearMine(p);
        ok("cleared", !HumanProfile.hasMine(p) && !HumanProfile.usingMine(p) && HumanProfile.describeMine(p).isEmpty());
        ok("after clearing the hand-made values are in use", HumanProfile.meanMs(p) == 3000);
        ok("and the choice is refused again", !HumanProfile.setUseMine(p, true));
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
