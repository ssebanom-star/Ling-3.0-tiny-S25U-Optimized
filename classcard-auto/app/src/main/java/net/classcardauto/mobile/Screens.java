package net.classcardauto.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/**
 * Pure view builders for the app's screens. They hold no business logic: callers pass callbacks in and push state
 * through the returned holders, which keeps the layouts renderable on their own for previews.
 */
public final class Screens {
    private Screens() {
    }

    private static int dp(Context c, int v) {
        return AppStyle.INSTANCE.dp(c, v);
    }

    private static LinearLayout.LayoutParams lp(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(c, topDp);
        return p;
    }

    private static void add(LinearLayout parent, View child, int topDp) {
        parent.addView(child, lp(parent.getContext(), topDp));
    }

    /** Full-screen vertical page: top bar (fixed) + scrolling content. */
    public static final class Page {
        public final LinearLayout root;
        public final LinearLayout content;
        public final ScrollView scroll;

        Page(LinearLayout root, LinearLayout content, ScrollView scroll) {
            this.root = root;
            this.content = content;
            this.scroll = scroll;
        }
    }

    static Page page(Context c, View topBar) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(1);
        root.setBackgroundColor(s.getBackground());
        root.setFocusableInTouchMode(true);
        if (topBar != null) {
            root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));
        }
        ScrollView scroll = new ScrollView(c);
        scroll.setOverScrollMode(2);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(1);
        content.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        return new Page(root, content, scroll);
    }

    static LinearLayout brandBar(Context c, Function0<Unit> onSettings) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(0);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 20), dp(c, 12), dp(c, 12), dp(c, 4));
        ImageView mark = new ImageView(c);
        mark.setImageResource(R.drawable.app_mark);
        mark.setImportantForAccessibility(2);
        bar.addView(mark, new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36)));
        TextView name = s.label(c, "MiMiC", 17.0f, s.getInk(), true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1.0f);
        np.leftMargin = dp(c, 12);
        bar.addView(name, np);
        if (onSettings != null) {
            bar.addView(s.iconButton(c, R.drawable.ic_settings, "설정 및 안내", s.getInk(), onSettings));
        }
        return bar;
    }

    static LinearLayout titleBar(Context c, String title, Function0<Unit> onBack) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(0);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 8), dp(c, 8), dp(c, 20), dp(c, 4));
        bar.addView(s.iconButton(c, R.drawable.ic_back, "뒤로", s.getInk(), onBack));
        TextView t = s.label(c, title, 19.0f, s.getInk(), true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tp.leftMargin = dp(c, 8);
        bar.addView(t, tp);
        return bar;
    }

    // ---------------------------------------------------------------- home

    public enum Link { NO_SERVICE, READY, RUNNING, PAUSED }

    public static final class Home {
        public final LinearLayout root;
        public final View dot;
        public final TextView chip;
        public final TextView statusTitle;
        public final TextView statusBody;
        public final Button primary;
        public final Button stop;
        public final Button accessibility;
        public final LinearLayout banner;
        public final TextView bannerText;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText) {
            this.banner = banner;
            this.bannerText = bannerText;
            this.root = root;
            this.dot = dot;
            this.chip = chip;
            this.statusTitle = statusTitle;
            this.statusBody = statusBody;
            this.primary = primary;
            this.stop = stop;
            this.accessibility = accessibility;
        }

        /** Shows the "last run stopped" notice above the status card; pass null to hide it. */
        public void showFailure(String text) {
            if (text == null) {
                banner.setVisibility(View.GONE);
                return;
            }
            bannerText.setText(AppStyle.keepWords(text));
            banner.setVisibility(View.VISIBLE);
        }

        public void render(Link link, String detail) {
            render(link, detail, -1L, null);
        }

        public void render(Link link, String detail, long remainingSeconds) {
            render(link, detail, remainingSeconds, null);
        }

        /**
         * Updates the status card and which buttons are usable. {@code detail} is the live status while running and
         * {@code remainingSeconds} the time left before the run limit (-1 = unknown).
         */
        public void render(Link link, String detail, long remainingSeconds, String note) {
            AppStyle s = AppStyle.INSTANCE;
            Context c = root.getContext();
            int dotColor;
            String chipText;
            int chipFg;
            int chipBg;
            switch (link) {
                case NO_SERVICE:
                    dotColor = s.getWarn();
                    chipText = "연결 필요";
                    chipFg = s.getWarn();
                    chipBg = s.getSoftWarn();
                    statusTitle.setText(AppStyle.keepWords("접근성 연결이 필요해요"));
                    statusBody.setText(AppStyle.keepWords("접근성 설정에서 ‘MiMiC’를 켜 주세요."));
                    break;
                case RUNNING:
                    dotColor = s.getInfo();
                    chipText = remainingSeconds < 0 ? "실행 중" : "실행 중 · 남은 " + AppStyle.formatClock(remainingSeconds);
                    chipFg = s.getInfo();
                    chipBg = s.getSoftInfo();
                    if (remainingSeconds >= 0 && remainingSeconds <= 60) {
                        chipFg = s.getWarn();
                        chipBg = s.getSoftWarn();
                    }
                    statusTitle.setText(AppStyle.keepWords(detail == null || detail.isEmpty() ? "실행 중" : detail));
                    statusBody.setText(AppStyle.keepWords(note != null ? note : "일시정지와 재개는 클래스카드 위의 플로팅 버튼에서 해요. 중지는 아래 버튼으로도 할 수 있어요."));
                    break;
                case PAUSED:
                    dotColor = s.getWarn();
                    chipText = remainingSeconds < 0 ? "일시정지" : "일시정지 · 남은 " + AppStyle.formatClock(remainingSeconds);
                    chipFg = s.getWarn();
                    chipBg = s.getSoftWarn();
                    statusTitle.setText(AppStyle.keepWords(detail == null || detail.isEmpty() ? "일시정지됨" : detail));
                    statusBody.setText(AppStyle.keepWords(note != null ? note : "클래스카드에서 직접 조작한 뒤 화면의 플로팅 버튼 ‘재개’를 누르면 이어서 진행해요. 앱 화면으로 돌아올 필요는 없어요."));
                    break;
                default:
                    dotColor = s.getAccent();
                    chipText = "준비됨";
                    chipFg = s.getAccentDark();
                    chipBg = s.getSoftAccent();
                    statusTitle.setText(AppStyle.keepWords("시작할 준비가 됐어요"));
                    statusBody.setText(AppStyle.keepWords("클래스카드 단어장이나 테스트 시작 전 화면에서 시작하세요."));
                    break;
            }
            s.setDotColor(dot, dotColor);
            chip.setText(chipText);
            chip.setTextColor(chipFg);
            chip.setBackground(s.roundRect(c, chipBg, 999, 0));
            boolean running = link == Link.RUNNING;
            boolean paused = link == Link.PAUSED;
            boolean ready = link == Link.READY;
            primary.setEnabled(ready);
            primary.setAlpha(ready ? 1.0f : 0.45f);
            primary.setVisibility((running || paused) ? View.GONE : View.VISIBLE);
            stop.setVisibility((running || paused) ? View.VISIBLE : View.GONE);
            accessibility.setVisibility(link == Link.NO_SERVICE ? View.VISIBLE : View.GONE);
        }
    }

    public static Home home(Context c, SharedPreferences prefs, Function0<Unit> onSettings, Function0<Unit> onStart, Function0<Unit> onStop, Function0<Unit> onAccessibility, Function0<Unit> onFailures) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, brandBar(c, onSettings));
        LinearLayout content = page.content;

        TextView headline = s.label(c, "단어 학습을\n가볍게", 30.0f, s.getInk(), true);
        headline.setLineSpacing(dp(c, 2), 1.0f);
        add(content, headline, 20);
        add(content, s.label(c, "연습·허용된 테스트 화면에서 시작하세요.", 15.0f, s.getMuted(), false), 10);

        // notice about the previous run (hidden until MainActivity finds a recent failure)
        LinearLayout banner = new LinearLayout(c);
        banner.setOrientation(0);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(s.roundRect(c, s.getSoftDanger(), 16, 0));
        banner.setPadding(dp(c, 16), dp(c, 8), dp(c, 8), dp(c, 8));
        TextView bannerText = s.label(c, "", 14.0f, s.getDanger(), true);
        banner.addView(bannerText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button bannerButton = s.button(c, "보기", AppStyle.Tone.TEXT, onFailures);
        bannerButton.setTextColor(s.getDanger());
        bannerButton.setMinHeight(dp(c, 40));
        bannerButton.setMinimumHeight(dp(c, 40));
        banner.addView(bannerButton, new LinearLayout.LayoutParams(-2, -2));
        banner.setVisibility(View.GONE);
        add(content, banner, 18);

        // status card
        LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(0);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = s.dot(c, s.getAccent(), 10);
        head.addView(dot);
        TextView chip = s.chip(c, "준비됨", s.getAccentDark(), s.getSoftAccent());
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-2, -2);
        chp.leftMargin = dp(c, 10);
        head.addView(chip, chp);
        card.addView(head);
        TextView statusTitle = s.label(c, "연결 상태를 확인하고 있어요", 21.0f, s.getInk(), true);
        add(card, statusTitle, 14);
        TextView statusBody = s.caption(c, "");
        add(card, statusBody, 6);
        Button accessibility = s.button(c, "접근성 켜기", AppStyle.Tone.TONAL, onAccessibility);
        accessibility.setVisibility(View.GONE);
        add(card, accessibility, 16);
        add(card, s.divider(c), 18);
        add(card, s.caption(c, "수집한 단어장은 이번 실행에만 사용하고 끝나면 지웁니다."), 14);
        add(content, card, 24);

        // primary actions
        Button primary = s.button(c, "클래스카드에서 시작", AppStyle.Tone.PRIMARY, onStart);
        primary.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        primary.getCompoundDrawablesRelative()[0].setTint(-1);
        primary.setCompoundDrawablePadding(dp(c, 8));
        primary.setMinHeight(dp(c, 58));
        primary.setMinimumHeight(dp(c, 58));
        primary.setTextSize(16.0f);
        add(content, primary, 20);
        Button stop = s.button(c, "실행 중지", AppStyle.Tone.DANGER, onStop);
        stop.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_stop, 0, 0, 0);
        stop.getCompoundDrawablesRelative()[0].setTint(s.getDanger());
        stop.setCompoundDrawablePadding(dp(c, 8));
        stop.setVisibility(View.GONE);
        add(content, stop, 10);

        // what the next run will do, in short; tapping opens the settings
        LinearLayout summary = s.card(c);
        summary.setPadding(dp(c, 20), dp(c, 6), dp(c, 12), dp(c, 6));
        for (int i = 0; i < 2; i++) {
            if (i > 0) {
                summary.addView(s.divider(c));
            }
            summary.addView(s.navRow(c, SECTIONS[i][0], sectionSummary(prefs, i), onSettings));
        }
        add(content, s.sectionLabel(c, "다음 실행"), 24);
        add(content, summary, 8);

        TextView foot = s.caption(c, "기기 내부 처리  ·  혼합 언어 OCR  ·  파일 가져오기·내보내기 없음");
        foot.setGravity(Gravity.CENTER);
        foot.setTextSize(12.0f);
        add(content, foot, 24);

        Home h = new Home(page.root, dot, chip, statusTitle, statusBody, primary, stop, accessibility, banner, bannerText);
        h.render(Link.READY, null);
        return h;
    }

    /** Card with the three scroll sliders used while the word list is being collected. */
    public static LinearLayout scrollCard(final Context c, final SharedPreferences prefs) {
        AppStyle s = AppStyle.INSTANCE;
        final LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(0);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(s.icon(c, R.drawable.ic_tune, s.getAccent(), 22));
        TextView t = s.title(c, "단어 수집 스크롤");
        t.setTextSize(17.0f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tp.leftMargin = dp(c, 10);
        head.addView(t, tp);
        card.addView(head);
        add(card, s.caption(c, "처음 단어를 모을 때 목록을 넘기는 방식입니다. 범위가 너무 크면 단어를 놓칠 수 있어요."), 8);
        add(card, s.switchRow(c, "끝에서 멈춰 관성 막기", "스와이프 끝에서 손을 바로 떼지 않고 0.15초 멈췄다가 떼서, 목록이 관성으로 더 미끄러지지 않고 설정한 범위만큼만 넘어갑니다.", prefs.getBoolean("collect_end_hold", true), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.edit().putBoolean("collect_end_hold", checked).apply();
            }
        }), 12);
        addSliders(c, card, prefs, false);
        Button reset = s.button(c, "기본값으로", AppStyle.Tone.TEXT, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                ScrollSettings.reset(prefs);
                // rebuild the card in place so the sliders jump back to the defaults
                android.view.ViewGroup parent = (android.view.ViewGroup) card.getParent();
                if (parent != null) {
                    int index = parent.indexOfChild(card);
                    android.view.ViewGroup.LayoutParams params = card.getLayoutParams();
                    parent.removeViewAt(index);
                    parent.addView(scrollCard(c, prefs), index, params);
                }
                return Unit.INSTANCE;
            }
        });
        LinearLayout.LayoutParams rp = lp(c, 8);
        rp.width = -2;
        rp.gravity = Gravity.END;
        card.addView(reset, rp);
        return card;
    }

    /**
     * Sliders for the formerly fixed numbers. {@code kind} 0 = answer matching, 1 = run limits. Seconds are shown for
     * every time value. A "기본값으로" button restores the built-in defaults.
     */
    public static LinearLayout advancedCard(final Context c, final SharedPreferences p, final int kind) {
        AppStyle s = AppStyle.INSTANCE;
        final LinearLayout card = s.card(c);
        if (kind == 0) {
            card.addView(s.caption(c, "화면 글자가 조금 틀리게 읽혔거나 보기가 잘렸을 때 얼마나 너그럽게 정답으로 볼지 정합니다. 너그러울수록 완주는 잘 되지만 오답이 늘 수 있어요. 다음 실행부터 적용됩니다."));
            card.addView(s.sliderRow(c, "글자 인식 오차 허용(유사도 하한)", "%", AdvancedSettings.FUZZY_MIN, AdvancedSettings.FUZZY_MAX, AdvancedSettings.FUZZY_STEP, AdvancedSettings.fuzzyPercent(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setFuzzyPercent(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "동점일 때 필요한 격차", "%", AdvancedSettings.TIE_MIN, AdvancedSettings.TIE_MAX, AdvancedSettings.TIE_STEP, AdvancedSettings.tiePercent(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setTiePercent(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "잘린 보기 최소 일치 글자", "글자", AdvancedSettings.PARTIAL_MIN, AdvancedSettings.PARTIAL_MAX, AdvancedSettings.PARTIAL_STEP, AdvancedSettings.partialMin(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setPartialMin(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
        } else if (kind == 2) {
            card.addView(s.caption(c, "‘평균·랜덤’ 방식의 세부 값입니다. ‘내 패턴’을 쓰는 동안에는 적용되지 않아요(내 패턴은 따로 저장돼요). 다음 실행부터 적용됩니다."));
            card.addView(s.sliderRow(c, "응답 시간의 변동 폭", "%", HumanProfile.SIGMA_MIN, HumanProfile.SIGMA_MAX, HumanProfile.SIGMA_STEP, HumanProfile.sigmaPct(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_SIGMA, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "가끔 더 망설일 확률", "%", HumanProfile.HESITATE_MIN, HumanProfile.HESITATE_MAX, HumanProfile.HESITATE_STEP, HumanProfile.hesitatePct(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_HESITATE, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "문항마다 느려지는 정도", "‰", HumanProfile.FATIGUE_MIN, HumanProfile.FATIGUE_MAX, HumanProfile.FATIGUE_STEP, HumanProfile.fatiguePermille(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_FATIGUE, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "답을 누른 뒤 카드 넘기기 전 멈춤", "ms", HumanProfile.PAUSE_MIN, HumanProfile.PAUSE_MAX, HumanProfile.PAUSE_STEP, (int) HumanProfile.advanceMs(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_ADVANCE, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "보기를 열기 전 멈춤", "ms", HumanProfile.PAUSE_MIN, HumanProfile.PAUSE_MAX, HumanProfile.PAUSE_STEP, (int) HumanProfile.revealMs(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_REVEAL, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "시작 버튼 누르기 전 기다림", "ms", HumanProfile.NAV_MIN, HumanProfile.NAV_MAX, HumanProfile.NAV_STEP, (int) HumanProfile.navMs(p, false), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    p.edit().putInt(HumanProfile.K_NAV, v.intValue()).apply();
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
        } else if (kind == 3) {
            String mine = HumanProfile.describeMine(p);
            card.addView(s.caption(c, mine.isEmpty() ? "아직 측정한 내 패턴이 없어요. 시작 전 패널의 ‘입력’ 탭의 ‘측정 시작’으로 직접 테스트를 풀어 만들 수 있어요." : "측정한 내 패턴: " + mine + ". 평균·랜덤 값과 따로 저장되어 있어요."));
        } else {
            card.addView(s.caption(c, "실행이 너무 오래 걸리거나 화면이 멈춘 것처럼 보일 때 언제 포기할지 정합니다. 다음 실행부터 적용됩니다."));
            card.addView(s.sliderRow(c, "실행 제한 시간", "초", AdvancedSettings.RUN_MIN, AdvancedSettings.RUN_MAX, AdvancedSettings.RUN_STEP, AdvancedSettings.runLimitSeconds(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setRunLimitSeconds(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "화면 전환 대기 한도", "초", AdvancedSettings.PHASE_MIN, AdvancedSettings.PHASE_MAX, AdvancedSettings.PHASE_STEP, AdvancedSettings.phaseSeconds(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setPhaseSeconds(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "카드가 멈춰 있어야 하는 시간", "ms", AdvancedSettings.GATE_MIN, AdvancedSettings.GATE_MAX, AdvancedSettings.GATE_STEP, AdvancedSettings.gateMs(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setGateMs(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "자동 대기 최대 시간(화면 이탈·잠김)", "초", AdvancedSettings.HOLD_MIN, AdvancedSettings.HOLD_MAX, AdvancedSettings.HOLD_STEP, AdvancedSettings.holdSeconds(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setHoldSeconds(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "문항당 최대 응답 시간", "초", AdvancedSettings.QCAP_MIN, AdvancedSettings.QCAP_MAX, AdvancedSettings.QCAP_STEP, AdvancedSettings.questionCapSeconds(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setQuestionCapSeconds(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "단어 수집 스크롤 최대 횟수", "회", AdvancedSettings.SCROLL_MIN, AdvancedSettings.SCROLL_MAX, AdvancedSettings.SCROLL_STEP, AdvancedSettings.scrollSteps(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setScrollSteps(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "직접 터치로 일시정지되는 횟수", "회", AdvancedSettings.TOUCH_MIN, AdvancedSettings.TOUCH_MAX, AdvancedSettings.TOUCH_STEP, AdvancedSettings.touchPauseCount(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setTouchPauseCount(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
            card.addView(s.sliderRow(c, "안 눌린 터치 재시도 횟수", "회", AdvancedSettings.RETRY_MIN, AdvancedSettings.RETRY_MAX, AdvancedSettings.RETRY_STEP, AdvancedSettings.tapRetries(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setTapRetries(p, v.intValue());
                    return Unit.INSTANCE;
                }
            }, false), lp(c, 16));
        }
        Button reset = s.button(c, kind == 3 ? "내 패턴 지우기" : "기본값으로", AppStyle.Tone.TEXT, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                if (kind == 0) {
                    AdvancedSettings.resetMatching(p);
                } else if (kind == 2) {
                    HumanProfile.resetDefaults(p);
                } else if (kind == 3) {
                    HumanProfile.clearMine(p);
                } else {
                    AdvancedSettings.resetLimits(p);
                }
                android.view.ViewGroup parent = (android.view.ViewGroup) card.getParent();
                if (parent != null) {
                    int index = parent.indexOfChild(card);
                    android.view.ViewGroup.LayoutParams params = card.getLayoutParams();
                    parent.removeViewAt(index);
                    parent.addView(advancedCard(c, p, kind), index, params);
                }
                return Unit.INSTANCE;
            }
        });
        LinearLayout.LayoutParams rp = lp(c, 8);
        rp.width = -2;
        rp.gravity = Gravity.END;
        card.addView(reset, rp);
        return card;
    }

    /** Appends the range / duration / interval sliders to {@code parent}. */
    public static void addSliders(Context c, LinearLayout parent, final SharedPreferences prefs, boolean compact) {
        AppStyle s = AppStyle.INSTANCE;
        int top = compact ? 4 : 16;
        parent.addView(s.sliderRow(c, "한 번에 넘기는 범위", "%", ScrollSettings.RANGE_MIN, ScrollSettings.RANGE_MAX, ScrollSettings.RANGE_STEP, ScrollSettings.rangePercent(prefs), new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer v) {
                ScrollSettings.setRangePercent(prefs, v.intValue());
                return Unit.INSTANCE;
            }
        }, compact), lp(c, top));
        parent.addView(s.sliderRow(c, "스와이프 시간", "ms", ScrollSettings.DURATION_MIN, ScrollSettings.DURATION_MAX, ScrollSettings.DURATION_STEP, ScrollSettings.durationMs(prefs), new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer v) {
                ScrollSettings.setDurationMs(prefs, v.intValue());
                return Unit.INSTANCE;
            }
        }, compact), lp(c, top));
        parent.addView(s.sliderRow(c, "스크롤 사이 대기", "ms", ScrollSettings.INTERVAL_MIN, ScrollSettings.INTERVAL_MAX, ScrollSettings.INTERVAL_STEP, ScrollSettings.intervalMs(prefs), new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer v) {
                ScrollSettings.setIntervalMs(prefs, v.intValue());
                return Unit.INSTANCE;
            }
        }, compact), lp(c, top));
    }

    /**
     * One tab of the dark floating panel's settings (0 입력 · 1 시험 · 2 안전 · 3 수집). The values live in the same
     * preferences as the app's own settings screens.
     */
    public static void addPanelGroup(final Context c, LinearLayout parent, final SharedPreferences p, int group) {
        AppStyle s = AppStyle.INSTANCE;
        switch (group) {
            case 0: {
                parent.addView(panelSwitch(c, "초고속 모드", p.getBoolean("turbo", false), "turbo", p), lp(c, 6));
                parent.addView(panelNote(c, "앱의 대기를 최대한 줄여요. 켜면 사람처럼 입력은 쓰지 않아요."), lp(c, 0));
                parent.addView(panelSwitch(c, "└ 확인 없이 넘기기", p.getBoolean("turbo_skip_confirm", false), "turbo_skip_confirm", p), lp(c, 4));
                parent.addView(panelNote(c, "답을 누르자마자 넘겨요. 터치가 안 먹히면 그 문항은 틀려요. 초고속일 때만 동작."), lp(c, 0));
                parent.addView(panelSwitch(c, "└ 구간 먼저 전부 펼치기", p.getBoolean("turbo_expand_first", false), "turbo_expand_first", p), lp(c, 4));
                parent.addView(panelNote(c, "수집 전에 맨 아래로 크게 내린 뒤 아래 구간부터 펼치며 올라와요. 초고속일 때만 동작."), lp(c, 0));
                parent.addView(panelSwitch(c, "사람처럼 입력", p.getBoolean("human_like", false), "human_like", p), lp(c, 8));
                final TextView estimate = panelNote(c, paceEstimate(p));
                final LinearLayout meanRow = s.sliderRow(c, "평균 응답 시간", "ms", HumanTiming.MEAN_MIN, HumanTiming.MEAN_MAX, HumanTiming.MEAN_STEP, HumanProfile.meanMs(p, false), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        p.edit().putInt(HumanProfile.K_MEAN, v.intValue()).apply();
                        estimate.setText(paceEstimate(p));
                        return Unit.INSTANCE;
                    }
                }, true);
                parent.addView(panelHeading(c, "응답 속도 기준"), lp(c, 10));
                parent.addView(sourceChooser(c, p, true, new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        dim(meanRow, HumanProfile.usingMine(p));
                        estimate.setText(paceEstimate(p));
                        return Unit.INSTANCE;
                    }
                }), lp(c, 6));
                parent.addView(meanRow, lp(c, 8));
                dim(meanRow, HumanProfile.usingMine(p));
                parent.addView(estimate, lp(c, 2));
                String measured = HumanProfile.describeMine(p);
                parent.addView(panelHeading(c, "내 패턴"), lp(c, 12));
                parent.addView(panelNote(c, measured.isEmpty() ? "아직 측정하지 않았어요. 측정을 누른 뒤 클래스카드에서 직접 풀면 속도를 기록해요." : measured), lp(c, 2));
                TextView measure = s.label(c, measured.isEmpty() ? "측정 시작" : "다시 측정", 12.0f, -1, true);
                measure.setGravity(Gravity.CENTER);
                measure.setBackground(s.roundRect(c, android.graphics.Color.argb(34, 255, 255, 255), 999, 0));
                measure.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BotService service = BotService.INSTANCE.getInstance();
                        if (service != null) {
                            service.toggleCalibration();
                        }
                    }
                });
                LinearLayout.LayoutParams mlp = lp(c, 8);
                mlp.height = dp(c, 30);
                parent.addView(measure, mlp);
                break;
            }
            case 1:
                parent.addView(s.sliderRow(c, "일부러 틀릴 문항 수", "개", AdvancedSettings.WRONG_MIN, AdvancedSettings.WRONG_MAX, AdvancedSettings.WRONG_STEP, AdvancedSettings.wrongCount(p), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        AdvancedSettings.setWrongCount(p, v.intValue());
                        return Unit.INSTANCE;
                    }
                }, true), lp(c, 8));
                parent.addView(panelNote(c, "실제 시험에 나온 문항 중에서 무작위로 골라 틀려요."), lp(c, 2));
                parent.addView(s.sliderRow(c, "문항당 최대 응답 시간", "초", AdvancedSettings.QCAP_MIN, AdvancedSettings.QCAP_MAX, AdvancedSettings.QCAP_STEP, AdvancedSettings.questionCapSeconds(p), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        AdvancedSettings.setQuestionCapSeconds(p, v.intValue());
                        return Unit.INSTANCE;
                    }
                }, true), lp(c, 10));
                parent.addView(panelNote(c, "랜덤 시간이 길게 뽑혀도 이 안에 답해요. 클래스카드 문항 제한시간보다 짧게 두세요."), lp(c, 2));
                parent.addView(s.sliderRow(c, "최대 실행 시간", "초", AdvancedSettings.RUN_MIN, AdvancedSettings.RUN_MAX, AdvancedSettings.RUN_STEP, AdvancedSettings.runLimitSeconds(p), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        AdvancedSettings.setRunLimitSeconds(p, v.intValue());
                        return Unit.INSTANCE;
                    }
                }, true), lp(c, 10));
                parent.addView(panelNote(c, paceEstimate(p)), lp(c, 2));
                break;
            case 2:
                parent.addView(panelSwitch(c, "실행 중 화면 터치 잠금", p.getBoolean("touch_lock", true), "touch_lock", p), lp(c, 6));
                parent.addView(panelSwitch(c, "멈추면 진동으로 알림", p.getBoolean("vibrate_alerts", true), "vibrate_alerts", p), lp(c, 2));
                parent.addView(s.sliderRow(c, "터치 몇 번에 일시정지", "회", AdvancedSettings.TOUCH_MIN, AdvancedSettings.TOUCH_MAX, AdvancedSettings.TOUCH_STEP, AdvancedSettings.touchPauseCount(p), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        AdvancedSettings.setTouchPauseCount(p, v.intValue());
                        return Unit.INSTANCE;
                    }
                }, true), lp(c, 8));
                parent.addView(panelNote(c, "잠금이 꺼져 있을 때, 시험 중 화면을 직접 이만큼 누르면 멈춰요."), lp(c, 2));
                break;
            default:
                parent.addView(panelNote(c, "처음 단어를 모을 때 목록을 넘기는 방식이에요."), lp(c, 8));
                parent.addView(panelSwitch(c, "끝에서 멈춰 관성 막기", p.getBoolean("collect_end_hold", true), "collect_end_hold", p), lp(c, 6));
                parent.addView(panelNote(c, "손을 떼기 전 0.15초 멈춰서, 설정한 범위만큼만 넘어가요."), lp(c, 0));
                parent.addView(panelSwitch(c, "수집 중 스와이프 표시", p.getBoolean("collect_show_swipe", true), "collect_show_swipe", p), lp(c, 6));
                TextView preview = s.label(c, "화면에서 미리 보기", 12.0f, -1, true);
                preview.setGravity(Gravity.CENTER);
                preview.setBackground(s.roundRect(c, android.graphics.Color.argb(34, 255, 255, 255), 999, 0));
                preview.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        BotService service = BotService.INSTANCE.getInstance();
                        if (service != null) {
                            service.previewCollectionSwipe();
                        }
                    }
                });
                LinearLayout.LayoutParams plp = lp(c, 8);
                plp.height = dp(c, 30);
                parent.addView(preview, plp);
                addSliders(c, parent, p, true);
                break;
        }
    }

    private static TextView panelHeading(Context c, String text) {
        TextView t = AppStyle.INSTANCE.label(c, text, 11.0f, -1, true);
        t.setAlpha(0.65f);
        return t;
    }

    private static TextView panelNote(Context c, String text) {
        TextView t = AppStyle.INSTANCE.label(c, text, 10.5f, -1, false);
        t.setAlpha(0.7f);
        t.setLineSpacing(0, 1.1f);
        return t;
    }

    /** Greys out (and disables) a group of controls that are not in use. */
    static void dim(View v, boolean dimmed) {
        v.setAlpha(dimmed ? 0.35f : 1.0f);
        v.setEnabled(!dimmed);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                dim(g.getChildAt(i), dimmed);
            }
        }
    }

    /**
     * Two choices that exclude each other: "평균·랜덤" (the hand-made numbers) or "내 패턴" (the measured ones, stored
     * separately). Choosing "내 패턴" before anything was measured is refused with a hint.
     */
    public static LinearLayout sourceChooser(final Context c, final SharedPreferences p, final boolean compact, final Function0<Unit> onChanged) {
        final AppStyle s = AppStyle.INSTANCE;
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] pills = new TextView[2];
        String[] labels = {"평균·랜덤", "내 패턴"};
        for (int i = 0; i < 2; i++) {
            final int index = i;
            TextView pill = s.label(c, labels[i], compact ? 11.5f : 13.5f, -1, true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(c, 12), dp(c, compact ? 6 : 9), dp(c, 12), dp(c, compact ? 6 : 9));
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!HumanProfile.setUseMine(p, index == 1)) {
                        android.widget.Toast.makeText(c, "먼저 패널 ‘입력’ 탭의 ‘측정 시작’으로 내 패턴을 만들어 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }
                    styleChooser(c, p, pills, compact);
                    if (onChanged != null) {
                        onChanged.invoke();
                    }
                }
            });
            pills[i] = pill;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
            if (i == 1) {
                lp.leftMargin = dp(c, 6);
            }
            row.addView(pill, lp);
        }
        styleChooser(c, p, pills, compact);
        return row;
    }

    private static void styleChooser(Context c, SharedPreferences p, TextView[] pills, boolean compact) {
        AppStyle s = AppStyle.INSTANCE;
        boolean mine = HumanProfile.usingMine(p);
        for (int i = 0; i < 2; i++) {
            boolean selected = (i == 1) == mine;
            pills[i].setTextColor(selected ? -1 : (compact ? android.graphics.Color.argb(200, 255, 255, 255) : s.getInk()));
            int bg = selected ? s.getAccent() : (compact ? android.graphics.Color.argb(34, 255, 255, 255) : s.getBackground());
            pills[i].setBackground(s.roundRect(c, bg, 999, selected || compact ? 0 : s.getBorder()));
            // the measured choice stays visible but looks off until there is a measurement
            pills[i].setAlpha(i == 1 && !HumanProfile.hasMine(p) ? 0.45f : 1.0f);
        }
    }

    /** One line saying how long a test is expected to take with the current settings (a rough estimate). */
    public static String paceEstimate(SharedPreferences p) {
        if (p.getBoolean("turbo", false)) {
            return "초고속 모드 · 속도는 클래스카드 화면 전환 속도에 달려 있어요";
        }
        long per = HumanTiming.estimatePerQuestionMs(p.getBoolean("human_like", false), HumanProfile.meanMs(p), AdvancedSettings.gateMs(p));
        return "문항당 약 " + String.format(java.util.Locale.US, "%.1f", per / 1000.0) + "초 · 20문항 약 " + AppStyle.formatClock(per * 20 / 1000) + " · 50문항 약 " + AppStyle.formatClock(per * 50 / 1000);
    }

    private static LinearLayout panelSwitch(Context c, String title, boolean checked, final String key, final SharedPreferences p) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(s.label(c, title, 12.5f, -1, true), new LinearLayout.LayoutParams(0, -2, 1.0f));
        final android.widget.CompoundButton sw = s.toggle(c, checked, new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                p.edit().putBoolean(key, on).apply();
            }
        });
        sw.setScaleX(0.8f);
        sw.setScaleY(0.8f);
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.toggle();
            }
        });
        return row;
    }

    // ------------------------------------------------------------ settings

    public static final class Nav {
        public final String title;
        public final String description;
        public final Function0<Unit> action;

        public Nav(String title, String description, Function0<Unit> action) {
            this.title = title;
            this.description = description;
            this.action = action;
        }
    }

    /** Settings categories, in menu order: title, one-line description. */
    public static final String[][] SECTIONS = {
            {"입력 방식", "사람처럼 입력 · 응답 속도 · 내 패턴 · 오답 수"},
            {"안전 · 잠금", "터치 잠금 · 진동 알림 · 실행 한도"},
            {"단어 읽기 · 수집", "화면 이미지로 읽기 · 스크롤"},
            {"정답 매칭", "보기를 고르는 기준 (고급)"},
    };

    /** The settings menu: one row per category, then help and records. {@code onOpen} gets the category index. */
    public static LinearLayout settings(Context c, SharedPreferences prefs, String versionName, Function0<Unit> onBack, List<Nav> help, final Function1<Integer, Unit> onOpen) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, titleBar(c, "설정", onBack));
        LinearLayout content = page.content;

        add(content, s.sectionLabel(c, "실행 설정"), 12);
        LinearLayout menu = s.card(c);
        menu.setPadding(dp(c, 20), dp(c, 6), dp(c, 12), dp(c, 6));
        for (int i = 0; i < SECTIONS.length; i++) {
            final int index = i;
            if (i > 0) {
                menu.addView(s.divider(c));
            }
            menu.addView(s.navRow(c, SECTIONS[i][0], sectionSummary(prefs, i), new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    return onOpen.invoke(index);
                }
            }));
        }
        add(content, menu, 8);
        add(content, s.caption(c, "자주 바꾸는 값은 클래스카드 위 플로팅 패널에서도 바로 바꿀 수 있어요. 두 곳은 같은 값을 씁니다."), 10);

        add(content, s.sectionLabel(c, "안내 및 기록"), 24);
        LinearLayout list = s.card(c);
        list.setPadding(dp(c, 20), dp(c, 6), dp(c, 12), dp(c, 6));
        for (int i = 0; i < help.size(); i++) {
            Nav n = help.get(i);
            if (i > 0) {
                list.addView(s.divider(c));
            }
            list.addView(s.navRow(c, n.title, n.description, n.action));
        }
        add(content, list, 8);

        TextView ver = s.caption(c, "버전 " + versionName);
        ver.setGravity(Gravity.CENTER);
        add(content, ver, 24);
        return page.root;
    }

    /** The current values of a category, in one line, shown under its title in the menu. */
    static String sectionSummary(SharedPreferences p, int section) {
        switch (section) {
            case 0:
                if (p.getBoolean("turbo", false)) {
                    return "초고속 모드 · 오답 " + AdvancedSettings.wrongCount(p) + "개";
                }
                if (!p.getBoolean("human_like", false)) {
                    return "사람처럼 입력 꺼짐 · 오답 " + AdvancedSettings.wrongCount(p) + "개";
                }
                return (HumanProfile.usingMine(p) ? "내 패턴" : "평균 " + AppStyle.formatValue(HumanProfile.meanMs(p, false), "ms")) + " · 오답 " + AdvancedSettings.wrongCount(p) + "개";
            case 1:
                return "잠금 " + (p.getBoolean("touch_lock", true) ? "켜짐" : "꺼짐") + " · 진동 " + (p.getBoolean("vibrate_alerts", true) ? "켜짐" : "꺼짐") + " · 최대 " + AppStyle.formatClock(AdvancedSettings.runLimitSeconds(p));
            case 2:
                return (p.getBoolean("image_collection", false) ? "이미지로 읽기" : "화면 글자 우선") + " · 넘김 " + ScrollSettings.rangePercent(p) + "%";
            default:
                return SECTIONS[section][1];
        }
    }

    /** One settings category on its own page. */
    public static LinearLayout settingsSection(Context c, SharedPreferences prefs, int section, Function0<Unit> onBack) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, titleBar(c, SECTIONS[section][0], onBack));
        LinearLayout content = page.content;
        final SharedPreferences p = prefs;
        switch (section) {
            case 0: {
                LinearLayout humanCard = s.card(c);
                humanCard.addView(s.switchRow(c, "초고속 모드", "단어 수집이 끝난 뒤부터 화면을 더 자주 읽고(0.1초→0.035초), 앱이 스스로 두는 대기·스와이프 시간·시작 카운트다운(8초→3초)을 줄입니다. 단어 수집은 정확도를 위해 원래 속도로 합니다. 클래스카드 자체 애니메이션은 줄일 수 없습니다. 켜면 사람처럼 입력보다 우선합니다.", p.getBoolean("turbo", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("turbo", checked).apply();
                    }
                }));
                add(humanCard, s.divider(c), 16);
                add(humanCard, s.switchRow(c, "확인 없이 넘기기 (초고속 전용)", "답을 누른 뒤 정답/오답 표시를 기다리지 않고 바로 다음 문항으로 넘깁니다. 터치가 반영되지 않아도 다시 누르지 않으므로 그 문항은 틀릴 수 있습니다. 초고속 모드가 켜져 있을 때만 동작합니다.", p.getBoolean("turbo_skip_confirm", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("turbo_skip_confirm", checked).apply();
                    }
                }), 16);
                add(humanCard, s.divider(c), 16);
                add(humanCard, s.switchRow(c, "구간 먼저 전부 펼치기 (초고속 전용)", "단어를 수집하기 전에 목록을 크게 스와이프해 맨 아래까지 내린 뒤, 화면에서 가장 아래 구간부터 눌러 펼치면서 위로 올라옵니다. 맨 위에 도착하면 평소처럼 수집합니다. 아래부터 누르면 위쪽 줄이 밀리지 않아 연달아 누를 수 있습니다.", p.getBoolean("turbo_expand_first", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("turbo_expand_first", checked).apply();
                    }
                }), 16);
                add(humanCard, s.divider(c), 16);
                add(humanCard, s.switchRow(c, "사람처럼 입력", "문제를 읽는 시간, 터치 위치와 길이, 카드를 넘기는 간격을 사람처럼 무작위로 흔듭니다. 다음 실행부터 적용됩니다.", p.getBoolean("human_like", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("human_like", checked).apply();
                    }
                }), 16);
                add(content, humanCard, 12);

                add(content, s.sectionLabel(c, "응답 속도 기준"), 24);
                LinearLayout paceCard = s.card(c);
                final LinearLayout appMeanRow = s.sliderRow(c, "평균 응답 시간", "ms", HumanTiming.MEAN_MIN, HumanTiming.MEAN_MAX, HumanTiming.MEAN_STEP, HumanProfile.meanMs(p, false), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        p.edit().putInt(HumanProfile.K_MEAN, v.intValue()).apply();
                        return Unit.INSTANCE;
                    }
                }, false);
                paceCard.addView(sourceChooser(c, p, false, new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        dim(appMeanRow, HumanProfile.usingMine(p));
                        return Unit.INSTANCE;
                    }
                }));
                add(paceCard, s.caption(c, "‘평균·랜덤’은 아래 평균과 세부 값을, ‘내 패턴’은 직접 푼 모습을 측정해 따로 저장한 값을 씁니다. 서로 덮어쓰지 않아요."), 8);
                add(paceCard, appMeanRow, 16);
                dim(appMeanRow, HumanProfile.usingMine(p));
                add(paceCard, s.caption(c, "보통 길이의 문제 기준이며 글이 길수록 오래 걸립니다. 예상: " + paceEstimate(p)), 8);
                add(content, paceCard, 8);

                add(content, s.sectionLabel(c, "오답"), 24);
                LinearLayout wrongCard = s.card(c);
                wrongCard.addView(s.sliderRow(c, "일부러 틀릴 문항 수", "개", AdvancedSettings.WRONG_MIN, AdvancedSettings.WRONG_MAX, AdvancedSettings.WRONG_STEP, AdvancedSettings.wrongCount(p), new Function1<Integer, Unit>() {
                    @Override
                    public Unit invoke(Integer v) {
                        AdvancedSettings.setWrongCount(p, v.intValue());
                        return Unit.INSTANCE;
                    }
                }, false));
                add(wrongCard, s.caption(c, "실제 시험에 나온 문항 중에서 무작위로 골라 정답이 아닌 보기를 누릅니다. 0이면 모두 맞힙니다."), 8);
                add(content, wrongCard, 8);

                add(content, s.sectionLabel(c, "측정한 내 패턴"), 24);
                add(content, advancedCard(c, p, 3), 8);
                add(content, s.sectionLabel(c, "고급 · 평균·랜덤 세부값"), 24);
                add(content, advancedCard(c, p, 2), 8);
                break;
            }
            case 1: {
                LinearLayout lockCard = s.card(c);
                lockCard.addView(s.switchRow(c, "실행 중 화면 터치 잠금", "실행 중에는 플로팅 패널 외의 화면 터치를 막습니다. 일시정지하면 풀립니다. 상태바·홈/뒤로 제스처·전원/볼륨 버튼은 막을 수 없습니다.", p.getBoolean("touch_lock", true), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("touch_lock", checked).apply();
                    }
                }));
                add(lockCard, s.divider(c), 16);
                add(lockCard, s.switchRow(c, "멈추면 진동으로 알림", "클래스카드 화면을 벗어나거나 중지·완료·오류로 끝났을 때 짧게 진동합니다.", p.getBoolean("vibrate_alerts", true), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("vibrate_alerts", checked).apply();
                    }
                }), 16);
                add(content, lockCard, 12);
                add(content, s.sectionLabel(c, "실행 한도"), 24);
                add(content, advancedCard(c, p, 1), 8);
                break;
            }
            case 2: {
                LinearLayout reading = s.card(c);
                reading.addView(s.switchRow(c, "화면 이미지로 읽기", "한국어·한자·중국어·일본어·영어를 함께 인식합니다. 기본은 화면 글자를 우선합니다.", p.getBoolean("image_collection", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("image_collection", checked).putBoolean("ocr_only", checked).apply();
                    }
                }));
                add(content, reading, 12);
                add(content, scrollCard(c, prefs), 20);
                break;
            }
            default: {
                LinearLayout partial = s.card(c);
                partial.addView(s.switchRow(c, "잘린 선택지 보조 매칭", "정확히 일치하는 보기가 없으면 일치하는 글자 수가 가장 많은 보기를 고릅니다. 추정이라 오답이 날 수 있고, 동점이거나 2글자 미만이면 멈춰요.", p.getBoolean("partial_choices", false), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        p.edit().putBoolean("partial_choices", checked).apply();
                    }
                }));
                add(content, partial, 12);
                add(content, s.sectionLabel(c, "매칭 기준"), 24);
                add(content, advancedCard(c, p, 0), 8);
                break;
            }
        }
        add(content, s.caption(c, "바뀐 값은 다음 실행부터 적용됩니다."), 16);
        return page.root;
    }

    // ------------------------------------------------------ password / consent

    public static final class Password {
        public final LinearLayout root;
        public final EditText input;
        public final TextView error;

        Password(LinearLayout root, EditText input, TextView error) {
            this.root = root;
            this.input = input;
            this.error = error;
        }
    }

    public static Password password(Context c, Function0<Unit> onUnlock, Function0<Unit> onClose) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, brandBar(c, null));
        LinearLayout content = page.content;
        add(content, s.label(c, "앱 잠금", 30.0f, s.getInk(), true), 28);
        add(content, s.label(c, "비밀번호를 입력해 앱을 열어주세요.", 15.0f, s.getMuted(), false), 10);

        EditText input = new EditText(c);
        input.setHint("비밀번호");
        input.setHintTextColor(s.getMuted());
        input.setTextColor(s.getInk());
        input.setTextSize(17.0f);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setImeOptions(6);
        input.setSaveEnabled(false);
        input.setImportantForAutofill(2);
        input.setBackground(s.roundRect(c, s.getSurface(), 16, s.getBorder()));
        input.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 16));
        add(content, input, 28);
        TextView error = s.label(c, "", 14.0f, s.getDanger(), false);
        add(content, error, 10);
        Button unlock = s.button(c, "잠금 해제", AppStyle.Tone.PRIMARY, onUnlock);
        unlock.setMinHeight(dp(c, 56));
        add(content, unlock, 14);
        add(content, s.button(c, "닫기", AppStyle.Tone.TEXT, onClose), 6);
        return new Password(page.root, input, error);
    }

    public static final class Consent {
        public final LinearLayout root;

        Consent(LinearLayout root) {
            this.root = root;
        }
    }

    /** Two-step consent screen. {@code second} selects step 2/2. */
    public static Consent consent(Context c, boolean second, String noticeText, String disclosureText, Function0<Unit> onPrivacy, Function0<Unit> onAgree, Function0<Unit> onDecline) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(1);
        root.setBackgroundColor(s.getBackground());
        root.setFocusableInTouchMode(true);
        root.addView(brandBar(c, null), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(c);
        header.setOrientation(1);
        header.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), 0);
        header.addView(s.label(c, second ? "마지막으로 확인해주세요" : "사용 안내", 27.0f, s.getInk(), true));
        add(header, s.caption(c, second ? "2 / 2  ·  이용 조건 확인" : "1 / 2  ·  안내 확인"), 8);
        LinearLayout steps = new LinearLayout(c);
        for (int i = 0; i < 2; i++) {
            View bar = new View(c);
            bar.setBackground(s.roundRect(c, (i == 0 || second) ? s.getAccent() : s.getBorder(), 3, 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(c, 5), 1.0f);
            if (i == 1) {
                p.leftMargin = dp(c, 6);
            }
            steps.addView(bar, p);
        }
        add(header, steps, 16);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(c);
        scroll.setOverScrollMode(2);
        LinearLayout card = s.card(c);
        if (second) {
            card.addView(s.title(c, "사용 안내를 확인했습니다"));
            add(card, s.body(c, "앞 화면의 사용 안내를 읽고 이해했으며, 해당 안내에 동의합니다."), 12);
            TextView sub = s.title(c, "접근성 권한과 자동 조작");
            sub.setTextSize(17.0f);
            add(card, sub, 22);
            add(card, s.body(c, disclosureText), 10);
            add(card, s.caption(c, "동의 시각과 약관 버전을 이 휴대폰에 저장합니다. 같은 약관에는 다시 동의하지 않아도 됩니다."), 20);
        } else {
            card.addView(s.body(c, noticeText));
        }
        scroll.addView(card, new FrameLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, 0, 1.0f);
        sp.leftMargin = dp(c, 20);
        sp.rightMargin = dp(c, 20);
        sp.topMargin = dp(c, 18);
        root.addView(scroll, sp);

        LinearLayout actions = new LinearLayout(c);
        actions.setOrientation(1);
        actions.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        Button agree = s.button(c, second ? "동의하고 시작하기 · 2/2" : "안내에 동의합니다 · 1/2", AppStyle.Tone.PRIMARY, onAgree);
        agree.setMinHeight(dp(c, 56));
        actions.addView(agree, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout second2 = new LinearLayout(c);
        second2.setOrientation(0);
        Button privacy = s.button(c, "개인정보처리방침", AppStyle.Tone.OUTLINE, onPrivacy);
        Button decline = s.button(c, "동의하지 않고 닫기", AppStyle.Tone.TEXT, onDecline);
        second2.addView(privacy, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(0, -2, 1.0f);
        dp2.leftMargin = dp(c, 8);
        second2.addView(decline, dp2);
        add(actions, second2, 10);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return new Consent(root);
    }

    private static String formatTime(long millis) {
        return new java.text.SimpleDateFormat("M월 d일 HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date(millis));
    }

    private static int codeColor(String code) {
        AppStyle s = AppStyle.INSTANCE;
        switch (code) {
            case "PROMPT_NOT_FOUND":
            case "CHOICE_NOT_FOUND":
                return s.getDanger();
            case "UNCLASSIFIED":
                return s.getWarn();
            default:
                return s.getInfo();
        }
    }

    private static int codeSoft(String code) {
        AppStyle s = AppStyle.INSTANCE;
        switch (code) {
            case "PROMPT_NOT_FOUND":
            case "CHOICE_NOT_FOUND":
                return s.getSoftDanger();
            case "UNCLASSIFIED":
                return s.getSoftWarn();
            default:
                return s.getSoftInfo();
        }
    }

    /** One failure as a card: reason chip + time, explanation, progress and, when stored, the question's text. */
    public static LinearLayout failureCard(Context c, FailureLog.Record r) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(0);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(s.chip(c, FailureLog.label(r.code), codeColor(r.code), codeSoft(r.code)));
        View spacer = new View(c);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));
        head.addView(s.caption(c, formatTime(r.time)));
        card.addView(head);

        String meta = r.progress.isEmpty() ? "" : "문항 " + r.progress.replace(" | ", " / ");
        if (r.cards > 0) {
            meta += (meta.isEmpty() ? "" : "  ·  ") + "수집한 단어 " + r.cards + "개";
        }
        if (!r.pace.isEmpty()) {
            meta += (meta.isEmpty() ? "" : "  ·  ") + (r.pace.equals("human") ? "사람처럼" : r.pace.equals("natural") ? "자연 모드" : r.pace.equals("turbo") ? "초고속" : "빠른 모드");
        }
        if (!meta.isEmpty()) {
            add(card, s.label(c, meta, 15.0f, s.getInk(), true), 12);
        }
        add(card, s.caption(c, FailureLog.hint(r.code)), 6);

        return card;
    }

    /** The failure history screen. Records are given newest first. */
    public static LinearLayout failures(Context c, java.util.List<FailureLog.Record> records, Function0<Unit> onBack, Function0<Unit> onCopy, Function0<Unit> onClear) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, titleBar(c, "실패 기록", onBack));
        LinearLayout content = page.content;
        if (records.isEmpty()) {
            LinearLayout empty = s.card(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(s.icon(c, R.drawable.ic_check, s.getAccent(), 36));
            TextView t = s.title(c, "기록된 실패가 없어요");
            t.setGravity(Gravity.CENTER);
            add(empty, t, 12);
            TextView d = s.caption(c, "실행이 멈추면 멈춘 시각과 이유가 여기에 쌓여요. 문제나 단어의 글은 저장하지 않아요.");
            d.setGravity(Gravity.CENTER);
            add(empty, d, 8);
            add(content, empty, 16);
            return page.root;
        }
        add(content, s.caption(c, "최근 " + records.size() + "건 · 최신순 · 멈춘 시각과 이유만 저장해요"), 8);
        for (FailureLog.Record r : records) {
            add(content, failureCard(c, r), 12);
        }
        add(content, s.button(c, "개발자용으로 기록 복사", AppStyle.Tone.TONAL, onCopy), 20);
        add(content, s.button(c, "기록 모두 지우기", AppStyle.Tone.DANGER, onClear), 10);
        return page.root;
    }

    public static final class Activation {
        public final LinearLayout root;
        public final TextView status;
        public final android.widget.EditText codeInput;

        Activation(LinearLayout root, TextView status, android.widget.EditText codeInput) {
            this.root = root;
            this.status = status;
            this.codeInput = codeInput;
        }
    }

    private static String grouped(String code) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                out.append(' ');
            }
            out.append(code.charAt(i));
        }
        return out.toString();
    }

    private static LinearLayout stepRow(Context c, String number, String title, String body, View extra) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(0);
        TextView badge = s.label(c, number, 14.0f, s.getAccentDark(), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(s.roundRect(c, s.getSoftAccent(), 999, 0));
        row.addView(badge, new LinearLayout.LayoutParams(dp(c, 28), dp(c, 28)));
        LinearLayout text = new LinearLayout(c);
        text.setOrientation(1);
        text.addView(s.label(c, title, 16.0f, s.getInk(), true));
        if (body != null) {
            add(text, s.caption(c, body), 4);
        }
        if (extra != null) {
            add(text, extra, 10);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        tp.leftMargin = dp(c, 12);
        row.addView(text, tp);
        return row;
    }

    private static TextView codeBox(Context c, String text, float size) {
        AppStyle s = AppStyle.INSTANCE;
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(size);
        t.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        t.setTextColor(s.getInk());
        t.setTextIsSelectable(true);
        t.setBackground(s.roundRect(c, s.getBackground(), 12, s.getBorder()));
        t.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        return t;
    }

    /**
     * First-run screen shown until an authorisation file issued for this phone has been dropped into the app folder.
     * {@code code} is this phone's device code, {@code folder} the folder to copy the key file into.
     */
    public static Activation activation(Context c, String code, String folder, Function0<Unit> onCheck, Function0<Unit> onCopyCode, Function0<Unit> onCopyFolder, Function0<Unit> onSubmitAuthCode, Function0<Unit> onClose) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, brandBar(c, null));
        LinearLayout content = page.content;
        add(content, s.label(c, "인증이 필요해요", 28.0f, s.getInk(), true), 22);
        add(content, s.label(c, "이 휴대폰은 아직 인증되지 않았어요. 아래 두 방법 중 편한 쪽으로 인증하세요.", 15.0f, s.getMuted(), false), 10);
        add(content, s.sectionLabel(c, "방법 A · 컴퓨터에서 키 파일 만들기"), 18);

        LinearLayout card = s.card(c);
        LinearLayout codeBlock = new LinearLayout(c);
        codeBlock.setOrientation(0);
        codeBlock.setGravity(Gravity.CENTER_VERTICAL);
        TextView codeView = codeBox(c, grouped(code), 15.0f);
        codeBlock.addView(codeView, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button copyCode = s.button(c, "복사", AppStyle.Tone.TONAL, onCopyCode);
        copyCode.setMinHeight(dp(c, 44));
        copyCode.setMinimumHeight(dp(c, 44));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = dp(c, 8);
        codeBlock.addView(copyCode, cp);
        card.addView(stepRow(c, "1", "기기 코드 확인", "이 코드는 이 휴대폰에만 해당돼요.", codeBlock));

        add(card, stepRow(c, "2", "컴퓨터에서 키 파일 만들기", "issuer 폴더에서 아래처럼 실행하면 classcard-auth.key 가 만들어져요.", codeBox(c, "python issue.py --device " + code, 12.5f)), 22);

        LinearLayout folderBlock = new LinearLayout(c);
        folderBlock.setOrientation(1);
        folderBlock.addView(codeBox(c, folder, 12.5f));
        Button copyFolder = s.button(c, "경로 복사", AppStyle.Tone.TEXT, onCopyFolder);
        copyFolder.setMinHeight(dp(c, 40));
        copyFolder.setMinimumHeight(dp(c, 40));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, -2);
        fp.gravity = Gravity.END;
        folderBlock.addView(copyFolder, fp);
        add(card, stepRow(c, "3", "휴대폰에 넣기", "USB로 컴퓨터에 연결하고 파일 관리자에서 이 폴더를 열어 키 파일을 넣으세요. 폴더가 보이지 않으면 휴대폰을 다시 연결해 보세요.", folderBlock), 22);
        add(content, card, 10);

        add(content, s.sectionLabel(c, "방법 C · 인증용 앱(MiMiC Auth)으로 코드 받기"), 22);
        LinearLayout cardC = s.card(c);
        final android.widget.EditText codeInput = new android.widget.EditText(c);
        try {
            if (AuthChallenge.available()) {
                cardC.addView(s.label(c, "내 인증용 앱으로 아래 QR을 찍으면 앱에 짧은 코드가 떠요. 그 코드를 입력하세요.", 14.0f, s.getMuted(), false));
                android.widget.ImageView qr = new android.widget.ImageView(c);
                qr.setImageBitmap(QrCodec.render(AuthChallenge.qrText(c), 700));
                qr.setAdjustViewBounds(true);
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(c, 190), dp(c, 190));
                qp.topMargin = dp(c, 12);
                qp.gravity = Gravity.CENTER_HORIZONTAL;
                cardC.addView(qr, qp);
            } else {
                cardC.addView(s.label(c, "이 빌드에는 인증용 앱이 아직 등록되지 않았어요. 인증용 앱의 공개키를 넣어 다시 빌드하면 쓸 수 있어요.", 14.0f, s.getMuted(), false));
            }
        } catch (Exception e) {
            cardC.addView(s.label(c, "QR을 만들지 못했어요.", 14.0f, s.getDanger(), false));
        }
        codeInput.setHint("코드 입력 (예: 3-ABCDE-FGH12)");
        codeInput.setSingleLine(true);
        codeInput.setTextSize(17.0f);
        codeInput.setTextColor(s.getInk());
        codeInput.setHintTextColor(s.getMuted());
        codeInput.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeInput.setBackground(s.roundRect(c, s.getBackground(), 12, s.getBorder()));
        codeInput.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10));
        add(cardC, codeInput, 12);
        Button submit = s.button(c, "코드 확인", AppStyle.Tone.TONAL, onSubmitAuthCode);
        submit.setMinHeight(dp(c, 48));
        submit.setMinimumHeight(dp(c, 48));
        add(cardC, submit, 8);
        add(content, cardC, 10);

        TextView status = s.label(c, "", 14.0f, s.getDanger(), false);
        add(content, status, 16);
        Button check = s.button(c, "확인", AppStyle.Tone.PRIMARY, onCheck);
        check.setMinHeight(dp(c, 56));
        add(content, check, 12);
        add(content, s.button(c, "닫기", AppStyle.Tone.TEXT, onClose), 6);
        add(content, s.caption(c, "키 파일을 넣으면 몇 초 안에 자동으로 확인해요. 확인이 끝나면 파일은 지워져요."), 12);
        return new Activation(page.root, status, codeInput);
    }

    /** Simple text body for sheets (privacy policy, notices, last run). */
    public static View textBody(Context c, String text) {
        AppStyle s = AppStyle.INSTANCE;
        TextView t = s.body(c, text);
        t.setTextSize(14.5f);
        t.setTextIsSelectable(true);
        return t;
    }

    static List<Nav> navs(Nav... items) {
        List<Nav> out = new ArrayList<>();
        for (Nav n : items) {
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }
}
