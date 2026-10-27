import re
exec(open('/tmp/claude-0/work/fix.py').read())

def read(p):
    return open(D + p).read()

def write(p, s):
    open(D + p, 'w').write(s)

def sub(p, a, b, count=1):
    s = read(p)
    assert a in s, (p, a[:110])
    write(p, s.replace(a, b, count))

# ---------------- Solver: is this choice definitely not an answer? ----------------
sub('Solver.java', '    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {', '''    /** True when {@code choice} does not match the prompt's meaning at all, so it is a safe wrong answer. */
    public final boolean isWrongChoice(String prompt, UiNode choice) {
        try {
            answer(prompt, java.util.Collections.singletonList(choice));
            return false;
        } catch (AnswerResolutionException e) {
            AnswerFailure f = e.getFailure();
            return f == AnswerFailure.CHOICE_NOT_FOUND || f == AnswerFailure.PARTIAL_TOO_SHORT || f == AnswerFailure.PARTIAL_TIE;
        }
    }

    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {''')

# ---------------- BotEngine ----------------
sub('BotEngine.java', '    private boolean human;\n', '''    private boolean human;
    private java.util.Set<Integer> wrongSet;
    private final java.util.Map<String, String> wrongPick = new java.util.HashMap<>();

    /**
     * The answer to tap. Normally the correct one; for the questions chosen to be answered wrong on purpose it is a
     * choice that definitely does not match. A question keeps the same wrong choice if the tap is repeated.
     */
    private final ChoiceAnswer decideAnswer(Solver solver, String prompt, List<UiNode> choices, String progress) {
        ChoiceAnswer correct = solver.answer(prompt, choices);
        int wanted = AdvancedSettings.wrongCount(this.storage.getPrefs());
        if (wanted <= 0 || this.expectedQuestions <= 0) {
            return correct;
        }
        if (this.wrongSet == null) {
            this.wrongSet = WrongAnswers.chooseQuestions(this.rng, this.expectedQuestions, wanted);
            QuietJson planned = new QuietJson().put("count", this.wrongSet.size()).put("total", this.expectedQuestions);
            log("deliberate_wrong_planned", planned);
        }
        if (!this.wrongSet.contains(Integer.valueOf(WrongAnswers.questionNumber(progress)))) {
            return correct;
        }
        UiNode pick = null;
        String remembered = this.wrongPick.get(progress);
        if (remembered != null) {
            for (UiNode node : choices) {
                if (Intrinsics.areEqual(node.getText(), remembered)) {
                    pick = node;
                    break;
                }
            }
        }
        if (pick == null) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < choices.size(); i++) {
                UiNode node = choices.get(i);
                if (!Intrinsics.areEqual(node, correct.getTarget()) && solver.isWrongChoice(prompt, node)) {
                    candidates.add(Integer.valueOf(i));
                }
            }
            int index = WrongAnswers.pickWrong(this.rng, candidates);
            if (index < 0) {
                return correct;
            }
            pick = choices.get(index);
            this.wrongPick.put(progress, pick.getText());
            QuietJson put = new QuietJson().put("progress", progress);
            log("deliberate_wrong", put);
        }
        return new ChoiceAnswer(pick, new ArrayList<Card>(), 0);
    }

    private final long runLimitMs() {
        return AdvancedSettings.runLimitSeconds(this.storage.getPrefs()) * 1000L;
    }
''')
sub('BotEngine.java', 'final ChoiceAnswer answer = solver.answer(prompt, choices);', 'final ChoiceAnswer answer = decideAnswer(solver, prompt, choices, progress);')
sub('BotEngine.java', '        this.human = this.storage.getPrefs().getBoolean("human_like", false);', '''        this.human = this.storage.getPrefs().getBoolean("human_like", false);
        this.wrongSet = null;
        this.wrongPick.clear();''')
sub('BotEngine.java', 'this.solver = new Solver(copy$default, this.partialChoices);', '''AdvancedSettings.applyMatching(this.storage.getPrefs());
        this.solver = new Solver(copy$default, this.partialChoices);''')
s = read('BotEngine.java')
assert s.count('new SwipeGate(650L, 2)') == 2
s = s.replace('new SwipeGate(650L, 2)', 'new SwipeGate(AdvancedSettings.gateMs(storage.getPrefs()), 2)', 1)
s = s.replace('new SwipeGate(650L, 2)', 'new SwipeGate(AdvancedSettings.gateMs(this.storage.getPrefs()), 2)', 1)
s = s.replace('if (i2 >= 160) {', 'if (i2 >= AdvancedSettings.scrollSteps(this.storage.getPrefs())) {', 1)
s = s.replace('if (pending.getTries() >= 2) {', 'if (pending.getTries() >= AdvancedSettings.tapRetries(this.storage.getPrefs())) {', 1)
s = s.replace('if (now - this.started >= 600000) {\n                throw new IllegalStateException("10분 실행 제한을 초과했습니다.".toString());', 'if (now - this.started >= runLimitMs()) {\n                throw new IllegalStateException(("실행 제한 시간(" + (runLimitMs() / 1000) + "초)을 초과했습니다.").toString());', 1)
s = s.replace('if (now - this.entered >= 35000) {', 'if (now - this.entered >= AdvancedSettings.phaseSeconds(this.storage.getPrefs()) * 1000L) {', 1)
s = s.replace('.put("ocr_only", this.ocrOnly);\n        this.active = false;', '.put("ocr_only", this.ocrOnly).put("deliberate_wrong", this.wrongPick.size());\n        this.active = false;', 1)
for needle in ['600000', '35000', 'i2 >= 160', 'getTries() >= 2', 'SwipeGate(650L']:
    assert needle not in s, needle
assert 'deliberate_wrong", this.wrongPick.size()' in s
write('BotEngine.java', s)

# ---------------- Screens: settings ----------------
s = read('Screens.java')
# slider for deliberate mistakes inside the input-method card
a = '        add(humanCard, s.caption(c, "보통 길이의 문제 기준 값이며, 글이 길수록 오래 걸리고 가끔 더 망설입니다."), 8);'
assert a in s
s = s.replace(a, a + '''
        add(humanCard, s.divider(c), 18);
        add(humanCard, s.sliderRow(c, "일부러 틀릴 문항 수", "개", AdvancedSettings.WRONG_MIN, AdvancedSettings.WRONG_MAX, AdvancedSettings.WRONG_STEP, AdvancedSettings.wrongCount(p), new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer v) {
                AdvancedSettings.setWrongCount(p, v.intValue());
                return Unit.INSTANCE;
            }
        }, false), 16);
        add(humanCard, s.caption(c, "한 번 실행에서 이 개수만큼의 문항을 무작위로 골라 정답이 아닌 보기를 누릅니다. 0이면 모두 맞힙니다. 문항 수보다 크면 전체가 오답이 됩니다."), 8);''', 1)
# advanced sections
a = '        add(content, s.sectionLabel(c, "안내 및 기록"), 24);'
assert a in s
s = s.replace(a, '''        add(content, s.sectionLabel(c, "고급 · 정답 매칭"), 24);
        add(content, advancedCard(c, p, 0), 8);
        add(content, s.sectionLabel(c, "고급 · 실행 한도"), 24);
        add(content, advancedCard(c, p, 1), 8);

''' + a, 1)
s = s.replace('    /** Appends the range / duration / interval sliders to {@code parent}. */', '''    /**
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
            card.addView(s.sliderRow(c, "단어 수집 스크롤 최대 횟수", "회", AdvancedSettings.SCROLL_MIN, AdvancedSettings.SCROLL_MAX, AdvancedSettings.SCROLL_STEP, AdvancedSettings.scrollSteps(p), new Function1<Integer, Unit>() {
                @Override
                public Unit invoke(Integer v) {
                    AdvancedSettings.setScrollSteps(p, v.intValue());
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
        Button reset = s.button(c, "기본값으로", AppStyle.Tone.TEXT, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                if (kind == 0) {
                    AdvancedSettings.resetMatching(p);
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

    /** Appends the range / duration / interval sliders to {@code parent}. */''', 1)
write('Screens.java', s)
print('ok')
