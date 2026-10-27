package net.classcardauto.mobile;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** ClassCardAuto Solver 개선판(순수 로직). 원본 Solver.answer와 같은 입출력 의미를 유지하되 OCR 오차/동점/표기 변형에 강하다. */
public final class Matcher {
    public static final class CardData {
        public final List<String> words;     // card.getWords()
        public final List<String> meanings;  // card.meaning + aliases
        public CardData(List<String> words, List<String> meanings) { this.words = words; this.meanings = meanings; }
    }
    public enum Failure { PROMPT_NOT_FOUND, CHOICE_NOT_FOUND, CHOICE_AMBIGUOUS, PARTIAL_TOO_SHORT, PARTIAL_TIE }
    public static final class Result {
        public final int index; public final int strength; public final double score; public final List<Integer> cards;
        Result(int i, int s, double sc, List<Integer> cards) { index = i; strength = s; score = sc; this.cards = cards; }
    }
    public static final class ResolutionException extends RuntimeException {
        public final Failure failure;
        ResolutionException(Failure f) { super(f.name()); failure = f; }
    }

    // ---- 설정 ----
    // 기본값. 앱에서는 AdvancedSettings 가 configure() 로 덮어쓴다.
    static final double DEFAULT_FUZZY_MIN = 0.80;  // OCR 오인식 허용 하한 (유사도)
    static final double DEFAULT_TIE_MARGIN = 0.08; // 동점 보조판별 최소 격차
    static final int DEFAULT_PARTIAL_MIN = 2;      // 잘린 선택지가 겹쳐야 하는 최소 글자 수

    private static volatile double fuzzyMin = DEFAULT_FUZZY_MIN;
    private static volatile double tieMargin = DEFAULT_TIE_MARGIN;
    private static volatile int partialMin = DEFAULT_PARTIAL_MIN;

    /** Changes the matching strictness for the following answers. Values are clamped to sensible ranges. */
    public static void configure(double fuzzy, double tie, int partial) {
        fuzzyMin = Math.max(0.5, Math.min(1.0, fuzzy));
        tieMargin = Math.max(0.0, Math.min(0.5, tie));
        partialMin = Math.max(1, Math.min(10, partial));
    }

    /** Back to the built-in values (for tests). */
    public static void resetConfiguration() {
        configure(DEFAULT_FUZZY_MIN, DEFAULT_TIE_MARGIN, DEFAULT_PARTIAL_MIN);
    }

    private static final Pattern POS = Pattern.compile("\\[(?:명|동|형|부|전|접|대|감|관)(?:사)?]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern PAREN = Pattern.compile("[(（][^)）]*[)）]");
    private static final Pattern SPLIT = Pattern.compile("[,;\\r\\n]|\\s*/\\s*");
    private static final Pattern NUMBERING = Pattern.compile("(?<![\\p{L}\\p{N}_])\\d+\\.\\s*(?=[가-힣(~∼])");

    // ---- 텍스트 규칙 (원본 TextRules 동일) ----
    static String base(String s) { return Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    static String normalize(String s) { return SPACES.matcher(POS.matcher(base(s)).replaceAll("")).replaceAll(" ").trim(); }
    static Set<String> parts(String s) {
        String t = NUMBERING.matcher(POS.matcher(base(s)).replaceAll(",")).replaceAll(",");
        Set<String> out = new LinkedHashSet<>();
        for (String p : SPLIT.split(t)) { String n = normalize(p); if (!n.isEmpty()) out.add(n); }
        return out;
    }
    /** 느슨한 표기: 괄호 내용·물결·문장부호·선행 "to " 제거, 글자/숫자만 남김. */
    static String loose(String s) {
        String t = normalize(PAREN.matcher(s).replaceAll(" "));
        t = t.replaceAll("^to\\s+", "").replaceAll("[~∼]", "");
        StringBuilder sb = new StringBuilder();
        t.codePoints().filter(Character::isLetterOrDigit).forEach(sb::appendCodePoint);
        return sb.toString();
    }
    static Set<String> looseParts(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : parts(s)) { String l = loose(p); if (!l.isEmpty()) out.add(l); }
        return out;
    }

    /** 3=완전일치, 2=뜻의 한 파트 일치, 1=느슨한 일치(괄호/문장부호/파트 변형), 0=불일치 */
    static int definitionStrength(String text, String meaning) {
        String n = normalize(text);
        if (n.equals(normalize(meaning))) return 3;
        Set<String> mp = parts(meaning);
        if (parts(text).equals(mp)) return 3;
        Set<String> mp2 = parts(Pattern.compile("[\\r\\n]+").matcher(meaning).replaceAll(" "));
        if (parts(text).equals(mp2)) return 3;
        if (mp.contains(n) || mp2.contains(n)) return 2;
        String l = loose(text);
        if (l.isEmpty()) return 0;
        if (l.equals(loose(meaning))) return 2;           // 괄호/기호 차이만 있는 경우
        Set<String> lp = looseParts(meaning);
        if (lp.contains(l)) return 1;
        if (looseParts(text).equals(lp) && !lp.isEmpty()) return 1;
        return 0;
    }
    static int strength(String text, CardData c) {
        int best = 0;
        for (String m : c.meanings) if (!m.trim().isEmpty()) best = Math.max(best, definitionStrength(text, m));
        return best;
    }
    static int wordStrength(String text, CardData c) {
        String n = normalize(text);
        String l = loose(text);
        int best = 0;
        for (String w : c.words) {
            if (n.equals(normalize(w))) return 3;
            if (!l.isEmpty() && l.equals(loose(w))) best = Math.max(best, 2);
        }
        return best;
    }

    // ---- 유사도 ----
    static int[] cps(String s) { return s.codePoints().toArray(); }
    static int levenshtein(String a, String b) {
        int[] x = cps(a), y = cps(b);
        int[] prev = new int[y.length + 1], cur = new int[y.length + 1];
        for (int j = 0; j <= y.length; j++) prev[j] = j;
        for (int i = 1; i <= x.length; i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length; j++)
                cur[j] = Math.min(Math.min(cur[j - 1], prev[j]) + 1, prev[j - 1] + (x[i - 1] == y[j - 1] ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[y.length];
    }
    /** OCR 혼동 문자 접기: l/I/1/|, O/0, rn→m 등. 비교용 키로만 사용. */
    static String ocrFold(String s) {
        String t = loose(s);
        return t.replace("rn", "m").replace('1', 'l').replace('i', 'l').replace('0', 'o').replace('5', 's');
    }
    static double similarity(String a, String b) {
        String x = ocrFold(a), y = ocrFold(b);
        int m = Math.max(cps(x).length, cps(y).length);
        if (m == 0) return 0;
        return 1.0 - (double) levenshtein(x, y) / m;
    }
    static double bestSim(String text, List<String> targets) {
        double b = 0;
        for (String t : targets) {
            b = Math.max(b, similarity(text, t));
            for (String p : parts(t)) b = Math.max(b, similarity(text, p));
        }
        return b;
    }
    /** ChoiceOverlap.count와 동일한 LCS 글자 수(원본은 겹침 글자 수). */
    static int overlap(String visible, String expected) {
        int[] a = cps(loose(visible)), b = cps(loose(expected));
        int[] dp = new int[b.length + 1];
        for (int i = 1; i <= a.length; i++) {
            int diag = 0;
            for (int j = 1; j <= b.length; j++) {
                int tmp = dp[j];
                dp[j] = a[i - 1] == b[j - 1] ? diag + 1 : Math.max(dp[j], dp[j - 1]);
                diag = tmp;
            }
        }
        return dp[b.length];
    }

    // ---- 본체 ----
    private static final class M { final int card; final boolean w2m; final int str; final double sim;
        M(int c, boolean w, int s, double sim) { card = c; w2m = w; str = s; this.sim = sim; } }

    public static Result answer(String prompt, List<String> choices, List<CardData> cards, boolean allowPartial) {
        String np = normalize(prompt);
        List<M> ms = new ArrayList<>();
        // 1) 제시어 → 후보 카드 (정확 → 느슨 → OCR 퍼지)
        for (int i = 0; i < cards.size(); i++) {
            CardData c = cards.get(i);
            int ws = wordStrength(prompt, c);
            if (ws > 0) ms.add(new M(i, true, ws, 1.0));
            int s = strength(prompt, c);
            if (s > 0) ms.add(new M(i, false, s, 1.0));
        }
        if (ms.isEmpty()) {
            double top = 0; List<M> fz = new ArrayList<>();
            for (int i = 0; i < cards.size(); i++) {
                CardData c = cards.get(i);
                double sw = bestSim(prompt, c.words), sm = bestSim(prompt, c.meanings);
                double s = Math.max(sw, sm);
                if (s >= fuzzyMin) {
                    fz.add(new M(i, sw >= sm, 1, s));
                    top = Math.max(top, s);
                }
            }
            for (M m : fz) if (m.sim >= top - 1e-9) ms.add(m);
        }
        if (ms.isEmpty()) throw new ResolutionException(Failure.PROMPT_NOT_FOUND);

        // 2) 각 선택지 점수
        int n = choices.size();
        int[] str = new int[n]; double[] sim = new double[n]; boolean[] hit = new boolean[n];
        List<Set<Integer>> support = new ArrayList<>();
        for (int k = 0; k < n; k++) support.add(new LinkedHashSet<>());
        for (int k = 0; k < n; k++) {
            String ch = choices.get(k);
            for (M m : ms) {
                CardData c = cards.get(m.card);
                int s = m.w2m ? strength(ch, c) : wordStrength(ch, c);
                if (s <= 0) continue;
                hit[k] = true;
                support.get(k).add(m.card);
                str[k] = Math.max(str[k], Math.min(s, m.str));
                double sm = (m.w2m ? bestSim(ch, c.meanings) : bestSim(ch, c.words)) * m.sim;
                sim[k] = Math.max(sim[k], sm);
            }
        }
        List<Integer> hits = new ArrayList<>();
        for (int k = 0; k < n; k++) if (hit[k]) hits.add(k);
        if (hits.isEmpty()) {
            if (allowPartial) return partial(ms, choices, cards);
            // 마지막 수단: 선택지가 정답과 충분히 유사하면(OCR 오차) 유일 최고 유사도 채택
            return fuzzyChoice(ms, choices, cards);
        }
        // 3) 최고 강도 → 동점이면 유사도 보조판별
        int best = 0; for (int k : hits) best = Math.max(best, str[k]);
        List<Integer> top = new ArrayList<>();
        for (int k : hits) if (str[k] == best) top.add(k);
        if (top.size() == 1) return new Result(top.get(0), best, sim[top.get(0)], new ArrayList<>(support.get(top.get(0))));
        top.sort((a, b) -> Double.compare(sim[b], sim[a]));
        if (sim[top.get(0)] - sim[top.get(1)] >= tieMargin) return new Result(top.get(0), best, sim[top.get(0)], new ArrayList<>(support.get(top.get(0))));
        throw new ResolutionException(Failure.CHOICE_AMBIGUOUS);
    }

    private static List<Integer> candidateCards(List<M> ms) {
        Set<Integer> out = new LinkedHashSet<>();
        for (M m : ms) out.add(m.card);
        return new ArrayList<>(out);
    }

    private static Result fuzzyChoice(List<M> ms, List<String> choices, List<CardData> cards) {
        double[] sc = new double[choices.size()];
        for (int k = 0; k < sc.length; k++)
            for (M m : ms) {
                CardData c = cards.get(m.card);
                sc[k] = Math.max(sc[k], m.w2m ? bestSim(choices.get(k), c.meanings) : bestSim(choices.get(k), c.words));
            }
        int b = 0; for (int k = 1; k < sc.length; k++) if (sc[k] > sc[b]) b = k;
        double second = 0; for (int k = 0; k < sc.length; k++) if (k != b) second = Math.max(second, sc[k]);
        if (sc[b] >= fuzzyMin && sc[b] - second >= tieMargin) return new Result(b, 1, sc[b], candidateCards(ms));
        throw new ResolutionException(Failure.CHOICE_NOT_FOUND);
    }

    private static Result partial(List<M> ms, List<String> choices, List<CardData> cards) {
        int n = choices.size(); int[] pts = new int[n]; double[] sim = new double[n];
        for (int k = 0; k < n; k++)
            for (M m : ms) {
                CardData c = cards.get(m.card);
                for (String e : m.w2m ? c.meanings : c.words) {
                    pts[k] = Math.max(pts[k], overlap(choices.get(k), e));
                    sim[k] = Math.max(sim[k], similarity(choices.get(k), e));
                }
            }
        int best = 0; for (int p : pts) best = Math.max(best, p);
        if (best < partialMin) throw new ResolutionException(Failure.PARTIAL_TOO_SHORT);
        List<Integer> top = new ArrayList<>();
        for (int k = 0; k < n; k++) if (pts[k] == best) top.add(k);
        if (top.size() == 1) return new Result(top.get(0), 1, sim[top.get(0)], candidateCards(ms));
        top.sort((a, b) -> Double.compare(sim[b], sim[a]));   // 원본은 여기서 PARTIAL_TIE
        if (sim[top.get(0)] - sim[top.get(1)] >= tieMargin) return new Result(top.get(0), 1, sim[top.get(0)], candidateCards(ms));
        throw new ResolutionException(Failure.PARTIAL_TIE);
    }
}
