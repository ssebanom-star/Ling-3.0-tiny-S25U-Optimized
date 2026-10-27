package net.classcardauto.mobile;

import java.util.Collection;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;

/* compiled from: Core.kt */
/* loaded from: classes.dex */
public final /* data */ class Card {
    private final List<String> aliases;
    private final String meaning;
    private final String word;
    private final List<String> wordAliases;

    public Card(String word, String meaning, List<String> aliases, List<String> wordAliases) {
        Intrinsics.checkNotNullParameter(word, "word");
        Intrinsics.checkNotNullParameter(meaning, "meaning");
        Intrinsics.checkNotNullParameter(aliases, "aliases");
        Intrinsics.checkNotNullParameter(wordAliases, "wordAliases");
        this.word = word;
        this.meaning = meaning;
        this.aliases = aliases;
        this.wordAliases = wordAliases;
        if (StringsKt.isBlank(word) || StringsKt.isBlank(meaning)) {
            throw new IllegalArgumentException("단어와 뜻은 모두 필수입니다.".toString());
        }
    }

    public /* synthetic */ Card(String str, String str2, List list, List list2, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this(str, str2, (i & 4) != 0 ? CollectionsKt.emptyList() : list, (i & 8) != 0 ? CollectionsKt.emptyList() : list2);
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ Card copy$default(Card card, String str, String str2, List list, List list2, int i, Object obj) {
        if ((i & 1) != 0) {
            str = card.word;
        }
        if ((i & 2) != 0) {
            str2 = card.meaning;
        }
        if ((i & 4) != 0) {
            list = card.aliases;
        }
        if ((i & 8) != 0) {
            list2 = card.wordAliases;
        }
        return card.copy(str, str2, list, list2);
    }



    public final List<String> component3() {
        return this.aliases;
    }

    public final List<String> component4() {
        return this.wordAliases;
    }

    public final Card copy(String word, String meaning, List<String> aliases, List<String> wordAliases) {
        Intrinsics.checkNotNullParameter(word, "word");
        Intrinsics.checkNotNullParameter(meaning, "meaning");
        Intrinsics.checkNotNullParameter(aliases, "aliases");
        Intrinsics.checkNotNullParameter(wordAliases, "wordAliases");
        return new Card(word, meaning, aliases, wordAliases);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Card)) {
            return false;
        }
        Card card = (Card) other;
        return Intrinsics.areEqual(this.word, card.word) && Intrinsics.areEqual(this.meaning, card.meaning) && Intrinsics.areEqual(this.aliases, card.aliases) && Intrinsics.areEqual(this.wordAliases, card.wordAliases);
    }

    public final List<String> getAliases() {
        return this.aliases;
    }

    public final String getMeaning() {
        return this.meaning;
    }

    public final String getWord() {
        return this.word;
    }

    public final List<String> getWordAliases() {
        return this.wordAliases;
    }

    public final List<String> getWords() {
        return CollectionsKt.plus((Collection) CollectionsKt.listOf(this.word), (Iterable) this.wordAliases);
    }

    public int hashCode() {
        return (((((this.word.hashCode() * 31) + this.meaning.hashCode()) * 31) + this.aliases.hashCode()) * 31) + this.wordAliases.hashCode();
    }

    public String toString() {
        return "Card(word=" + this.word + ", meaning=" + this.meaning + ", aliases=" + this.aliases + ", wordAliases=" + this.wordAliases + ")";
    }
}
