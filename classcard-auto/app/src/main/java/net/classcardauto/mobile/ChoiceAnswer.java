package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class ChoiceAnswer {
    private final List<Card> cards;
    private final int strength;
    private final UiNode target;

    public ChoiceAnswer(UiNode target, List<Card> cards, int i) {
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(cards, "cards");
        this.target = target;
        this.cards = cards;
        this.strength = i;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ ChoiceAnswer copy$default(ChoiceAnswer choiceAnswer, UiNode uiNode, List list, int i, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            uiNode = choiceAnswer.target;
        }
        if ((i2 & 2) != 0) {
            list = choiceAnswer.cards;
        }
        if ((i2 & 4) != 0) {
            i = choiceAnswer.strength;
        }
        return choiceAnswer.copy(uiNode, list, i);
    }


    public final List<Card> component2() {
        return this.cards;
    }


    public final ChoiceAnswer copy(UiNode target, List<Card> cards, int strength) {
        Intrinsics.checkNotNullParameter(target, "target");
        Intrinsics.checkNotNullParameter(cards, "cards");
        return new ChoiceAnswer(target, cards, strength);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChoiceAnswer)) {
            return false;
        }
        ChoiceAnswer choiceAnswer = (ChoiceAnswer) other;
        return Intrinsics.areEqual(this.target, choiceAnswer.target) && Intrinsics.areEqual(this.cards, choiceAnswer.cards) && this.strength == choiceAnswer.strength;
    }

    public final List<Card> getCards() {
        return this.cards;
    }

    public final int getStrength() {
        return this.strength;
    }

    public final UiNode getTarget() {
        return this.target;
    }

    public int hashCode() {
        return (((this.target.hashCode() * 31) + this.cards.hashCode()) * 31) + Integer.hashCode(this.strength);
    }

    public String toString() {
        return "ChoiceAnswer(target=" + this.target + ", cards=" + this.cards + ", strength=" + this.strength + ")";
    }
}
