import re
exec(open('/tmp/claude-0/work/fix.py').read())

def sub(p, a, b, count=1):
    s = open(D + p).read()
    assert a in s, (p, a[:100])
    s = s.replace(a, b, count)
    open(D + p, 'w').write(s)

# --- Solver: closest collected words, for PROMPT_NOT_FOUND diagnosis ---
sub('Solver.java', '    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {', '''    /** The collected cards whose word or meaning looks most like {@code prompt}, as "word → meaning (87%)". */
    public final List<String> nearest(String prompt, int limit) {
        final List<Card> cards = this.vocabulary.getCards();
        final double[] scores = new double[cards.size()];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            List<String> meanings = new ArrayList<>();
            meanings.add(card.getMeaning());
            meanings.addAll(card.getAliases());
            scores[i] = Math.max(Matcher.bestSim(prompt, card.getWords()), Matcher.bestSim(prompt, meanings));
            order.add(Integer.valueOf(i));
        }
        java.util.Collections.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(scores[b.intValue()], scores[a.intValue()]);
            }
        });
        List<String> out = new ArrayList<>();
        for (Integer index : order) {
            if (out.size() >= limit || scores[index.intValue()] < 0.4) {
                break;
            }
            Card card = cards.get(index.intValue());
            out.add(card.getWord() + " → " + card.getMeaning() + " (" + Math.round(scores[index.intValue()] * 100) + "%)");
        }
        return out;
    }

    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {''')

# --- BotEngine: remember the failing question, write a record when the run fails ---
sub('BotEngine.java', '    private boolean human;\n', '''    private boolean human;
    private FailureLog.Record failureDetail;

    /** Solver.answer, remembering the question's text when it cannot be answered (kept only if detailed records are on). */
    private final ChoiceAnswer answerOrRecord(Solver solver, String prompt, List<UiNode> choices) {
        try {
            return solver.answer(prompt, choices);
        } catch (AnswerResolutionException e) {
            if (this.storage.getPrefs().getBoolean("failure_detail", false)) {
                FailureLog.Record detail = new FailureLog.Record();
                detail.prompt = prompt;
                for (UiNode node : choices) {
                    detail.choices.add(node.getText());
                }
                detail.nearest = solver.nearest(prompt, 3);
                this.failureDetail = detail;
            }
            throw e;
        }
    }

    private final void writeFailureRecord(String reason, AnswerFailure failure) {
        FailureLog.Record record = new FailureLog.Record();
        record.time = System.currentTimeMillis();
        record.code = failure != null ? failure.name() : "UNCLASSIFIED";
        record.message = reason;
        record.progress = this.lastProgress;
        record.phase = this.phase;
        record.pace = this.human ? "human" : (this.natural ? "natural" : "fast");
        record.cards = this.vocabulary != null ? this.vocabulary.getCards().size() : this.collected.size();
        FailureLog.Record detail = this.failureDetail;
        if (detail != null && this.storage.getPrefs().getBoolean("failure_detail", false)) {
            record.prompt = detail.prompt;
            record.choices = detail.choices;
            record.nearest = detail.nearest;
        }
        this.failureDetail = null;
        FailureLog.append(this.storage.getRoot(), record);
    }
''')
sub('BotEngine.java', '''        Intrinsics.checkNotNullParameter(reason, "reason");
        if (this.active) {
            QuietJson put = new QuietJson().put("message", reason).put("phase", this.phase);''', '''        Intrinsics.checkNotNullParameter(reason, "reason");
        if (this.active) {
            writeFailureRecord(reason, failure);
            QuietJson put = new QuietJson().put("message", reason).put("phase", this.phase);''')
sub('BotEngine.java', 'final ChoiceAnswer answer = solver.answer(prompt, choices);', 'final ChoiceAnswer answer = answerOrRecord(solver, prompt, choices);')
sub('BotEngine.java', '        this.human = this.storage.getPrefs().getBoolean("human_like", false);', '''        this.human = this.storage.getPrefs().getBoolean("human_like", false);
        this.failureDetail = null;''')
print('ok')
