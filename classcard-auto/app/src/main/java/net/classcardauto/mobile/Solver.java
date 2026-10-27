package net.classcardauto.mobile;

import java.util.ArrayList;
import java.util.List;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/**
 * Picks the on-screen choice that corresponds to the prompt, using the collected vocabulary.
 * The matching rules live in {@link Matcher}; this class only adapts app types to it.
 * (The original decompiled implementation is kept in reference/ for comparison.)
 */
public final class Solver {
    private final boolean allowPartialChoices;
    private final Vocabulary vocabulary;

    public Solver(Vocabulary vocabulary, boolean z) {
        Intrinsics.checkNotNullParameter(vocabulary, "vocabulary");
        this.vocabulary = vocabulary;
        this.allowPartialChoices = z;
    }

    public /* synthetic */ Solver(Vocabulary vocabulary, boolean z, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(vocabulary, (i & 2) != 0 ? false : z);
    }

    /** True when {@code choice} does not match the prompt's meaning at all, so it is a safe wrong answer. */
    public final boolean isWrongChoice(String prompt, UiNode choice) {
        try {
            answer(prompt, java.util.Collections.singletonList(choice));
            return false;
        } catch (AnswerResolutionException e) {
            AnswerFailure f = e.getFailure();
            return f == AnswerFailure.CHOICE_NOT_FOUND || f == AnswerFailure.PARTIAL_TOO_SHORT || f == AnswerFailure.PARTIAL_TIE;
        }
    }

    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {
        Intrinsics.checkNotNullParameter(prompt, "prompt");
        Intrinsics.checkNotNullParameter(choices, "choices");
        List<Card> cards = this.vocabulary.getCards();
        List<Matcher.CardData> data = new ArrayList<>(cards.size());
        for (Card card : cards) {
            List<String> meanings = new ArrayList<>();
            meanings.add(card.getMeaning());
            meanings.addAll(card.getAliases());
            data.add(new Matcher.CardData(card.getWords(), meanings));
        }
        List<String> texts = new ArrayList<>(choices.size());
        for (UiNode node : choices) {
            texts.add(node.getText());
        }
        try {
            Matcher.Result result = Matcher.answer(prompt, texts, data, this.allowPartialChoices);
            List<Card> matched = new ArrayList<>();
            for (Integer index : result.cards) {
                matched.add(cards.get(index.intValue()));
            }
            return new ChoiceAnswer(choices.get(result.index), matched, result.strength);
        } catch (Matcher.ResolutionException e) {
            throw new AnswerResolutionException(AnswerFailure.valueOf(e.failure.name()));
        }
    }
}
