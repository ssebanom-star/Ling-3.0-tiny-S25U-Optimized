package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class WrongAnswersTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    static Matcher.CardData c(String w, String... m) {
        return new Matcher.CardData(List.of(w), List.of(m));
    }

    public static void main(String[] a) {
        Random r = new Random(7);

        // --- which questions are wrong ---
        Set<Integer> s = WrongAnswers.chooseQuestions(r, 20, 5);
        ok("exact count", s.size() == 5);
        ok("within range", s.stream().allMatch(v -> v >= 1 && v <= 20));
        ok("zero", WrongAnswers.chooseQuestions(r, 20, 0).isEmpty());
        ok("capped at total", WrongAnswers.chooseQuestions(r, 4, 99).size() == 4);
        ok("total zero", WrongAnswers.chooseQuestions(r, 0, 3).isEmpty());
        ok("negative count", WrongAnswers.chooseQuestions(r, 10, -2).isEmpty());

        int[] hits = new int[21];
        for (int i = 0; i < 20000; i++) {
            for (int q : WrongAnswers.chooseQuestions(r, 20, 5)) {
                hits[q]++;
            }
        }
        boolean uniform = true;
        for (int q = 1; q <= 20; q++) {
            uniform &= Math.abs(hits[q] - 5000) < 400;   // expected 20000 * 5/20
        }
        ok("every question equally likely", uniform);

        // --- progress parsing ---
        ok("parse 3 | 20", WrongAnswers.questionNumber("3 | 20") == 3);
        ok("parse 12|40", WrongAnswers.questionNumber("12|40") == 12);
        ok("parse leading space", WrongAnswers.questionNumber("  7 | 9") == 7);
        ok("parse empty", WrongAnswers.questionNumber("") == -1 && WrongAnswers.questionNumber(null) == -1);
        ok("parse junk", WrongAnswers.questionNumber("abc") == -1);

        // --- pick ---
        ok("no candidate", WrongAnswers.pickWrong(r, new ArrayList<>()) == -1);
        ok("single candidate", WrongAnswers.pickWrong(r, Arrays.asList(4)) == 4);
        boolean inSet = true;
        for (int i = 0; i < 200; i++) {
            int p = WrongAnswers.pickWrong(r, Arrays.asList(1, 3, 5));
            inSet &= p == 1 || p == 3 || p == 5;
        }
        ok("pick from candidates only", inSet);

        // --- Matcher knobs ---
        List<Matcher.CardData> cs = List.of(c("approve", "승인하다"), c("rapid", "빠른"), c("apple", "사과"));
        List<String> ch = List.of("승인하다", "먹다", "자다", "웃다", "울다", "걷다");
        Matcher.resetConfiguration();
        ok("default accepts one-letter slip", Matcher.answer("aprove", ch, cs, false).index == 0);
        Matcher.configure(0.95, 0.08, 2);
        boolean rejected = false;
        try {
            Matcher.answer("aprove", ch, cs, false);
        } catch (Matcher.ResolutionException e) {
            rejected = e.failure == Matcher.Failure.PROMPT_NOT_FOUND;
        }
        ok("strict fuzzy rejects the same slip", rejected);
        ok("strict still accepts exact", Matcher.answer("approve", ch, cs, false).index == 0);

        // partial minimum: the visible part "승" shares 1 letter
        Matcher.resetConfiguration();
        boolean tooShort = false;
        try {
            Matcher.answer("approve", List.of("승", "먹기", "자기", "웃기", "울기", "걷기"), cs, true);
        } catch (Matcher.ResolutionException e) {
            tooShort = e.failure == Matcher.Failure.PARTIAL_TOO_SHORT;
        }
        ok("partial default needs 2 letters", tooShort);
        Matcher.configure(0.8, 0.08, 1);
        ok("partial min 1 accepts one letter", Matcher.answer("approve", List.of("승", "먹기", "자기", "웃기", "울기", "걷기"), cs, true).index == 0);

        // tie margin 0 turns an ambiguous tie into a pick
        Matcher.resetConfiguration();
        List<Matcher.CardData> dup = List.of(c("big", "큰"), c("large", "큰"));
        boolean amb = false;
        try {
            Matcher.answer("큰", List.of("big", "large", "x", "y"), dup, false);
        } catch (Matcher.ResolutionException e) {
            amb = e.failure == Matcher.Failure.CHOICE_AMBIGUOUS;
        }
        ok("default keeps the tie ambiguous", amb);
        Matcher.configure(0.8, 0.0, 2);
        ok("tie margin 0 picks one", Matcher.answer("큰", List.of("big", "large", "x", "y"), dup, false).index >= 0);

        // clamping
        Matcher.configure(5.0, -1.0, 99);
        ok("configure clamps without throwing", true);
        Matcher.resetConfiguration();

        // a deliberately wrong pick: choices that do not match the prompt are safe candidates
        List<String> choices = List.of("승인하다", "먹다", "자다", "웃다", "울다", "걷다");
        List<Integer> safe = new ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            boolean wrong;
            try {
                Matcher.answer("approve", List.of(choices.get(i)), cs, false);
                wrong = false;
            } catch (Matcher.ResolutionException e) {
                wrong = e.failure == Matcher.Failure.CHOICE_NOT_FOUND;
            }
            if (wrong) {
                safe.add(i);
            }
        }
        ok("the correct choice is never a wrong candidate", !safe.contains(0) && safe.size() == 5);

        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
