package net.classcardauto.mobile;

import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class Vocabulary {
    private final List<Card> cards;
    private final boolean complete;
    private final Integer expectedCount;
    private final String name;
    private final String source;

    public Vocabulary(List<Card> cards, String name, String source, boolean z, Integer num) {
        Intrinsics.checkNotNullParameter(cards, "cards");
        Intrinsics.checkNotNullParameter(name, "name");
        Intrinsics.checkNotNullParameter(source, "source");
        this.cards = cards;
        this.name = name;
        this.source = source;
        this.complete = z;
        this.expectedCount = num;
    }

    public /* synthetic */ Vocabulary(List list, String str, String str2, boolean z, Integer num, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(list, (i & 2) != 0 ? "" : str, (i & 4) != 0 ? "user" : str2, (i & 8) != 0 ? false : z, (i & 16) != 0 ? null : num);
    }

    public static /* synthetic */ Vocabulary copy$default(Vocabulary vocabulary, List list, String str, String str2, boolean z, Integer num, int i, Object obj) {
        if ((i & 1) != 0) {
            list = vocabulary.cards;
        }
        if ((i & 2) != 0) {
            str = vocabulary.name;
        }
        if ((i & 4) != 0) {
            str2 = vocabulary.source;
        }
        if ((i & 8) != 0) {
            z = vocabulary.complete;
        }
        if ((i & 16) != 0) {
            num = vocabulary.expectedCount;
        }
        Integer num2 = num;
        String str3 = str2;
        return vocabulary.copy(list, str, str3, z, num2);
    }

    public final List<Card> component1() {
        return this.cards;
    }





    public final Vocabulary copy(List<Card> cards, String name, String source, boolean complete, Integer expectedCount) {
        Intrinsics.checkNotNullParameter(cards, "cards");
        Intrinsics.checkNotNullParameter(name, "name");
        Intrinsics.checkNotNullParameter(source, "source");
        return new Vocabulary(cards, name, source, complete, expectedCount);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vocabulary)) {
            return false;
        }
        Vocabulary vocabulary = (Vocabulary) other;
        return Intrinsics.areEqual(this.cards, vocabulary.cards) && Intrinsics.areEqual(this.name, vocabulary.name) && Intrinsics.areEqual(this.source, vocabulary.source) && this.complete == vocabulary.complete && Intrinsics.areEqual(this.expectedCount, vocabulary.expectedCount);
    }

    public final List<Card> getCards() {
        return this.cards;
    }

    public final boolean getComplete() {
        return this.complete;
    }

    public final Integer getExpectedCount() {
        return this.expectedCount;
    }

    public final String getName() {
        return this.name;
    }

    public final String getSource() {
        return this.source;
    }

    public int hashCode() {
        int hashCode = ((((((this.cards.hashCode() * 31) + this.name.hashCode()) * 31) + this.source.hashCode()) * 31) + Boolean.hashCode(this.complete)) * 31;
        Integer num = this.expectedCount;
        return hashCode + (num == null ? 0 : num.hashCode());
    }

    public String toString() {
        return "Vocabulary(cards=" + this.cards + ", name=" + this.name + ", source=" + this.source + ", complete=" + this.complete + ", expectedCount=" + this.expectedCount + ")";
    }
}
