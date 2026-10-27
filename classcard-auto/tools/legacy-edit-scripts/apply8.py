import re
exec(open('/tmp/claude-0/work/fix.py').read())

def sub(p, a, b, count=1):
    s = open(D + p).read()
    assert a in s, (p, a[:100])
    s = s.replace(a, b, count)
    open(D + p, 'w').write(s)

# ---- BotEngine ----
sub('BotEngine.java', '    private final long scrollInterval() {', '''    private final java.util.Random rng = new java.util.Random();
    private final java.util.Map<String, Long> humanDue = new java.util.HashMap<>();
    private boolean human;

    public final boolean isHuman() {
        return this.human;
    }

    public final java.util.Random getRng() {
        return this.rng;
    }

    /** True once the (randomised) thinking time for this question has passed; always true when the mode is off. */
    private final boolean humanReady(String progress, String prompt, List<UiNode> choices, long now) {
        if (!this.human) {
            return true;
        }
        String key = "think:" + progress;
        Long due = this.humanDue.get(key);
        if (due == null) {
            int choiceChars = 0;
            for (UiNode node : choices) {
                choiceChars += node.getText().length();
            }
            int mean = this.storage.getPrefs().getInt("human_mean_ms", HumanTiming.MEAN_DEFAULT);
            long wait = HumanTiming.thinkMillis(this.rng, mean, prompt.length(), choiceChars, this.answered.size());
            due = Long.valueOf(now + wait);
            this.humanDue.put(key, due);
            QuietJson put = new QuietJson().put("progress", progress).put("think_ms", wait);
            log("think", put);
        }
        return now >= due.longValue();
    }

    /** Same idea for the pause before swiping the card on. */
    private final boolean humanAdvanceReady(String key, long now) {
        if (!this.human) {
            return true;
        }
        Long due = this.humanDue.get(key);
        if (due == null) {
            int mean = this.storage.getPrefs().getInt("human_mean_ms", HumanTiming.MEAN_DEFAULT);
            due = Long.valueOf(now + HumanTiming.advanceMillis(this.rng, mean));
            this.humanDue.put(key, due);
        }
        return now >= due.longValue();
    }

    private final long scrollInterval() {''')
sub('BotEngine.java', '        this.natural = this.storage.getPrefs().getBoolean("natural", false);', '''        this.natural = this.storage.getPrefs().getBoolean("natural", false);
        this.human = this.storage.getPrefs().getBoolean("human_like", false);
        this.humanDue.clear();''')
sub('BotEngine.java', '.put("pace", this.natural ? "natural" : "fast")', '.put("pace", this.human ? "human" : (this.natural ? "natural" : "fast"))')
sub('BotEngine.java', '''                            inputAnswer(ns, sortedWith, str, str2, now, 1);
                            return;''', '''                            if (humanReady(str, str2, sortedWith, now)) {
                                inputAnswer(ns, sortedWith, str, str2, now, 1);
                            }
                            return;''')
sub('BotEngine.java', 'if (this.natural || !SwipeGate.ready$default(this.gate, str + ":prompt"', 'if (this.natural || !humanAdvanceReady(str + ":prompt", now) || !SwipeGate.ready$default(this.gate, str + ":prompt"')
sub('BotEngine.java', 'if (this.natural || !this.gate.ready(str + ":feedback"', 'if (this.natural || !humanAdvanceReady(str + ":feedback", now) || !this.gate.ready(str + ":feedback"')

# ---- BotService ----
replace(D + 'BotService.java', 'public final void tap(UiNode target, Function0<Unit> after)', '''    public final void tap(UiNode target, Function0<Unit> after) {
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(after, "after");
        if (!target.getBounds().getValid() || !target.getEnabled()) {
            throw new IllegalStateException("유효하지 않은 터치 위치입니다.".toString());
        }
        Bounds b = target.getBounds();
        if (getEngine().isHuman()) {
            java.util.Random rng = getEngine().getRng();
            float[] p = HumanTiming.tapPoint(rng, b.getLeft(), b.getTop(), b.getRight(), b.getBottom());
            gesture(p[0], p[1], p[0], p[1], HumanTiming.tapDurationMs(rng), after);
            return;
        }
        gesture(b.getX(), b.getY(), b.getX(), b.getY(), 45L, after);
    }''')
s = open(D + 'BotService.java').read()
# swipeCard: vary duration/path when the mode is on
a = '        gesture(safeSwipePath.getStartX(), safeSwipePath.getY(), safeSwipePath.getEndX(), safeSwipePath.getY(), 250L, new Function0() {'
assert a in s
s = s.replace(a, '''        float humanDy = 0.0f;
        float humanDx = 0.0f;
        long swipeMs = 250L;
        if (getEngine().isHuman()) {
            java.util.Random rng = getEngine().getRng();
            humanDy = HumanTiming.jitter(rng, 6.0f);
            humanDx = HumanTiming.jitter(rng, 10.0f);
            swipeMs = HumanTiming.swipeDurationMs(rng, 250L);
        }
        gesture(safeSwipePath.getStartX(), safeSwipePath.getY(), safeSwipePath.getEndX() + humanDx, safeSwipePath.getY() + humanDy, swipeMs, new Function0() {''', 1)
# scrollCollection: vary duration/horizontal drift
a2 = 'gesture(x, bounds.getTop() + (bounds.getHeight() * span[0]), x, bounds.getTop() + (bounds.getHeight() * span[1]), ScrollSettings.durationMs(prefs), after);'
assert a2 in s
s = s.replace(a2, '''long scrollMs = ScrollSettings.durationMs(prefs);
        float drift = 0.0f;
        if (getEngine().isHuman()) {
            java.util.Random rng = getEngine().getRng();
            scrollMs = HumanTiming.swipeDurationMs(rng, scrollMs);
            drift = HumanTiming.jitter(rng, 12.0f);
        }
        gesture(x, bounds.getTop() + (bounds.getHeight() * span[0]), x + drift, bounds.getTop() + (bounds.getHeight() * span[1]), scrollMs, after);''', 1)
open(D + 'BotService.java', 'w').write(s)

# ---- Settings screen ----
sub('Screens.java', '''        add(content, s.sectionLabel(c, "안내 및 기록"), 24);''', '''        add(content, s.sectionLabel(c, "입력 방식"), 24);
        LinearLayout humanCard = s.card(c);
        humanCard.addView(s.switchRow(c, "사람처럼 입력", "문제를 읽는 시간, 터치 위치와 길이, 카드를 넘기는 간격을 사람처럼 무작위로 흔듭니다. 켜면 실행 기록의 pace가 human으로 남습니다. 다음 실행부터 적용됩니다.", p.getBoolean("human_like", false), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                p.edit().putBoolean("human_like", checked).apply();
            }
        }));
        add(humanCard, s.sliderRow(c, "평균 응답 시간", "ms", HumanTiming.MEAN_MIN, HumanTiming.MEAN_MAX, HumanTiming.MEAN_STEP, p.getInt("human_mean_ms", HumanTiming.MEAN_DEFAULT), new Function1<Integer, Unit>() {
            @Override
            public Unit invoke(Integer v) {
                p.edit().putInt("human_mean_ms", v.intValue()).apply();
                return Unit.INSTANCE;
            }
        }, false), 16);
        add(humanCard, s.caption(c, "보통 길이의 문제 기준 값이며, 글이 길수록 오래 걸리고 가끔 더 망설입니다."), 8);
        add(content, humanCard, 8);

        add(content, s.sectionLabel(c, "안내 및 기록"), 24);''')
print('ok')
