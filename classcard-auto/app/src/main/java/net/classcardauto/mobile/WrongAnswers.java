package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/** Which questions of a run are answered wrong on purpose, and which wrong choice is used. Pure logic. */
public final class WrongAnswers {
    private WrongAnswers() {
    }

    /**
     * Picks {@code count} distinct question numbers out of 1..total, uniformly. The count is capped at the total.
     */
    public static Set<Integer> chooseQuestions(Random r, int total, int count) {
        int n = Math.max(0, Math.min(count, total));
        List<Integer> all = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            all.add(Integer.valueOf(i));
        }
        Collections.shuffle(all, r);
        return new TreeSet<>(all.subList(0, n));
    }

    /** The leading number of a progress string such as "3 | 20", or -1. */
    public static int questionNumber(String progress) {
        if (progress == null) {
            return -1;
        }
        int i = 0;
        while (i < progress.length() && progress.charAt(i) == ' ') {
            i++;
        }
        int start = i;
        while (i < progress.length() && Character.isDigit(progress.charAt(i))) {
            i++;
        }
        if (i == start) {
            return -1;
        }
        try {
            return Integer.parseInt(progress.substring(start, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** One of {@code candidates} (indices of choices that are definitely wrong), or -1 when there is none. */
    public static int pickWrong(Random r, List<Integer> candidates) {
        if (candidates.isEmpty()) {
            return -1;
        }
        return candidates.get(r.nextInt(candidates.size())).intValue();
    }
}
