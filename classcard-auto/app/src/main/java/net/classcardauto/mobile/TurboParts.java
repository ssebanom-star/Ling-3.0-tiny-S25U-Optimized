package net.classcardauto.mobile;

import android.content.SharedPreferences;

/**
 * The pieces of "초고속 모드" that can be switched on one by one. The master switch is {@code turbo}; each part only
 * acts while it is on. The first four are on by default, the last two (which can cost answers) off.
 */
public final class TurboParts {
    public static final String REACT = "turbo_react";
    public static final String COLLECT = "turbo_collect";
    public static final String TEST = "turbo_test";
    public static final String COUNTDOWN = "turbo_countdown";
    public static final String SKIP_CONFIRM = "turbo_skip_confirm";
    public static final String EXPAND_FIRST = "turbo_expand_first";
    /** Where 초고속 applies at all: during word collection, during the test (both on by default). */
    public static final String SCOPE_COLLECT = "turbo_scope_collect";
    public static final String SCOPE_TEST = "turbo_scope_test";

    /** key, title, description, default — in the order they are shown. */
    public static final Object[][] PARTS = {
            {REACT, "빠른 반응", "화면 읽기 0.1→0.035초, 탭·화면 전환 뒤 대기 절반", true},
            {COLLECT, "빠른 수집", "단어 읽기 간격 0.4→0.2초, 스크롤 간격 최대 0.25초, 목록 스와이프 최대 0.22초", true},
            {TEST, "빠른 카드 넘기기", "시험 카드 스와이프 0.25→0.15초", true},
            {COUNTDOWN, "짧은 시작 대기", "시작 카운트다운 8→1초", true},
            {SKIP_CONFIRM, "확인 없이 넘기기", "답을 누르자마자 넘김. 터치가 안 먹히면 그 문항은 틀림", false},
            {EXPAND_FIRST, "구간 먼저 전부 펼치기", "수집 전에 맨 아래로 내린 뒤 아래 구간부터 펼치며 올라옴", false},
    };

    private TurboParts() {
    }

    /** Which scope a part belongs to: true = collection, false = test, null = both (it follows the current phase). */
    public static Boolean scopeOf(String key) {
        if (COLLECT.equals(key) || EXPAND_FIRST.equals(key)) {
            return Boolean.TRUE;
        }
        if (TEST.equals(key) || SKIP_CONFIRM.equals(key)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public static boolean scopeOn(SharedPreferences p, boolean collecting) {
        return p.getBoolean(collecting ? SCOPE_COLLECT : SCOPE_TEST, true);
    }

    public static boolean defaultOf(String key) {
        for (Object[] part : PARTS) {
            if (part[0].equals(key)) {
                return (Boolean) part[3];
            }
        }
        return false;
    }

    /** The part is on and so is the master switch. */
    public static boolean on(SharedPreferences p, String key) {
        Boolean scope = scopeOf(key);
        return p.getBoolean("turbo", false) && p.getBoolean(key, defaultOf(key)) && (scope == null || scopeOn(p, scope.booleanValue()));
    }

    /** "4/6 켜짐" style count for summaries. */
    public static String count(SharedPreferences p) {
        int n = 0;
        for (Object[] part : PARTS) {
            if (p.getBoolean((String) part[0], (Boolean) part[3])) {
                n++;
            }
        }
        return n + "/" + PARTS.length;
    }
}
