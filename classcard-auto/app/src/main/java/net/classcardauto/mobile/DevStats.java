package net.classcardauto.mobile;

import java.util.Locale;

/**
 * Where the time of a question goes, kept in memory only (nothing is written anywhere, and no question text is kept):
 * A prompt shown → choices shown, B choices shown → answer sent, E the gesture itself (send → done), C tap → feedback
 * shown (when it is waited for), D gesture done (or feedback) → next prompt. Only the experimental 개발 page shows it.
 */
public final class DevStats {
    private static long seenAt;
    private static long choicesAt;
    private static long tapAt;
    private static long doneAt;
    private static long feedbackAt;
    private static long prevMark;
    private static final int METRICS = 5;
    private static final long[] SUM = new long[METRICS];
    private static final int[] COUNT = new int[METRICS];

    private DevStats() {
    }

    public static synchronized void reset() {
        seenAt = choicesAt = tapAt = doneAt = feedbackAt = prevMark = 0;
        for (int i = 0; i < METRICS; i++) {
            SUM[i] = 0;
            COUNT[i] = 0;
        }
    }

    private static void add(int metric, long ms) {
        if (ms >= 0 && ms < 60000) {
            SUM[metric] += ms;
            COUNT[metric]++;
        }
    }

    /** A new question's prompt is on screen. */
    public static synchronized void seen(long now) {
        if (prevMark > 0) {
            add(3, now - prevMark);
        }
        prevMark = 0;
        seenAt = now;
        choicesAt = tapAt = doneAt = feedbackAt = 0;
    }

    public static synchronized void choices(long now) {
        if (seenAt > 0 && choicesAt == 0) {
            choicesAt = now;
            add(0, now - seenAt);
        }
    }

    /** The answer is being sent. */
    public static synchronized void tap(long now) {
        if (choicesAt > 0 && tapAt == 0) {
            add(1, now - choicesAt);
            tapAt = now;
            prevMark = now;
        }
    }

    /** The gesture finished. */
    public static synchronized void done(long now) {
        if (tapAt > 0 && doneAt == 0) {
            add(4, now - tapAt);
            doneAt = now;
            prevMark = now;
        }
    }

    public static synchronized void feedback(long now) {
        if (tapAt > 0 && feedbackAt == 0) {
            add(2, now - tapAt);
            feedbackAt = now;
            prevMark = now;
        }
    }

    private static String avg(int metric) {
        return COUNT[metric] == 0 ? "-" : String.format(Locale.US, "%d", SUM[metric] / COUNT[metric]);
    }

    public static synchronized String summary() {
        if (COUNT[0] == 0 && COUNT[1] == 0) {
            return "아직 측정값이 없어요. 시험을 한 번 돌린 뒤 새로고침하세요.";
        }
        long total = 0;
        for (int i : new int[]{0, 1, 4, 3}) {
            if (COUNT[i] > 0) {
                total += SUM[i] / COUNT[i];
            }
        }
        return "이번 실행 평균 (ms, " + COUNT[1] + "문항)\n"
                + "문제→보기 " + avg(0) + " · 보기→보냄 " + avg(1) + "\n"
                + "제스처 " + avg(4) + " · 탭→피드백 " + avg(2) + "\n"
                + "완료→다음 문제 " + avg(3) + "\n"
                + "합계 약 " + total + "ms / 문항";
    }
}
