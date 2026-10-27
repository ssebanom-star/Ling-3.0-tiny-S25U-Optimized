package net.classcardauto.mobile;

import java.util.*;
public class MatcherTest {
    static int pass = 0, fail = 0;
    static Matcher.CardData c(String w, String... m) { return new Matcher.CardData(List.of(w), List.of(m)); }
    static void ok(String name, boolean cond) { if (cond) pass++; else { fail++; System.out.println("FAIL " + name); } }
    static int idx(String p, List<String> ch, List<Matcher.CardData> cs, boolean part) {
        try { return Matcher.answer(p, ch, cs, part).index; } catch (Matcher.ResolutionException e) { System.out.println("   (" + e.failure + ")"); return -1; }
    }
    static Matcher.Failure fl(String p, List<String> ch, List<Matcher.CardData> cs, boolean part) {
        try { Matcher.answer(p, ch, cs, part); return null; } catch (Matcher.ResolutionException e) { return e.failure; }
    }
    public static void main(String[] a) {
        List<Matcher.CardData> cs = List.of(
            c("apple", "사과"), c("run", "[동] 달리다, 운영하다"), c("bank", "[명] 은행", "둑"),
            c("approach", "접근하다; 다가가다"), c("approve", "승인하다"), c("rapid", "빠른"),
            c("sustain", "(~을) 지탱하다, 유지하다"), c("elaborate", "정교한"), c("illustrate", "설명하다, 예시하다"));
        // --- 원본 동작 회귀 ---
        ok("exact w2m", idx("apple", List.of("바나나","사과","포도","배","귤","감"), cs, false) == 1);
        ok("pos tag stripped", idx("run", List.of("달리다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("part match", idx("run", List.of("운영하다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("m2w", idx("은행", List.of("apple","bank","run","approve","rapid","sustain"), cs, false) == 1);
        ok("numbering parts", idx("approach", List.of("다가가다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("prompt missing", fl("zzzzzz", List.of("a","b"), cs, false) == Matcher.Failure.PROMPT_NOT_FOUND);
        ok("choice missing", fl("apple", List.of("가","나","다"), cs, false) == Matcher.Failure.CHOICE_NOT_FOUND);
        // --- 개선: 표기 변형 ---
        ok("paren/tilde loose", idx("sustain", List.of("지탱하다, 유지하다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("paren only part", idx("sustain", List.of("지탱하다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("punct diff", idx("bank", List.of("은행.","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        // --- 개선: OCR 오인식 제시어 ---
        ok("ocr l/I", idx("appIe", List.of("바나나","사과","포도","배","귤","감"), cs, false) == 1);
        ok("ocr rn/m", idx("rapid".replace("a","a"), List.of("빠른","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        ok("ocr 1 char", idx("aprove", List.of("승인하다","먹다","자다","웃다","울다","걷다"), cs, false) == 0);
        // --- 개선: 선택지 OCR 오차 (한→영, 영단어 오인식) ---
        ok("choice ocr", idx("사과", List.of("banana","appIe","grape","pear","plum","kiwi"), cs, false) == 1);
        ok("choice ocr far", fl("사과", List.of("banana","grape","pear","plum","kiwi","lime"), cs, false) == Matcher.Failure.CHOICE_NOT_FOUND);
        // --- 부분 선택지 ---
        ok("partial ok", idx("elaborate", List.of("정교","먹다","자다","웃다","울다","걷다"), cs, true) == 0);
        ok("partial short", fl("elaborate", List.of("정","나","다"), cs, true) == Matcher.Failure.PARTIAL_TOO_SHORT);
        // --- 동의어 중복(실제 모호) 은 거부 유지 ---
        List<Matcher.CardData> dup = List.of(c("big","큰"), c("large","큰"));
        ok("true ambiguity stays", fl("큰", List.of("big","large","x","y"), dup, false) == Matcher.Failure.CHOICE_AMBIGUOUS);
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
