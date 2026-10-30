package net.classcardauto.mobile;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.Pair;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.SetsKt;
import kotlin.comparisons.ComparisonsKt;
import kotlin.io.FilesKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.Regex;
import kotlin.text.StringsKt;
import net.classcardauto.mobile.OcrReader;
import org.json.JSONArray;
import org.json.JSONObject;

/* compiled from: BotEngine.kt */
/* loaded from: /tmp/claude-0/work/out/x/classes.dex */
public final class BotEngine {
    private final Map<String, Integer> actionAttempts;
    private boolean active;
    private final Set<String> answered;
    private String code;
    private String collectReturnPhase;
    private final LinkedHashMap<Pair<String, String>, Card> collected;
    private final Set<String> collectedRows;
    private String collectingIdentity;
    private UiNode collectingObserved;
    private UiNode collectingWord;
    private final CollectionCursor collectionCursor;
    private final CollectionDisplayMode collectionDisplay;
    private int collectionPage;
    private long collectionReadAt;
    private boolean collectionReadPending;
    private int collectionReusedRows;
    private String collectionScrollSignature;
    private final StableCollectionRead collectionStability;
    private final Map<String, String> contexts;
    private int desiredDay;
    private final Map<String, Integer> discardedReads;
    private long entered;
    private int epoch;
    private File events;
    private int expectedQuestions;
    private long firstPrompt;
    private SwipeGate gate;
    private boolean imageCollection;
    private int inlinePairCount;
    private final StableInlineRead inlineStability;
    private final Map<String, Long> lastActions;
    private String lastProgress;
    private long lastTap;
    private final Set<String> loggedPrompts;
    private String mode;
    private String name;
    private boolean natural;
    private final Set<String> observedCollectionWords;
    private int ocrCaptures;
    private boolean ocrOnly;
    private int ocrReads;
    private boolean partialChoices;
    private Pending pending;
    private String phase;
    private String previousSignature;
    private boolean replace;
    private long resultSince;
    private File run;
    private UiNode sample;
    private int scrolls;
    private boolean searchReset;
    private final BotService service;
    private Solver solver;
    private long started;
    private final Storage storage;
    private final List<Double> tapTimes;
    private long testStarted;
    private String titleFilter;
    private int unchanged;
    private Vocabulary vocabulary;

    /* JADX INFO: Access modifiers changed from: private */
    /* compiled from: BotEngine.kt */
    @Metadata(d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\b\n\u0002\b\u0015\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0082\b\u0018\u00002\u00020\u0001B7\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0006\u0012\u0006\u0010\u0007\u001a\u00020\b\u0012\u0006\u0010\t\u001a\u00020\n\u0012\u0006\u0010\u000b\u001a\u00020\f¢\u0006\u0004\b\r\u0010\u000eJ\t\u0010\u001a\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001b\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001c\u001a\u00020\u0006HÆ\u0003J\t\u0010\u001d\u001a\u00020\bHÆ\u0003J\t\u0010\u001e\u001a\u00020\nHÆ\u0003J\t\u0010\u001f\u001a\u00020\fHÆ\u0003JE\u0010 \u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00062\b\b\u0002\u0010\u0007\u001a\u00020\b2\b\b\u0002\u0010\t\u001a\u00020\n2\b\b\u0002\u0010\u000b\u001a\u00020\fHÆ\u0001J\u0013\u0010!\u001a\u00020\"2\b\u0010#\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010$\u001a\u00020\fHÖ\u0001J\t\u0010%\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0010R\u0011\u0010\u0005\u001a\u00020\u0006¢\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u0011\u0010\u0007\u001a\u00020\b¢\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u0011\u0010\t\u001a\u00020\n¢\u0006\b\n\u0000\u001a\u0004\b\u0016\u0010\u0017R\u0011\u0010\u000b\u001a\u00020\f¢\u0006\b\n\u0000\u001a\u0004\b\u0018\u0010\u0019¨\u0006&"}, d2 = {"Lnet/classcardauto/mobile/BotEngine$Pending;", "", "progress", "", "prompt", "answer", "Lnet/classcardauto/mobile/ChoiceAnswer;", "tapped", "", "readMs", "", "tries", "", "<init>", "(Ljava/lang/String;Ljava/lang/String;Lnet/classcardauto/mobile/ChoiceAnswer;JDI)V", "getProgress", "()Ljava/lang/String;", "getPrompt", "getAnswer", "()Lnet/classcardauto/mobile/ChoiceAnswer;", "getTapped", "()J", "getReadMs", "()D", "getTries", "()I", "component1", "component2", "component3", "component4", "component5", "component6", "copy", "equals", "", "other", "hashCode", "toString", "app_release"}, k = 1, mv = {2, 1, 0}, xi = 48)
    /* loaded from: /tmp/claude-0/work/out/x/classes.dex */
    public static final /* data */ class Pending {
        private final ChoiceAnswer answer;
        private final String progress;
        private final String prompt;
        private final double readMs;
        private final long tapped;
        private final int tries;

        public Pending(String progress, String prompt, ChoiceAnswer answer, long j, double d, int i) {
            Intrinsics.checkNotNullParameter(progress, "progress");
            Intrinsics.checkNotNullParameter(prompt, "prompt");
            Intrinsics.checkNotNullParameter(answer, "answer");
            this.progress = progress;
            this.prompt = prompt;
            this.answer = answer;
            this.tapped = j;
            this.readMs = d;
            this.tries = i;
        }

        public static /* synthetic */ Pending copy$default(Pending pending, String str, String str2, ChoiceAnswer choiceAnswer, long j, double d, int i, int i2, Object obj) {
            if ((i2 & 1) != 0) {
                str = pending.progress;
            }
            if ((i2 & 2) != 0) {
                str2 = pending.prompt;
            }
            if ((i2 & 4) != 0) {
                choiceAnswer = pending.answer;
            }
            if ((i2 & 8) != 0) {
                j = pending.tapped;
            }
            if ((i2 & 16) != 0) {
                d = pending.readMs;
            }
            if ((i2 & 32) != 0) {
                i = pending.tries;
            }
            int i3 = i;
            double d2 = d;
            ChoiceAnswer choiceAnswer2 = choiceAnswer;
            return pending.copy(str, str2, choiceAnswer2, j, d2, i3);
        }







        public final Pending copy(String progress, String prompt, ChoiceAnswer answer, long tapped, double readMs, int tries) {
            Intrinsics.checkNotNullParameter(progress, "progress");
            Intrinsics.checkNotNullParameter(prompt, "prompt");
            Intrinsics.checkNotNullParameter(answer, "answer");
            return new Pending(progress, prompt, answer, tapped, readMs, tries);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Pending)) {
                return false;
            }
            Pending pending = (Pending) other;
            return Intrinsics.areEqual(this.progress, pending.progress) && Intrinsics.areEqual(this.prompt, pending.prompt) && Intrinsics.areEqual(this.answer, pending.answer) && this.tapped == pending.tapped && Double.compare(this.readMs, pending.readMs) == 0 && this.tries == pending.tries;
        }

        public final ChoiceAnswer getAnswer() {
            return this.answer;
        }

        public final String getProgress() {
            return this.progress;
        }

        public final String getPrompt() {
            return this.prompt;
        }

        public final double getReadMs() {
            return this.readMs;
        }

        public final long getTapped() {
            return this.tapped;
        }

        public final int getTries() {
            return this.tries;
        }

        public int hashCode() {
            return (((((((((this.progress.hashCode() * 31) + this.prompt.hashCode()) * 31) + this.answer.hashCode()) * 31) + Long.hashCode(this.tapped)) * 31) + Double.hashCode(this.readMs)) * 31) + Integer.hashCode(this.tries);
        }

        public String toString() {
            return "Pending(progress=" + this.progress + ", prompt=" + this.prompt + ", answer=" + this.answer + ", tapped=" + this.tapped + ", readMs=" + this.readMs + ", tries=" + this.tries + ")";
        }
    }

    /* compiled from: BotEngine.kt */
    @Metadata(k = 3, mv = {2, 1, 0}, xi = 48)
    /* loaded from: /tmp/claude-0/work/out/x/classes.dex */
    public /* synthetic */ class WhenMappings {
        public static final /* synthetic */ int[] $EnumSwitchMapping$0;

        static {
            int[] iArr = new int[CurrentEntry.values().length];
            try {
                iArr[CurrentEntry.ACTIVE_TEST.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[CurrentEntry.PREVIEW.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                iArr[CurrentEntry.SET.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                iArr[CurrentEntry.UNKNOWN.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            $EnumSwitchMapping$0 = iArr;
        }
    }

    public BotEngine(BotService service, Storage storage) {
        Intrinsics.checkNotNullParameter(service, "service");
        Intrinsics.checkNotNullParameter(storage, "storage");
        this.service = service;
        this.storage = storage;
        this.mode = "";
        this.phase = "idle";
        this.name = "";
        this.code = "";
        this.titleFilter = "";
        this.gate = new SwipeGate(AdvancedSettings.gateMs(storage.getPrefs()), 2);
        this.contexts = new LinkedHashMap();
        this.answered = new LinkedHashSet();
        this.loggedPrompts = new LinkedHashSet();
        this.lastActions = new LinkedHashMap();
        this.actionAttempts = new LinkedHashMap();
        this.tapTimes = new ArrayList();
        this.lastProgress = "";
        this.previousSignature = "";
        this.collected = new LinkedHashMap<>();
        this.collectedRows = new LinkedHashSet();
        this.collectionCursor = new CollectionCursor();
        this.collectReturnPhase = "finish_collect";
        this.collectingIdentity = "";
        this.observedCollectionWords = new LinkedHashSet();
        this.collectionStability = new StableCollectionRead();
        this.discardedReads = new LinkedHashMap();
        this.collectionDisplay = new CollectionDisplayMode();
        this.inlineStability = new StableInlineRead();
    }

    private final void act(String key, long now, Function0<Unit> action) {
        String str = this.phase + ":" + key;
        if (this.human && (key.equals("open") || key.equals("start")) && !humanNavReady("nav:" + str, now)) {
            return;
        }
        Long l = this.lastActions.get(str);
        if (l == null || now - l.longValue() >= 800) {
            Integer num = this.actionAttempts.get(str);
            int intValue = num != null ? num.intValue() : 0;
            if (intValue >= 8) {
                throw new IllegalStateException(("화면 조작 재시도 한도에 도달했습니다: " + key).toString());
            }
            this.lastActions.put(str, Long.valueOf(now));
            this.actionAttempts.put(str, Integer.valueOf(intValue + 1));
            action.invoke();
        }
    }

    private final void beginCollection(String returnPhase) {
        this.collectReturnPhase = returnPhase;
        this.collected.clear();
        this.collectedRows.clear();
        this.previousSignature = "";
        this.unchanged = 0;
        this.scrolls = 0;
        this.collectionPage = 0;
        this.collectionScrollSignature = null;
        this.collectionCursor.reset();
        this.collectionReusedRows = 0;
        this.observedCollectionWords.clear();
        this.inlineStability.reset();
        transition("collect_top", "앱 카드 수집 · 목록 처음으로");
    }

    private final Bounds cardBounds(List<UiNode> ns) {
        Object next;
        Bounds bounds;
        Bounds bounds2;
        List<UiNode> list = ns;
        ArrayList arrayList = new ArrayList();
        for (Object obj : list) {
            if (Intrinsics.areEqual(((UiNode) obj).getId(), "card_root")) {
                arrayList.add(obj);
            }
        }
        Iterator it = arrayList.iterator();
        Object obj2 = null;
        if (it.hasNext()) {
            next = it.next();
            if (it.hasNext()) {
                UiNode uiNode = (UiNode) next;
                int width = uiNode.getBounds().getWidth() * uiNode.getBounds().getHeight();
                do {
                    Object next2 = it.next();
                    UiNode uiNode2 = (UiNode) next2;
                    int width2 = uiNode2.getBounds().getWidth() * uiNode2.getBounds().getHeight();
                    if (width < width2) {
                        next = next2;
                        width = width2;
                    }
                } while (it.hasNext());
            }
        } else {
            next = null;
        }
        UiNode uiNode3 = (UiNode) next;
        if (uiNode3 != null) {
            return uiNode3.getBounds();
        }
        UiNode id = id(ns, "pager");
        if (id == null || (bounds = id.getBounds()) == null) {
            throw new IllegalStateException("카드 페이지를 확인할 수 없습니다.".toString());
        }
        ArrayList arrayList2 = new ArrayList();
        for (Object obj3 : list) {
            UiNode uiNode4 = (UiNode) obj3;
            if (uiNode4.getBounds().getLeft() > bounds.getLeft() && uiNode4.getBounds().getRight() < bounds.getRight() && uiNode4.getBounds().getTop() >= bounds.getTop() && uiNode4.getBounds().getBottom() <= bounds.getBottom()) {
                arrayList2.add(obj3);
            }
        }
        Iterator it2 = arrayList2.iterator();
        if (it2.hasNext()) {
            obj2 = it2.next();
            if (it2.hasNext()) {
                UiNode uiNode5 = (UiNode) obj2;
                int width3 = uiNode5.getBounds().getWidth() * uiNode5.getBounds().getHeight();
                do {
                    Object next3 = it2.next();
                    UiNode uiNode6 = (UiNode) next3;
                    int width4 = uiNode6.getBounds().getWidth() * uiNode6.getBounds().getHeight();
                    if (width3 < width4) {
                        obj2 = next3;
                        width3 = width4;
                    }
                } while (it2.hasNext());
            }
        }
        UiNode uiNode7 = (UiNode) obj2;
        if (uiNode7 == null || (bounds2 = uiNode7.getBounds()) == null) {
            throw new IllegalStateException("페이지 안의 카드를 확인할 수 없습니다.".toString());
        }
        return bounds2;
    }

    private final boolean checkCollectionScreen(List<UiNode> ns) {
        UiNode id = id(ns, "set_name");
        if (!Intrinsics.areEqual(id != null ? id.getText() : null, this.name)) {
            throw new IllegalStateException("수집 중 현재 세트가 바뀌었습니다.".toString());
        }
        UiNode id2 = id(ns, "switch_show_back");
        this.collectionDisplay.observe(ns);
        if (id2 == null || id2.getChecked() == getInlineCollection()) {
            return true;
        }
        this.service.tap(id2, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda15
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit checkCollectionScreen$lambda$53;
                checkCollectionScreen$lambda$53 = BotEngine.checkCollectionScreen$lambda$53(BotEngine.this);
                return checkCollectionScreen$lambda$53;
            }
        });
        return false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit checkCollectionScreen$lambda$53(BotEngine botEngine) {
        botEngine.inlineStability.reset();
        botEngine.collectionStability.reset();
        botEngine.service.hold(300L);
        return Unit.INSTANCE;
    }

    private final void collectBack(List<UiNode> ns, long now) {
        UiNode id = id(ns, "set_name");
        Object obj = null;
        if (!Intrinsics.areEqual(id != null ? id.getText() : null, this.name)) {
            throw new IllegalStateException("뜻 수집 중 세트가 바뀌었습니다.".toString());
        }
        if (this.collectionReadPending || now - this.collectionReadAt < 400) {
            return;
        }
        final UiNode uiNode = this.collectingWord;
        if (uiNode == null) {
            throw new IllegalStateException("수집할 단어를 확인할 수 없습니다.".toString());
        }
        Iterator<?> it = ns.iterator();
        while (true) {
            if (!it.hasNext()) {
                break;
            }
            Object next = it.next();
            UiNode uiNode2 = (UiNode) next;
            if (Intrinsics.areEqual(uiNode2.getId(), "txt_back")) {
                int top = uiNode2.getBounds().getTop();
                int bottom = uiNode2.getBounds().getBottom();
                int y = uiNode.getBounds().getY();
                if (top <= y && y <= bottom) {
                    obj = next;
                    break;
                }
            }
        }
        UiNode uiNode3 = (UiNode) obj;
        if (uiNode3 == null) {
            return;
        }
        readCollectionNode(uiNode3, "back", new Function1() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda21
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj2) {
                Unit collectBack$lambda$85;
                collectBack$lambda$85 = BotEngine.collectBack$lambda$85(BotEngine.this, uiNode, (UiNode) obj2);
                return collectBack$lambda$85;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectBack$lambda$85(BotEngine botEngine, UiNode uiNode, UiNode readBack) {
        Intrinsics.checkNotNullParameter(readBack, "readBack");
        botEngine.saveCollectedBack(uiNode, readBack, SystemClock.elapsedRealtime());
        return Unit.INSTANCE;
    }

    private final void collectFront(List<UiNode> ns, long now) {
        Bounds bounds;
        Object obj;
        Object obj2;
        UiNode uiNode;
        boolean z;
        Object obj3;
        boolean z2;
        if (checkCollectionScreen(ns) && !this.collectionReadPending && now - this.collectionReadAt >= 400) {
            UiNode id = id(ns, "recycler_view");
            if (id == null || (bounds = id.getBounds()) == null) {
                throw new IllegalStateException("단어 목록이 보이지 않습니다.".toString());
            }
            List<UiNode> list = ns;
            ArrayList arrayList = new ArrayList();
            for (Object obj4 : list) {
                if (Intrinsics.areEqual(((UiNode) obj4).getId(), "txt_front")) {
                    arrayList.add(obj4);
                }
            }
            List<UiNode> sortedWith = CollectionsKt.sortedWith(arrayList, new Comparator() { // from class: net.classcardauto.mobile.BotEngine$collectFront$$inlined$sortedBy$1
                /* JADX WARN: Multi-variable type inference failed */
                @Override // java.util.Comparator
                public final int compare(Object t, Object t2) {
                    return ComparisonsKt.compareValues(Integer.valueOf(((UiNode) t).getBounds().getTop()), Integer.valueOf(((UiNode) t2).getBounds().getTop()));
                }
            });
            CollectionCursor collectionCursor = this.collectionCursor;
            String str = this.collectionScrollSignature;
            collectionCursor.observe(sortedWith, str != null && (Intrinsics.areEqual(signature(ns), str) ^ true));
            Set<String> set = this.observedCollectionWords;
            List<UiNode> list2 = sortedWith;
            ArrayList arrayList2 = new ArrayList();
            for (Object obj5 : list2) {
                if (!StringsKt.isBlank(((UiNode) obj5).getText())) {
                    arrayList2.add(obj5);
                }
            }
            ArrayList arrayList3 = arrayList2;
            ArrayList arrayList4 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList3, 10));
            Iterator it = arrayList3.iterator();
            while (it.hasNext()) {
                arrayList4.add(((UiNode) it.next()).getText());
            }
            set.addAll(arrayList4);
            String str2 = this.collectionScrollSignature;
            if (str2 != null) {
                if (Intrinsics.areEqual(signature(ns), str2)) {
                    int i = this.unchanged;
                    this.unchanged = i + 1;
                    Integer.valueOf(i);
                } else {
                    this.collectionPage++;
                    this.unchanged = 0;
                    Unit unit = Unit.INSTANCE;
                }
                this.collectionScrollSignature = null;
                QuietJson put = new QuietJson().put("page", this.collectionPage).put("unchanged", this.unchanged);
                ArrayList arrayList5 = new ArrayList(CollectionsKt.collectionSizeOrDefault(list2, 10));
                Iterator<?> it2 = list2.iterator();
                while (it2.hasNext()) {
                    arrayList5.add(((UiNode) it2.next()).getText());
                }
                QuietJson put2 = put.put("fronts", new JSONArray((Collection) arrayList5));
                ArrayList arrayList6 = new ArrayList(CollectionsKt.collectionSizeOrDefault(list2, 10));
                Iterator<?> it3 = list2.iterator();
                while (it3.hasNext()) {
                    Integer collectionIndex = ((UiNode) it3.next()).getCollectionIndex();
                    arrayList6.add(Integer.valueOf(collectionIndex != null ? collectionIndex.intValue() : -1));
                }
                QuietJson put3 = put2.put("indices", new JSONArray((Collection) arrayList6));
                Intrinsics.checkNotNullExpressionValue(put3, "put(...)");
                log("collection_page", put3);
                ArrayList arrayList7 = new ArrayList();
                for (Object obj6 : list2) {
                    if (this.collectionCursor.contains((UiNode) obj6)) {
                        arrayList7.add(obj6);
                    }
                }
                ArrayList arrayList8 = arrayList7;
                this.collectionReusedRows += arrayList8.size();
                QuietJson put4 = new QuietJson().put("indexed", this.collectionCursor.getIndexed()).put("strategy", this.collectionCursor.getStrategy());
                Pair<Integer, String> last = this.collectionCursor.getLast();
                QuietJson put5 = put4.put("last_index", last != null ? last.getFirst() : null);
                Pair<Integer, String> last2 = this.collectionCursor.getLast();
                QuietJson put6 = put5.put("last_word", last2 != null ? last2.getSecond() : null);
                if (!(list2 instanceof Collection) || !list2.isEmpty()) {
                    Iterator<?> it4 = list2.iterator();
                    while (it4.hasNext()) {
                        if (this.collectionCursor.isAnchor((UiNode) it4.next())) {
                            z2 = true;
                            break;
                        }
                    }
                }
                z2 = false;
                QuietJson put7 = put6.put("anchor_visible", z2).put("skipped_completed", arrayList8.size());
                Intrinsics.checkNotNullExpressionValue(put7, "put(...)");
                log("collection_resume", put7);
                Unit unit2 = Unit.INSTANCE;
                Unit unit3 = Unit.INSTANCE;
            }
            Iterator<?> it5 = list.iterator();
            while (true) {
                if (!it5.hasNext()) {
                    obj = null;
                    break;
                }
                obj = it5.next();
                UiNode uiNode2 = (UiNode) obj;
                if (Intrinsics.areEqual(uiNode2.getId(), "titleopenstatus") && !uiNode2.getSelected()) {
                    break;
                }
            }
            UiNode uiNode3 = (UiNode) obj;
            if (uiNode3 != null) {
                Iterator<?> it6 = list.iterator();
                while (true) {
                    if (!it6.hasNext()) {
                        obj3 = null;
                        break;
                    }
                    obj3 = it6.next();
                    UiNode uiNode4 = (UiNode) obj3;
                    if (Intrinsics.areEqual(uiNode4.getId(), "term") && Intrinsics.areEqual(uiNode4.getRowBounds(), uiNode3.getRowBounds())) {
                        break;
                    }
                }
                final UiNode uiNode5 = (UiNode) obj3;
                if (uiNode5 != null) {
                    int top = bounds.getTop();
                    int bottom = bounds.getBottom();
                    int y = uiNode5.getBounds().getY();
                    if (top <= y && y <= bottom) {
                        act("expand-" + uiNode5.getText(), now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda4
                            @Override // kotlin.jvm.functions.Function0
                            public final Object invoke() {
                                Unit collectFront$lambda$69;
                                collectFront$lambda$69 = BotEngine.collectFront$lambda$69(BotEngine.this, uiNode5);
                                return collectFront$lambda$69;
                            }
                        });
                        return;
                    }
                }
            }
            if (getInlineCollection()) {
                ArrayList arrayList9 = new ArrayList();
                for (Object obj7 : list2) {
                    UiNode uiNode6 = (UiNode) obj7;
                    if (!this.collectedRows.contains(collectionIdentity(uiNode6)) && !this.collectionCursor.contains(uiNode6)) {
                        arrayList9.add(obj7);
                    }
                }
                List<InlinePair> inlinePairs = InlineCollectionKt.inlinePairs(arrayList9, ns, bounds);
                if (inlinePairs == null) {
                    fallbackInline();
                    return;
                } else if (!inlinePairs.isEmpty()) {
                    if (this.inlineStability.ready(inlinePairs)) {
                        readInlinePage(inlinePairs, now);
                        return;
                    }
                    return;
                }
            }
            if (getInlineCollection()) {
                uiNode = null;
            } else {
                Iterator<?> it7 = list.iterator();
                while (true) {
                    if (!it7.hasNext()) {
                        obj2 = null;
                        break;
                    }
                    obj2 = it7.next();
                    UiNode uiNode7 = (UiNode) obj2;
                    if (Intrinsics.areEqual(uiNode7.getId(), "txt_front") && uiNode7.getBounds().getHeight() >= 32 && uiNode7.getBounds().getBottom() <= bounds.getBottom() - 16) {
                        Bounds rowBounds = uiNode7.getRowBounds();
                        if (rowBounds == null) {
                            rowBounds = uiNode7.getBounds();
                        }
                        if (rowBounds.getTop() >= bounds.getTop()) {
                            Bounds rowBounds2 = uiNode7.getRowBounds();
                            if (rowBounds2 == null) {
                                rowBounds2 = uiNode7.getBounds();
                            }
                            if (rowBounds2.getBottom() <= bounds.getBottom() && !this.collectedRows.contains(collectionIdentity(uiNode7)) && !this.collectionCursor.contains(uiNode7)) {
                                break;
                            }
                        } else {
                            continue;
                        }
                    }
                }
                uiNode = (UiNode) obj2;
            }
            if (uiNode != null) {
                this.collectingObserved = uiNode;
                this.collectingIdentity = collectionIdentity(uiNode);
                readCollectionNode(uiNode, "front", new Function1() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda5
                    @Override // kotlin.jvm.functions.Function1
                    public final Object invoke(Object obj8) {
                        Unit collectFront$lambda$73;
                        collectFront$lambda$73 = BotEngine.collectFront$lambda$73(BotEngine.this, (UiNode) obj8);
                        return collectFront$lambda$73;
                    }
                });
                return;
            }
            Long l = this.lastActions.get("collect-scroll");
            if (now - (l != null ? l.longValue() : 0L) < scrollInterval()) {
                return;
            }
            if (this.unchanged < 2) {
                int i2 = this.scrolls;
                this.scrolls = i2 + 1;
                if (i2 >= AdvancedSettings.scrollSteps(this.storage.getPrefs())) {
                    throw new IllegalStateException("단어 수집 탐색 범위를 초과했습니다.".toString());
                }
                QuietJson jSONObject = new QuietJson();
                Pair<Integer, String> last3 = this.collectionCursor.getLast();
                QuietJson put8 = jSONObject.put("index", last3 != null ? last3.getFirst() : null);
                Pair<Integer, String> last4 = this.collectionCursor.getLast();
                QuietJson put9 = put8.put("word", last4 != null ? last4.getSecond() : null);
                if (!(list2 instanceof Collection) || !list2.isEmpty()) {
                    Iterator<?> it8 = list2.iterator();
                    while (it8.hasNext()) {
                        if (this.collectionCursor.isAnchor((UiNode) it8.next())) {
                            z = true;
                            break;
                        }
                    }
                }
                z = false;
                QuietJson put10 = put9.put("visible", z).put("strategy", this.collectionCursor.getStrategy());
                Intrinsics.checkNotNullExpressionValue(put10, "put(...)");
                log("collection_anchor", put10);
                this.collectionScrollSignature = signature(ns);
                this.lastActions.put("collect-scroll", Long.valueOf(now));
                this.service.scrollCollection(bounds, true, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda6
                    @Override // kotlin.jvm.functions.Function0
                    public final Object invoke() {
                        Unit collectFront$lambda$82;
                        collectFront$lambda$82 = BotEngine.collectFront$lambda$82(BotEngine.this);
                        return collectFront$lambda$82;
                    }
                });
                return;
            }
            if (this.collected.isEmpty()) {
                throw new IllegalStateException("수집된 단어가 없습니다.".toString());
            }
            Collection<Card> values = this.collected.values();
            Intrinsics.checkNotNullExpressionValue(values, "<get-values>(...)");
            ArrayList arrayList10 = new ArrayList();
            Iterator<?> it9 = values.iterator();
            while (it9.hasNext()) {
                CollectionsKt.addAll(arrayList10, ((Card) it9.next()).getWords());
            }
            ArrayList arrayList11 = arrayList10;
            ArrayList arrayList12 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList11, 10));
            Iterator it10 = arrayList11.iterator();
            while (it10.hasNext()) {
                arrayList12.add(TextRules.INSTANCE.normalize((String) it10.next()));
            }
            Set set2 = CollectionsKt.toSet(arrayList12);
            Set<String> set3 = this.observedCollectionWords;
            ArrayList arrayList13 = new ArrayList();
            for (Object obj8 : set3) {
                if (!set2.contains(TextRules.INSTANCE.normalize((String) obj8))) {
                    arrayList13.add(obj8);
                }
            }
            ArrayList arrayList14 = arrayList13;
            if (!arrayList14.isEmpty()) {
                throw new IllegalStateException(("화면에 보였지만 양면 수집이 누락된 단어가 있습니다: " + CollectionsKt.joinToString(CollectionsKt.take(arrayList14, 5), (CharSequence) ", ", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", null) + ". 테스트를 시작하지 않습니다.").toString());
            }
            if (!this.collectionCursor.missing().isEmpty()) {
                throw new IllegalStateException(("목록에서 수집하지 못한 카드 위치가 있습니다: " + CollectionsKt.take(this.collectionCursor.missing(), 5) + ". 테스트를 시작하지 않습니다.").toString());
            }
            Collection<Card> values2 = this.collected.values();
            Intrinsics.checkNotNullExpressionValue(values2, "<get-values>(...)");
            Vocabulary vocabulary = new Vocabulary(CollectionsKt.toList(values2), this.name, this.ocrOnly ? "device-qota-ocr" : this.ocrReads > 0 ? "device-screen+qota-ocr" : "device-screen", false, null, 16, null);
            this.vocabulary = vocabulary;
            QuietJson put11 = new QuietJson().put("cards", vocabulary.getCards().size()).put("traversed_to_end", true);
            Intrinsics.checkNotNullExpressionValue(put11, "put(...)");
            log("collected", put11);
            transition(this.collectReturnPhase, "수집 " + vocabulary.getCards().size() + "개 · 화면 대조");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectFront$lambda$69(final BotEngine botEngine, UiNode uiNode) {
        botEngine.service.tap(uiNode, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit collectFront$lambda$69$lambda$68;
                collectFront$lambda$69$lambda$68 = BotEngine.collectFront$lambda$69$lambda$68(botEngine);
                return collectFront$lambda$69$lambda$68;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectFront$lambda$69$lambda$68(BotEngine botEngine) {
        botEngine.service.hold(200L);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectFront$lambda$73(final BotEngine botEngine, final UiNode word) {
        Intrinsics.checkNotNullParameter(word, "word");
        botEngine.collectingWord = word;
        botEngine.service.tap(word, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda22
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit collectFront$lambda$73$lambda$72;
                collectFront$lambda$73$lambda$72 = BotEngine.collectFront$lambda$73$lambda$72(botEngine, word);
                return collectFront$lambda$73$lambda$72;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectFront$lambda$73$lambda$72(BotEngine botEngine, UiNode uiNode) {
        botEngine.transition("collect_back", "뜻 수집: " + uiNode.getText());
        botEngine.service.hold(300L);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectFront$lambda$82(BotEngine botEngine) {
        botEngine.service.hold(200L);
        return Unit.INSTANCE;
    }

    private final void collectTop(List<UiNode> ns, long now) {
        UiNode id;
        if (checkCollectionScreen(ns) && (id = id(ns, "recycler_view")) != null) {
            Long l = this.lastActions.get("collect-top");
            if (now - (l != null ? l.longValue() : 0L) < scrollInterval()) {
                return;
            }
            String signature = signature(ns);
            int i = Intrinsics.areEqual(signature, this.previousSignature) ? this.unchanged + 1 : 0;
            this.unchanged = i;
            this.previousSignature = signature;
            if (i >= 2) {
                this.unchanged = 0;
                this.previousSignature = "";
                transition("collect", "단어 양면 수집");
            } else {
                int i2 = this.scrolls;
                this.scrolls = i2 + 1;
                if (i2 >= 100) {
                    throw new IllegalStateException("단어 목록 처음으로 이동하지 못했습니다.".toString());
                }
                this.lastActions.put("collect-top", Long.valueOf(now));
                this.service.scrollCollection(id.getBounds(), false, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda12
                    @Override // kotlin.jvm.functions.Function0
                    public final Object invoke() {
                        Unit collectTop$lambda$55;
                        collectTop$lambda$55 = BotEngine.collectTop$lambda$55(BotEngine.this);
                        return collectTop$lambda$55;
                    }
                });
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit collectTop$lambda$55(BotEngine botEngine) {
        botEngine.service.hold(200L);
        return Unit.INSTANCE;
    }

    private final String collectionIdentity(UiNode node) {
        int i = this.collectionPage;
        Bounds rowBounds = node.getRowBounds();
        if (rowBounds == null) {
            rowBounds = node.getBounds();
        }
        return i + ":" + rowBounds;
    }

    public static /* synthetic */ void fail$default(BotEngine botEngine, String str, AnswerFailure answerFailure, int i, Object obj) {
        if ((i & 2) != 0) {
            answerFailure = null;
        }
        botEngine.fail(str, answerFailure);
    }

    private final void fallbackInline() {
        this.collectionDisplay.fallbackToFlip();
        this.inlinePairCount = 0;
        this.collectionReadPending = false;
        QuietJson put = new QuietJson().put("action", "inline_to_flip");
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("collection_fallback", put);
        beginCollection(this.collectReturnPhase);
    }

    private final void finishPreparation() {
        Vocabulary vocabulary = this.vocabulary;
        Intrinsics.checkNotNull(vocabulary);
        Vocabulary copy$default = Vocabulary.copy$default(vocabulary, null, this.name, null, false, null, 29, null);
        this.vocabulary = copy$default;
        AdvancedSettings.applyMatching(this.storage.getPrefs());
        this.solver = new Solver(copy$default, this.partialChoices);
        if (!Intrinsics.areEqual(this.mode, "screen_prepare")) {
            transition("open_test", "자료 준비 완료 · 응시 열기");
            return;
        }
        QuietJson put = new QuietJson().put("cards", copy$default.getCards().size());
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("prepared", put);
        writeSummary$default(this, "prepared", null, null, null, 14, null);
        this.active = false;
        this.epoch++;
        this.service.setStatus("준비 완료 · " + copy$default.getCards().size() + "개");
    }

    private final boolean getInlineCollection() {
        return this.collectionDisplay.getInline();
    }

    private final UiNode id(List<UiNode> list, String str) {
        Object obj;
        Iterator<?> it = list.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            if (Intrinsics.areEqual(((UiNode) obj).getId(), str)) {
                break;
            }
        }
        return (UiNode) obj;
    }

    private final void inputAnswer(List<UiNode> ns, List<UiNode> choices, final String progress, final String prompt, final long readAt, final int tries) {
        if (choices.size() != this.service.getProfile().getChoiceCount()) {
            return;
        }
        int max = Math.max(8, (int) (cardBounds(ns).getWidth() * 0.008d));
        List<UiNode> list = choices;
        Iterator<?> it = list.iterator();
        if (!it.hasNext()) {
            throw new NoSuchElementException();
        }
        int left = ((UiNode) it.next()).getBounds().getLeft();
        while (it.hasNext()) {
            int left2 = ((UiNode) it.next()).getBounds().getLeft();
            if (left < left2) {
                left = left2;
            }
        }
        Iterator<?> it2 = list.iterator();
        if (!it2.hasNext()) {
            throw new NoSuchElementException();
        }
        int left3 = ((UiNode) it2.next()).getBounds().getLeft();
        while (it2.hasNext()) {
            int left4 = ((UiNode) it2.next()).getBounds().getLeft();
            if (left3 > left4) {
                left3 = left4;
            }
        }
        if (left - left3 <= max) {
            Iterator<?> it3 = list.iterator();
            if (!it3.hasNext()) {
                throw new NoSuchElementException();
            }
            int right = ((UiNode) it3.next()).getBounds().getRight();
            while (it3.hasNext()) {
                int right2 = ((UiNode) it3.next()).getBounds().getRight();
                if (right < right2) {
                    right = right2;
                }
            }
            Iterator<?> it4 = list.iterator();
            if (!it4.hasNext()) {
                throw new NoSuchElementException();
            }
            int right3 = ((UiNode) it4.next()).getBounds().getRight();
            while (it4.hasNext()) {
                int right4 = ((UiNode) it4.next()).getBounds().getRight();
                if (right3 > right4) {
                    right3 = right4;
                }
            }
            if (right - right3 > max) {
                return;
            }
            Solver solver = this.solver;
            Intrinsics.checkNotNull(solver);
            final ChoiceAnswer answer = decideAnswer(solver, prompt, choices, progress);
            this.service.tap(answer.getTarget(), new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda20
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit inputAnswer$lambda$51;
                    inputAnswer$lambda$51 = BotEngine.inputAnswer$lambda$51(BotEngine.this, progress, prompt, answer, readAt, tries);
                    return inputAnswer$lambda$51;
                }
            }, tries > 1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit inputAnswer$lambda$51(BotEngine botEngine, String str, String str2, ChoiceAnswer choiceAnswer, long j, int i) {
        long now = SystemClock.elapsedRealtime();
        botEngine.pending = new Pending(str, str2, choiceAnswer, now, now - j, i);
        QuietJson put = new QuietJson().put("progress", str).put("answer", choiceAnswer.getTarget().getText()).put("attempt", i);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        botEngine.log("tap", put);
        botEngine.gate.resetObservation();
        return Unit.INSTANCE;
    }

    public static /* synthetic */ void log$default(BotEngine botEngine, String str, QuietJson jSONObject, int i, Object obj) {
        if ((i & 2) != 0) {
            jSONObject = new QuietJson();
        }
        botEngine.log(str, jSONObject);
    }

    private final void readCollectionNode(final UiNode node, final String side, final Function1<? super UiNode, Unit> after) {
        if (this.collectionStability.ready(side, node)) {
            this.collectionStability.reset();
            this.collectionReadAt = SystemClock.elapsedRealtime();
            if (!StringsKt.isBlank(node.getText()) && !this.imageCollection && !this.ocrOnly) {
                after.invoke(node);
                return;
            }
            this.collectionReadPending = true;
            this.ocrCaptures++;
            final int i = this.epoch;
            this.service.ocrNode(node, new Function1() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda3
                @Override // kotlin.jvm.functions.Function1
                public final Object invoke(Object obj) {
                    Unit readCollectionNode$lambda$105;
                    readCollectionNode$lambda$105 = BotEngine.readCollectionNode$lambda$105(BotEngine.this, i, side, node, after, obj);
                    return readCollectionNode$lambda$105;
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Removed duplicated region for block: B:15:0x007b A[Catch: Exception -> 0x0206, TryCatch #0 {Exception -> 0x0206, blocks: (B:7:0x0018, B:10:0x002f, B:12:0x004b, B:13:0x005b, B:15:0x007b, B:16:0x0085, B:19:0x0050, B:21:0x0054, B:24:0x0088, B:26:0x009f, B:28:0x00ad, B:29:0x00b3, B:31:0x00bb, B:33:0x00c2, B:36:0x0177, B:38:0x019a, B:39:0x019e, B:41:0x01ab, B:43:0x01d1, B:44:0x01dc, B:45:0x00cd, B:46:0x00d1, B:48:0x00d7, B:50:0x00eb, B:52:0x00f9, B:55:0x0107, B:57:0x012e, B:60:0x0133, B:68:0x01dd, B:69:0x01e8, B:71:0x01e9, B:72:0x0205), top: B:6:0x0018 }] */
    public static final Unit readCollectionNode$lambda$105(final BotEngine botEngine, int i, String str, UiNode uiNode, Function1 function1, Object result) {
        if (!botEngine.active || botEngine.epoch != i) {
            return Unit.INSTANCE;
        }
        botEngine.collectionReadPending = false;
        try {
            OcrReader.Read read = (OcrReader.Read) ResultCompat.valueOrThrow(result);
            if (read.getClipped()) {
                botEngine.collectedRows.add(botEngine.collectingIdentity);
                String text;
                if (Intrinsics.areEqual(str, "front")) {
                    text = uiNode.getText();
                } else {
                    UiNode uiNode2 = botEngine.collectingWord;
                    text = uiNode2 != null ? uiNode2.getText() : uiNode.getText();
                }
                QuietJson put = new QuietJson().put("side", str).put("word", text).put("recognized", read.getText()).put("reason", "glyph_touches_image_edge");
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                botEngine.log("collection_clipped", put);
                if (Intrinsics.areEqual(str, "back")) {
                    botEngine.service.tap(uiNode, new Function0() {
                        @Override
                        public final Object invoke() {
                            return BotEngine.readCollectionNode$lambda$105$lambda$100(botEngine);
                        }
                    });
                }
                return Unit.INSTANCE;
            }
            String obj = StringsKt.trim((CharSequence) read.getText()).toString();
            if (StringsKt.isBlank(obj)) {
                throw new IllegalArgumentException(("화면 이미지에서 " + str + " 내용을 읽지 못했습니다.").toString());
            }
            List<UiNode> readScreen = botEngine.service.readScreen();
            UiNode id = botEngine.id(readScreen, "set_name");
            if (!Intrinsics.areEqual(id != null ? id.getText() : null, botEngine.name)) {
                throw new IllegalStateException("이미지 인식 중 세트가 바뀌었습니다.".toString());
            }
            for (UiNode uiNode3 : readScreen) {
                if (Intrinsics.areEqual(uiNode3.getId(), uiNode.getId()) && Intrinsics.areEqual(uiNode3.getBounds(), uiNode.getBounds()) && Intrinsics.areEqual(uiNode3.getText(), uiNode.getText())) {
                    boolean usedScreenText = !StringsKt.isBlank(uiNode.getText()) && !botEngine.ocrOnly;
                    QuietJson put4 = new QuietJson().put("side", str).put("recognized", obj).put("screen_text", uiNode.getText()).put("used_screen_text", usedScreenText).put("ocr_mode", "qota-single-model").put("ocr_model", OcrReader.MODEL).put("confidence", read.getConfidence()).put("inference_ms", read.getInferenceMs());
                    Intrinsics.checkNotNullExpressionValue(put4, "put(...)");
                    botEngine.log("ocr_read", put4);
                    botEngine.ocrReads++;
                    function1.invoke(CoreKt.collectedText(uiNode, obj, botEngine.ocrOnly));
                    return Unit.INSTANCE;
                }
            }
            String str2 = botEngine.collectingIdentity + ":" + str;
            Integer num = botEngine.discardedReads.get(str2);
            int intValue = (num != null ? num.intValue() : 0) + 1;
            botEngine.discardedReads.put(str2, Integer.valueOf(intValue));
            if (intValue > 3) {
                throw new IllegalStateException("카드가 계속 이동하여 이미지 인식을 중지했습니다.".toString());
            }
            QuietJson put5 = new QuietJson().put("side", str).put("reason", "screen_moved").put("attempt", intValue);
            Intrinsics.checkNotNullExpressionValue(put5, "put(...)");
            botEngine.log("ocr_discarded", put5);
            botEngine.collectionReadAt = SystemClock.elapsedRealtime();
            return Unit.INSTANCE;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "이미지 수집 실패";
            }
            fail$default(botEngine, message, null, 2, null);
            return Unit.INSTANCE;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit readCollectionNode$lambda$105$lambda$100(BotEngine botEngine) {
        botEngine.transition("collect_restore", "잘린 카드 복원 · 겹치는 페이지에서 수집");
        botEngine.service.hold(300L);
        return Unit.INSTANCE;
    }

    /* JADX WARN: Removed duplicated region for block: B:16:0x0074  */
    /* JADX WARN: Removed duplicated region for block: B:24:0x00c5  */
    private final void readInlinePage(final List<InlinePair> pairs, long now) {
        final ArrayList<UiNode> nodes = new ArrayList<>();
        for (InlinePair inlinePair : pairs) {
            nodes.add(inlinePair.getFront());
            nodes.add(inlinePair.getMeaning());
        }
        boolean needsOcr = this.imageCollection || this.ocrOnly;
        if (!needsOcr) {
            for (UiNode node : nodes) {
                if (StringsKt.isBlank(node.getText())) {
                    needsOcr = true;
                    break;
                }
            }
        }
        this.inlineStability.reset();
        if (needsOcr) {
            this.collectionReadPending = true;
            this.collectionReadAt = now;
            this.ocrCaptures++;
            final int i = this.epoch;
            this.service.ocrNodes(nodes, new Function1() {
                @Override
                public final Object invoke(Object obj) {
                    return BotEngine.readInlinePage$lambda$99(BotEngine.this, i, nodes, pairs, obj);
                }
            });
            return;
        }
        for (InlinePair inlinePair2 : pairs) {
            recordCollected(inlinePair2.getFront(), inlinePair2.getMeaning(), inlinePair2.getFront(), collectionIdentity(inlinePair2.getFront()));
        }
        this.inlinePairCount += pairs.size();
        QuietJson put = new QuietJson().put("pairs", pairs.size()).put("used_screen_text", true);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("inline_batch", put);
    }

    public static final Unit readInlinePage$lambda$99(BotEngine botEngine, int i, List list, List list2, Object result) {
        if (!botEngine.active || botEngine.epoch != i) {
            return Unit.INSTANCE;
        }
        botEngine.collectionReadPending = false;
        List list3 = (List) ResultCompat.valueOrNull(result);
        if (list3 == null || list3.size() != list.size()) {
            botEngine.fallbackInline();
            return Unit.INSTANCE;
        }
        for (Object item : list3) {
            OcrReader.Read read = (OcrReader.Read) item;
            if (read.getClipped() || StringsKt.isBlank(read.getText())) {
                botEngine.fallbackInline();
                return Unit.INSTANCE;
            }
        }
        try {
            List<UiNode> readScreen = botEngine.service.readScreen();
            UiNode id = botEngine.id(readScreen, "set_name");
            if (!Intrinsics.areEqual(id != null ? id.getText() : null, botEngine.name)) {
                throw new IllegalStateException("목록 인식 중 세트가 바뀌었습니다.".toString());
            }
            boolean allVisible = true;
            for (Object item : list) {
                UiNode uiNode = (UiNode) item;
                boolean found = false;
                for (UiNode uiNode2 : readScreen) {
                    if (Intrinsics.areEqual(uiNode2.getId(), uiNode.getId()) && Intrinsics.areEqual(uiNode2.getText(), uiNode.getText()) && Intrinsics.areEqual(uiNode2.getBounds(), uiNode.getBounds()) && Intrinsics.areEqual(uiNode2.getRowBounds(), uiNode.getRowBounds())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    allVisible = false;
                    break;
                }
            }
            if (!allVisible) {
                botEngine.collectionReadAt = SystemClock.elapsedRealtime();
                Integer num = botEngine.discardedReads.get("inline");
                int intValue = (num != null ? num.intValue() : 0) + 1;
                botEngine.discardedReads.put("inline", Integer.valueOf(intValue));
                if (intValue >= 3) {
                    botEngine.fallbackInline();
                }
                QuietJson put = new QuietJson().put("action", "inline_screen_moved");
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                botEngine.log("ocr_discarded", put);
                return Unit.INSTANCE;
            }
            ArrayList arrayList = new ArrayList();
            int index = 0;
            for (Object item : list) {
                arrayList.add(CoreKt.collectedText((UiNode) item, ((OcrReader.Read) list3.get(index)).getText(), botEngine.ocrOnly));
                index++;
            }
            int pairIndex = 0;
            for (Object item : list2) {
                InlinePair inlinePair = (InlinePair) item;
                int i7 = pairIndex * 2;
                botEngine.recordCollected((UiNode) arrayList.get(i7), (UiNode) arrayList.get(i7 + 1), inlinePair.getFront(), botEngine.collectionIdentity(inlinePair.getFront()));
                pairIndex++;
            }
            botEngine.ocrReads += list3.size();
            botEngine.inlinePairCount += list2.size();
            double d = 0.0d;
            for (Object item : list3) {
                d += ((OcrReader.Read) item).getInferenceMs();
            }
            QuietJson put3 = new QuietJson().put("pairs", list2.size()).put("ocr_captures", 1).put("inference_ms", d).put("used_screen_text", !botEngine.ocrOnly);
            Intrinsics.checkNotNullExpressionValue(put3, "put(...)");
            botEngine.log("inline_batch", put3);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null) {
                message = "한 화면 수집 실패";
            }
            fail$default(botEngine, message, null, 2, null);
        }
        return Unit.INSTANCE;
    }

    private final void recordCollected(UiNode word, UiNode back, UiNode observed, String identity) {
        Card collectedCard = CoreKt.collectedCard(word, back);
        Pair<String, String> pair = new Pair<>(TextRules.INSTANCE.normalize(word.getText()), TextRules.INSTANCE.normalize(back.getText()));
        Card card = this.collected.get(pair);
        LinkedHashMap<Pair<String, String>, Card> linkedHashMap = this.collected;
        if (card != null) {
            collectedCard = Card.copy$default(collectedCard, null, null, CollectionsKt.distinct(CollectionsKt.plus((Collection) card.getAliases(), (Iterable) collectedCard.getAliases())), CollectionsKt.distinct(CollectionsKt.plus((Collection) card.getWordAliases(), (Iterable) collectedCard.getWordAliases())), 3, null);
        }
        linkedHashMap.put(pair, collectedCard);
        this.collectedRows.add(identity);
        if (observed != null) {
            this.collectionCursor.complete(observed);
        }
        QuietJson put = new QuietJson().put("word", word.getText()).put("meaning", back.getText()).put("cards", this.collected.size());
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("collected_card", put);
    }

    private final void saveCollectedBack(UiNode word, final UiNode back, long now) {
        recordCollected(word, back, this.collectingObserved, this.collectingIdentity);
        act("restore-" + this.collectingIdentity, now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda10
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit saveCollectedBack$lambda$87;
                saveCollectedBack$lambda$87 = BotEngine.saveCollectedBack$lambda$87(BotEngine.this, back);
                return saveCollectedBack$lambda$87;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit saveCollectedBack$lambda$87(final BotEngine botEngine, UiNode uiNode) {
        botEngine.service.tap(uiNode, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda14
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit saveCollectedBack$lambda$87$lambda$86;
                saveCollectedBack$lambda$87$lambda$86 = BotEngine.saveCollectedBack$lambda$87$lambda$86(botEngine);
                return saveCollectedBack$lambda$87$lambda$86;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit saveCollectedBack$lambda$87$lambda$86(BotEngine botEngine) {
        botEngine.transition("collect_restore", "카드 복원");
        botEngine.service.hold(300L);
        return Unit.INSTANCE;
    }

    private final String signature(List<UiNode> ns) {
        ArrayList arrayList = new ArrayList();
        for (Object obj : ns) {
            if (SetsKt.setOf((Object[]) new String[]{"txt_front", "txt_back", "txt_inline_back", "term", "txt_set_name"}).contains(((UiNode) obj).getId())) {
                arrayList.add(obj);
            }
        }
        return CollectionsKt.joinToString(arrayList, (CharSequence) "|", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", new Function1() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda7
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj2) {
                CharSequence signature$lambda$14;
                signature$lambda$14 = BotEngine.signature$lambda$14((UiNode) obj2);
                return signature$lambda$14;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final CharSequence signature$lambda$14(UiNode it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return it.getId() + ":" + it.getText() + ":" + it.getBounds();
    }

    private final void test(List<UiNode> ns, long now) {
        Object obj;
        String text;
        Integer intOrNull;
        String text2;
        Integer intOrNull2;
        boolean z;
        String text3;
        List<UiNode> list = ns;
        Iterator<?> it = list.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            UiNode uiNode = (UiNode) obj;
            if (Intrinsics.areEqual(uiNode.getId(), "btn_positive") && SetsKt.setOf((Object[]) new String[]{"응시", "새로시작"}).contains(uiNode.getText())) {
                break;
            }
        }
        final UiNode uiNode2 = (UiNode) obj;
        if (uiNode2 != null) {
            if (!this.replace) {
                throw new IllegalStateException("기존 미완료 응시가 있습니다. 무효화 허용이 필요합니다.".toString());
            }
            act("replace-" + uiNode2.getText(), now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda2
                @Override // kotlin.jvm.functions.Function0
                public final Object invoke() {
                    Unit test$lambda$34$lambda$33;
                    test$lambda$34$lambda$33 = BotEngine.test$lambda$34$lambda$33(BotEngine.this, uiNode2);
                    return test$lambda$34$lambda$33;
                }
            });
            return;
        }
        UiNode id = id(ns, "txt_test_score");
        String obj2 = (id == null || (text3 = id.getText()) == null) ? null : StringsKt.trim((CharSequence) text3).toString();
        if (obj2 == null || !new Regex("\\d+점").matches(obj2) || this.answered.isEmpty()) {
            UiNode id2 = id(ns, "txt_current_num");
            if (id2 != null && (text = id2.getText()) != null && (intOrNull = StringsKt.toIntOrNull(text)) != null) {
                int intValue = intOrNull.intValue();
                UiNode id3 = id(ns, "txt_total_cnt");
                if (id3 != null && (text2 = id3.getText()) != null && (intOrNull2 = StringsKt.toIntOrNull(text2)) != null) {
                    int intValue2 = intOrNull2.intValue();
                    if (1 > intValue || intValue > intValue2 || 1 > intValue2 || intValue2 >= 201) {
                        throw new IllegalStateException("문항 번호를 확인할 수 없습니다.".toString());
                    }
                    this.expectedQuestions = intValue2;
                    String str = intValue + " | " + intValue2;
                    this.lastProgress = str;
                    UiNode id4 = id(ns, "txt_quest");
                    if (id4 == null || StringsKt.isBlank(id4.getText())) {
                        id4 = null;
                    }
                    if (id4 != null) {
                        this.contexts.put(str, id4.getText());
                        if (this.loggedPrompts.add(str)) {
                            if (this.firstPrompt == 0) {
                                this.firstPrompt = now;
                            }
                            QuietJson put = new QuietJson().put("progress", str).put("prompt", id4.getText());
                            Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                            log("prompt", put);
                        }
                    }
                    String str2 = this.contexts.get(str);
                    if (str2 != null) {
                        ArrayList arrayList = new ArrayList();
                        for (Object obj3 : list) {
                            UiNode uiNode3 = (UiNode) obj3;
                            if (Intrinsics.areEqual(uiNode3.getId(), "txt_opt") && !StringsKt.isBlank(uiNode3.getText()) && uiNode3.getEnabled()) {
                                arrayList.add(obj3);
                            }
                        }
                        List<UiNode> sortedWith = CollectionsKt.sortedWith(arrayList, new Comparator() { // from class: net.classcardauto.mobile.BotEngine$test$$inlined$sortedBy$1
                            /* JADX WARN: Multi-variable type inference failed */
                            @Override // java.util.Comparator
                            public final int compare(Object t, Object t2) {
                                return ComparisonsKt.compareValues(Integer.valueOf(((UiNode) t).getBounds().getTop()), Integer.valueOf(((UiNode) t2).getBounds().getTop()));
                            }
                        });
                        UiNode id5 = id(ns, "txt_bottom_msg");
                        String text4 = id5 != null ? id5.getText() : null;
                        if (text4 == null) {
                            text4 = "";
                        }
                        boolean contains$default = StringsKt.contains((CharSequence) text4, (CharSequence) this.service.getProfile().getFeedbackMessage(), false);
                        if (!(list instanceof Collection) || !list.isEmpty()) {
                            Iterator<?> it2 = list.iterator();
                            while (it2.hasNext()) {
                                if (Intrinsics.areEqual(((UiNode) it2.next()).getId(), "txt_time_over")) {
                                    throw new IllegalStateException(("답 입력 제한 시간이 지났습니다: " + str).toString());
                                }
                            }
                        }
                        Pending pending = this.pending;
                        if (pending == null) {
                            z = contains$default;
                        } else {
                            if (!Intrinsics.areEqual(pending.getProgress(), str)) {
                                throw new IllegalStateException("입력 확인 전에 문항이 바뀌었습니다.".toString());
                            }
                            if (contains$default) {
                                this.answered.add(str);
                                this.pending = null;
                                this.tapTimes.add(Double.valueOf(pending.getReadMs()));
                                this.lastTap = pending.getTapped();
                                z = contains$default;
                                QuietJson put2 = new QuietJson().put("progress", str).put("prompt", pending.getPrompt()).put("answer", pending.getAnswer().getTarget().getText()).put("read_to_tap_ms", pending.getReadMs());
                                List<Card> cards = pending.getAnswer().getCards();
                                ArrayList arrayList2 = new ArrayList(CollectionsKt.collectionSizeOrDefault(cards, 10));
                                for (Iterator it3 = cards.iterator(); it3.hasNext(); it3 = it3) {
                                    Card card = (Card) it3.next();
                                    arrayList2.add(new QuietJson().put("word", card.getWord()).put("meaning", card.getMeaning()));
                                }
                                QuietJson put3 = put2.put("evidence", new JSONArray((Collection) arrayList2));
                                Intrinsics.checkNotNullExpressionValue(put3, "put(...)");
                                log("answer", put3);
                            } else if (now - pending.getTapped() > 800 && Intrinsics.areEqual(text4, this.service.getProfile().getReadyMessage())) {
                                if (pending.getTries() >= AdvancedSettings.tapRetries(this.storage.getPrefs())) {
                                    throw new IllegalStateException("정답 터치가 반영되지 않았습니다.".toString());
                                }
                                this.pending = null;
                                inputAnswer(ns, sortedWith, str, str2, now, pending.getTries() + 1);
                                return;
                            } else {
                                return;
                            }
                        }
                        this.service.setStatus(intValue + "/" + intValue2 + " · " + this.answered.size() + "개 입력");
                        if (Intrinsics.areEqual(text4, this.service.getProfile().getReadyMessage()) && !this.answered.contains(str)) {
                            if (humanReady(str, str2, sortedWith, now)) {
                                inputAnswer(ns, sortedWith, str, str2, now, 1);
                            }
                            return;
                        }
                        if (id4 != null && sortedWith.isEmpty() && !this.answered.contains(str)) {
                            if (this.natural || !humanAdvanceReady(str + ":prompt", now) || !SwipeGate.ready$default(this.gate, str + ":prompt", id4.getBounds(), now, false, 0, 24, null)) {
                                return;
                            }
                            this.service.swipeCard(cardBounds(ns), ns);
                            QuietJson put4 = new QuietJson().put("progress", str);
                            Intrinsics.checkNotNullExpressionValue(put4, "put(...)");
                            log("reveal_choices", put4);
                            return;
                        }
                        if (z && this.intervened && !this.answered.contains(str)) {
                            this.answered.add(str);
                            QuietJson adopted = new QuietJson().put("progress", str);
                            log("adopted_user_answer", adopted);
                        }
                        if (!z || !this.answered.contains(str)) {
                            this.gate.resetObservation();
                            return;
                        }
                        if (this.natural || !humanAdvanceReady(str + ":feedback", now) || !this.gate.ready(str + ":feedback", cardBounds(ns), now, false, 1)) {
                            return;
                        }
                        this.service.swipeCard(cardBounds(ns), ns);
                        QuietJson put5 = new QuietJson().put("progress", str);
                        Intrinsics.checkNotNullExpressionValue(put5, "put(...)");
                        log("advance", put5);
                    }
                }
            }
        } else {
            if (this.expectedQuestions <= 0 || (!this.intervened && this.answered.size() != this.expectedQuestions)) {
                throw new IllegalStateException("전체 문항 입력을 확인하지 못했습니다.".toString());
            }
            if (!(list instanceof Collection) || !list.isEmpty()) {
                for (UiNode uiNode4 : list) {
                    if (Intrinsics.areEqual(uiNode4.getId(), "message") && StringsKt.contains((CharSequence) uiNode4.getText(), (CharSequence) "전송", false)) {
                        this.resultSince = 0L;
                        return;
                    }
                }
            }
            if (this.resultSince == 0) {
                this.resultSince = now;
            }
            if (now - this.resultSince >= 750) {
                QuietJson put6 = new QuietJson().put("score", obj2).put("answers", this.answered.size());
                Intrinsics.checkNotNullExpressionValue(put6, "put(...)");
                log("result", put6);
                String str3 = obj2;
                writeSummary$default(this, "result", null, str3, null, 10, null);
                this.active = false;
                this.epoch++;
                String wrongNote = this.wrongSet != null ? " · 의도한 오답 " + this.wrongSet.size() + "개 중 " + this.wrongPick.size() + "개 실행" : "";
                this.service.alert(2);
                this.service.setStatus(str3 + " · " + this.answered.size() + "문항 완료" + wrongNote);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit test$lambda$34$lambda$33(BotEngine botEngine, UiNode uiNode) {
        BotService.tap$default(botEngine.service, uiNode, null, 2, null);
        return Unit.INSTANCE;
    }

    private final UiNode text(List<UiNode> list, String str) {
        Object obj;
        Iterator<?> it = list.iterator();
        while (true) {
            if (!it.hasNext()) {
                obj = null;
                break;
            }
            obj = it.next();
            if (Intrinsics.areEqual(((UiNode) obj).getText(), str)) {
                break;
            }
        }
        return (UiNode) obj;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$20$lambda$19(final BotEngine botEngine, UiNode uiNode) {
        botEngine.service.tap(uiNode, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda9
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit tick$lambda$20$lambda$19$lambda$18;
                tick$lambda$20$lambda$19$lambda$18 = BotEngine.tick$lambda$20$lambda$19$lambda$18(botEngine);
                return tick$lambda$20$lambda$19$lambda$18;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$20$lambda$19$lambda$18(BotEngine botEngine) {
        botEngine.service.hold(200L);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$21(List list, BotEngine botEngine) {
        CoreKt.validateEntryStructure(list, CurrentEntry.PREVIEW);
        QuietJson put = new QuietJson().put("action", "return_to_same_set");
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        botEngine.log("current_preview", put);
        botEngine.service.back();
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$25$lambda$24(final BotEngine botEngine, UiNode uiNode) {
        botEngine.service.tap(uiNode, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda13
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit tick$lambda$25$lambda$24$lambda$23;
                tick$lambda$25$lambda$24$lambda$23 = BotEngine.tick$lambda$25$lambda$24$lambda$23(botEngine);
                return tick$lambda$25$lambda$24$lambda$23;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$25$lambda$24$lambda$23(BotEngine botEngine) {
        botEngine.transition("intro", "테스트 안내 열기");
        botEngine.service.hold(350L);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$28$lambda$27(final BotEngine botEngine, UiNode uiNode) {
        botEngine.service.tap(uiNode, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda8
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                Unit tick$lambda$28$lambda$27$lambda$26;
                tick$lambda$28$lambda$27$lambda$26 = BotEngine.tick$lambda$28$lambda$27$lambda$26(botEngine);
                return tick$lambda$28$lambda$27$lambda$26;
            }
        });
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit tick$lambda$28$lambda$27$lambda$26(BotEngine botEngine) {
        botEngine.testStarted = SystemClock.elapsedRealtime();
        botEngine.transition("test", "문제 기다리는 중");
        return Unit.INSTANCE;
    }

    private final void transition(String next, String message) {
        this.phase = next;
        this.entered = SystemClock.elapsedRealtime();
        this.service.setStatus(message);
        QuietJson put = new QuietJson().put("phase", next).put("message", message);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("phase", put);
    }

    /* JADX WARN: Removed duplicated region for block: B:23:0x0120  */
    /* JADX WARN: Removed duplicated region for block: B:26:0x012d  */
    /* JADX WARN: Removed duplicated region for block: B:33:0x0168  */
    /* JADX WARN: Removed duplicated region for block: B:38:0x01ad  */
    /* JADX WARN: Removed duplicated region for block: B:41:0x025b  */
    /* JADX WARN: Removed duplicated region for block: B:45:0x01b0  */
    /* JADX WARN: Removed duplicated region for block: B:47:0x0173  */
    /* JADX WARN: Removed duplicated region for block: B:49:0x0132  */
    private final void writeSummary(String status, String reason, String score, AnswerFailure failure) {
        long elapsedRealtime = SystemClock.elapsedRealtime();
        List sorted = CollectionsKt.sorted(this.tapTimes);
        Object median;
        if (sorted.isEmpty()) {
            median = JSONObject.NULL;
        } else if (sorted.size() % 2 == 1) {
            median = sorted.get(sorted.size() / 2);
        } else {
            median = Double.valueOf((((Number) sorted.get((sorted.size() / 2) - 1)).doubleValue() + ((Number) sorted.get(sorted.size() / 2)).doubleValue()) / 2);
        }
        Object maxOrNull = CollectionsKt.maxOrNull((Iterable<Double>) this.tapTimes);
        if (maxOrNull == null) {
            maxOrNull = JSONObject.NULL;
        }
        Object testSeconds = this.testStarted > 0 ? Double.valueOf((elapsedRealtime - this.testStarted) / 1000.0d) : JSONObject.NULL;
        Object firstToLast = (this.firstPrompt > 0 && this.lastTap > 0) ? Double.valueOf((this.lastTap - this.firstPrompt) / 1000.0d) : JSONObject.NULL;
        Object errorCode;
        if (Intrinsics.areEqual(status, "error")) {
            errorCode = failure != null ? failure.name() : "UNCLASSIFIED";
        } else {
            errorCode = JSONObject.NULL;
        }
        String source = this.vocabulary != null ? this.vocabulary.getSource() : "";
        QuietJson summary = new QuietJson().put("status", status).put("reason", StringsKt.isBlank(reason) ? "" : "실행 중지 · 자료 삭제").put("score", score == null ? JSONObject.NULL : score).put("set_name", this.name).put("set_code", this.code).put("answers", this.answered.size()).put("expected_questions", this.expectedQuestions).put("runtime", "android-accessibility").put("requires_adb", false).put("requires_pc", false).put("pace", this.human ? "human" : (this.natural ? "natural" : "fast")).put("total_seconds", (elapsedRealtime - this.started) / 1000.0d).put("test_seconds", testSeconds).put("first_prompt_to_last_tap_seconds", firstToLast).put("read_to_tap_median_ms", median).put("read_to_tap_max_ms", maxOrNull).put("run_directory", this.run != null ? this.run.getName() : null).put("finished_at", Instant.now().toString()).put("mode", this.mode).put("source", source).put("error_code", errorCode).put("last_progress", this.lastProgress).put("collected_cards", this.collected.size()).put("ocr_reads", this.ocrReads).put("inline_pairs", this.inlinePairCount).put("ocr_captures", this.ocrCaptures).put("collection_mode", getInlineCollection() ? "inline" : "flip").put("collection_indexed", this.collectionCursor.getIndexed()).put("collection_reused_rows", this.collectionReusedRows).put("collection_strategy", this.collectionCursor.getStrategy()).put("ocr_mode", "qota-single-model").put("ocr_model", OcrReader.MODEL).put("ocr_only", this.ocrOnly).put("deliberate_wrong", this.wrongPick.size()).put("pauses", this.pauseState.getPauseCount()).put("intervened", this.intervened);
        this.active = false;
        this.epoch++;
        this.vocabulary = null;
        this.solver = null;
        this.pending = null;
        this.sample = null;
        this.collected.clear();
        this.collectedRows.clear();
        this.collectionCursor.reset();
        this.collectingWord = null;
        this.collectingObserved = null;
        this.collectingIdentity = "";
        this.observedCollectionWords.clear();
        this.contexts.clear();
        this.loggedPrompts.clear();
        this.lastActions.clear();
        this.actionAttempts.clear();
        this.collectionStability.reset();
        this.discardedReads.clear();
        this.previousSignature = "";
        this.inlineStability.reset();
        this.collectionScrollSignature = null;
        this.collectionReadPending = false;
        summary.put("data_discarded", true).put("retained_cards", 0).put("data_storage", "memory-only");
        // kept in memory only (lost when the process ends); nothing about the run or the set is written to storage
        summary.remove("set_name");
        summary.remove("set_code");
        Storage.setLatestSummary(summary.toString(2));
    }

    static /* synthetic */ void writeSummary$default(BotEngine botEngine, String str, String str2, String str3, AnswerFailure answerFailure, int i, Object obj) {
        if ((i & 2) != 0) {
            str2 = "";
        }
        if ((i & 4) != 0) {
            str3 = null;
        }
        if ((i & 8) != 0) {
            answerFailure = null;
        }
        botEngine.writeSummary(str, str2, str3, answerFailure);
    }

    public final void begin(String request) {
        Intrinsics.checkNotNullParameter(request, "request");
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException(UsageConsent.INSTANCE.getRequiredMessage().toString());
        }
        if (!SetsKt.setOf((Object[]) new String[]{"screen_auto", "screen_prepare"}).contains(request)) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        String installedVersion = this.service.getProfile().installedVersion(this.service);
        this.vocabulary = null;
        this.mode = request;
        this.active = true;
        this.epoch++;
        this.desiredDay = 0;
        this.titleFilter = "";
        this.imageCollection = this.storage.getPrefs().getBoolean("image_collection", false);
        this.ocrOnly = this.storage.getPrefs().getBoolean("ocr_only", false);
        this.collectionReadPending = false;
        this.collectionReadAt = 0L;
        this.ocrReads = 0;
        this.collectingObserved = null;
        this.collectingIdentity = "";
        this.collectionStability.reset();
        this.discardedReads.clear();
        this.collectionDisplay.reset();
        this.inlinePairCount = 0;
        this.ocrCaptures = 0;
        this.inlineStability.reset();
        this.natural = this.storage.getPrefs().getBoolean("natural", false);
        this.human = this.storage.getPrefs().getBoolean("human_like", false);
        this.wrongSet = null;
        this.calibrating = false;
        this.autoPaused = false;
        this.watchStamp = "";
        this.wrongOwed = 0;
        this.wrongSkipped.clear();
        this.wrongPick.clear();
        this.pauseState.reset();
        this.blockedTouches = 0;
        this.intervened = false;
        this.guessNext = false;
        this.guessPick.clear();
        this.humanDue.clear();
        this.replace = this.storage.getPrefs().getBoolean("replace", false);
        this.partialChoices = this.storage.getPrefs().getBoolean("partial_choices", false);
        this.started = SystemClock.elapsedRealtime();
        this.testStarted = 0L;
        this.firstPrompt = 0L;
        this.lastTap = 0L;
        this.name = "";
        this.code = "";
        this.expectedQuestions = 0;
        this.pending = null;
        this.sample = null;
        this.resultSince = 0L;
        this.lastProgress = "";
        this.gate = new SwipeGate(AdvancedSettings.gateMs(this.storage.getPrefs()), 2);
        this.contexts.clear();
        this.answered.clear();
        this.loggedPrompts.clear();
        this.tapTimes.clear();
        this.lastActions.clear();
        this.actionAttempts.clear();
        this.collected.clear();
        this.collectedRows.clear();
        this.collectionCursor.reset();
        this.collectionReusedRows = 0;
        this.previousSignature = "";
        this.unchanged = 0;
        this.scrolls = 0;
        this.searchReset = false;
        // nothing about a run is written to storage (no run folder, no event log); see log()
        this.run = null;
        this.events = null;
        QuietJson put = new QuietJson().put("mode", this.mode).put("runtime", "android-accessibility").put("day", this.desiredDay).put("device", Build.MANUFACTURER + " " + Build.MODEL).put("android_sdk", Build.VERSION.SDK_INT).put("started_at", Instant.now().toString()).put("requires_adb", false).put("pace", this.natural ? "natural" : "fast").put("ocr_mode", "qota-single-model").put("ocr_model", OcrReader.MODEL).put("ocr_only", this.ocrOnly);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("start", put);
        QuietJson put2 = new QuietJson().put("name", this.service.getProfile().getName()).put("classcard_version", installedVersion);
        Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
        log("profile", put2);
        transition("current_entry", "클래스카드 준비");
    }

    public final void fail(String reason, AnswerFailure failure) {
        String str;
        Intrinsics.checkNotNullParameter(reason, "reason");
        if (this.active) {
            writeFailureRecord(reason, failure);
            QuietJson put = new QuietJson().put("message", reason).put("phase", this.phase);
            if (failure == null || (str = failure.name()) == null) {
                str = "UNCLASSIFIED";
            }
            QuietJson put2 = put.put("error_code", str).put("progress", this.lastProgress);
            Intrinsics.checkNotNullExpressionValue(put2, "put(...)");
            log("error", put2);
            writeSummary$default(this, "error", reason, null, failure, 4, null);
            this.active = false;
            this.epoch++;
            this.service.alert(1);
            this.service.setStatus(reason);
        }
    }

    public final boolean getActive() {
        return this.active;
    }

    public final String getPhase() {
        return this.phase;
    }

    /** True until the actual test screen is reached; scroll/collection and intro screens count as "before". */
    public final boolean getBeforeTest() {
        return this.active && !"test".equals(this.phase);
    }

    public final boolean getCollecting() {
        return this.active && this.phase != null && this.phase.startsWith("collect");
    }

    private final java.util.Random rng = new java.util.Random();
    private final java.util.Map<String, Long> humanDue = new java.util.HashMap<>();
    private boolean human;
    private final PauseState pauseState = new PauseState();
    private boolean intervened;
    private boolean guessNext;
    private final java.util.Map<String, String> guessPick = new java.util.HashMap<>();

    private final boolean paused() {
        return this.pauseState.isPaused();
    }

    public final boolean isPaused() {
        return this.active && this.pauseState.isPaused();
    }

    public final int getUserTouches() {
        return this.pauseState.getTouches();
    }

    public final int getTouchLimit() {
        return AdvancedSettings.touchPauseCount(this.storage.getPrefs());
    }

    private int blockedTouches;

    /** True while the whole screen except the floating panel swallows touches: a run is going, not paused, setting on. */
    public final boolean isTouchLocked() {
        return this.active && !this.pauseState.isPaused() && this.storage.getPrefs().getBoolean("touch_lock", true);
    }

    public final int getBlockedTouches() {
        return this.blockedTouches;
    }

    /** A finger landed on the blocking layer (swallowed, not passed to ClassCard). */
    public final void registerBlockedTouch() {
        if (!isTouchLocked()) {
            return;
        }
        this.blockedTouches++;
        if (this.blockedTouches <= 50) {
            log("blocked_touch", new QuietJson().put("count", this.blockedTouches).put("phase", this.phase));
        }
        this.service.refreshOverlay();
    }

    public final boolean isInTest() {
        return this.active && "test".equals(this.phase);
    }

    /** Stops the bot acting until {@link #resume()}; the screen is left exactly as it is. */
    public final void pause(String reason) {
        if (!this.active || !this.pauseState.pause(SystemClock.elapsedRealtime())) {
            return;
        }
        QuietJson put = new QuietJson().put("reason", reason).put("phase", this.phase).put("touches", this.pauseState.getTouches());
        log("paused", put);
        this.service.setStatus("일시정지됨 · " + reason);
    }

    /**
     * Continues after a pause: the run clocks are moved forward by the pause, in-flight state is dropped, the touch
     * count restarts at 0, and the first answer afterwards is a guess if the question cannot be resolved (the user
     * may have moved on or answered by hand while paused).
     */
    public final void resume() {
        this.autoPaused = false;
        resumeInternal(true);
    }

    /** Resume after the run held itself (ClassCard out of sight / screen locked); a short absence changes nothing else. */
    public final void resumeAuto() {
        if (!isAutoPaused()) {
            return;
        }
        long held = SystemClock.elapsedRealtime() - this.autoPausedAt;
        this.autoPaused = false;
        resumeInternal(held >= 8000);
    }

    private final void resumeInternal(boolean strict) {
        long pausedFor = this.pauseState.resume(SystemClock.elapsedRealtime());
        if (pausedFor < 0 || !this.active) {
            return;
        }
        this.started += pausedFor;
        this.entered += pausedFor;
        if (this.testStarted > 0) {
            this.testStarted += pausedFor;
        }
        this.humanDue.clear();
        this.pending = null;
        this.collectionReadPending = false;
        this.gate = new SwipeGate(AdvancedSettings.gateMs(this.storage.getPrefs()), 2);
        this.service.markScreenSeen();
        this.watchStamp = "";
        this.collectionStability.reset();
        this.inlineStability.reset();
        this.previousSignature = "";
        this.unchanged = 0;
        if (strict) {
            // someone may have moved on or answered by hand: do not trust the old question, guess the first unresolved one
            this.intervened = true;
            this.guessNext = true;
        }
        QuietJson put = new QuietJson().put("paused_ms", pausedFor).put("phase", this.phase);
        log("resumed", put);
        this.service.setStatus(strict ? "재개했어요" : "자동으로 이어서 진행해요");
    }

    // ---- personal pace: watch the user press start and answer a few questions by hand (memory only) ----

    public static final int CALIBRATION_TARGET = 8;
    private boolean calibrating;
    private long calStartedAt;
    private final java.util.List<PaceModel.Think> calThink = new ArrayList<>();
    private final java.util.List<Long> calReveal = new ArrayList<>();
    private final java.util.List<Long> calAdvance = new ArrayList<>();
    private final java.util.List<Long> calNav = new ArrayList<>();
    private CurrentEntry calEntry;
    private long calEntrySeenAt;
    private String calProgress = "";
    private long calPromptAt;
    private long calChoicesAt;
    private long calFeedbackAt;
    private int calPromptChars;
    private int calChoiceChars;
    private boolean calThinkTaken;
    private boolean calQuestionOpen;

    public final boolean isCalibrating() {
        return this.calibrating;
    }

    public final int getCalibrationCount() {
        return this.calThink.size();
    }

    private final void clearCalibration() {
        this.calThink.clear();
        this.calReveal.clear();
        this.calAdvance.clear();
        this.calNav.clear();
        this.calEntry = null;
        this.calEntrySeenAt = 0L;
        this.calProgress = "";
        this.calPromptAt = 0L;
        this.calChoicesAt = 0L;
        this.calFeedbackAt = 0L;
        this.calThinkTaken = false;
        this.calQuestionOpen = false;
    }

    public final void startCalibration() {
        if (this.active) {
            return;
        }
        this.calibrating = true;
        this.calStartedAt = SystemClock.elapsedRealtime();
        clearCalibration();
    }

    public final void cancelCalibration() {
        this.calibrating = false;
        clearCalibration();
    }

    /**
     * Ends the measurement. With at least 3 answered questions everything measured is turned into the settings of the
     * human-like mode; returns a message for the user (or null when nothing was saved).
     */
    public final String finishCalibration() {
        String message = null;
        if (this.calThink.size() >= 3) {
            PaceModel.Result result = PaceModel.summarize(this.calThink, this.calReveal, this.calAdvance, this.calNav);
            HumanProfile.apply(this.storage.getPrefs(), result);
            message = "내 패턴 반영 (" + this.calThink.size() + "문항) · " + result.describe();
        }
        this.calibrating = false;
        clearCalibration();
        return message;
    }

    /** Called every tick while measuring: times the start presses, the opening of choices, the answers and the swipes on. */
    public final void observeCalibration(List<UiNode> ns, long now) {
        if (!this.calibrating || this.active) {
            return;
        }
        CurrentEntry entry = CoreKt.currentEntry(ns);
        if (entry == CurrentEntry.SET || entry == CurrentEntry.PREVIEW) {
            if (this.calEntry != entry) {
                // a new start-type screen: the wait before pressing is counted from the later of "measuring began" and "screen appeared"
                this.calEntry = entry;
                this.calEntrySeenAt = Math.max(now, this.calStartedAt);
            }
            return;
        }
        if (entry != CurrentEntry.ACTIVE_TEST) {
            return;
        }
        if (this.calEntry != null && this.calEntrySeenAt > 0) {
            long wait = now - this.calEntrySeenAt;
            if (wait >= 300 && wait <= 120000) {
                this.calNav.add(Long.valueOf(wait));
            }
            this.calEntry = null;
            this.calEntrySeenAt = 0L;
        }
        UiNode current = id(ns, "txt_current_num");
        UiNode total = id(ns, "txt_total_cnt");
        if (current == null || total == null) {
            return;
        }
        String progress = current.getText().trim() + " | " + total.getText().trim();
        UiNode quest = id(ns, "txt_quest");
        boolean promptShown = quest != null && !StringsKt.isBlank(quest.getText());
        if (!progress.equals(this.calProgress)) {
            // the next question appeared: how long the card was left after the previous answer
            if (this.calQuestionOpen && this.calFeedbackAt > 0) {
                long pause = now - this.calFeedbackAt;
                if (pause >= 120 && pause <= 30000) {
                    this.calAdvance.add(Long.valueOf(pause));
                }
            }
            this.calProgress = progress;
            this.calPromptAt = 0L;
            this.calChoicesAt = 0L;
            this.calFeedbackAt = 0L;
            this.calThinkTaken = false;
            this.calQuestionOpen = true;
        }
        UiNode message = id(ns, "txt_bottom_msg");
        String bottom = message != null ? message.getText() : "";
        int choices = 0;
        int chars = 0;
        for (UiNode node : ns) {
            if (Intrinsics.areEqual(node.getId(), "txt_opt") && !StringsKt.isBlank(node.getText()) && node.getEnabled()) {
                choices++;
                chars += node.getText().length();
            }
        }
        boolean choicesShown = choices == this.service.getProfile().getChoiceCount() && bottom.equals(this.service.getProfile().getReadyMessage());
        if (this.calPromptAt == 0 && promptShown && !choicesShown) {
            this.calPromptAt = now;
            this.calPromptChars = quest.getText().length();
        }
        if (this.calChoicesAt == 0 && choicesShown) {
            this.calChoicesAt = now;
            this.calChoiceChars = chars;
            if (quest != null) {
                this.calPromptChars = quest.getText().length();
            }
            if (this.calPromptAt > 0 && now - this.calPromptAt >= 250) {
                this.calReveal.add(Long.valueOf(now - this.calPromptAt));
            }
        }
        if (this.calChoicesAt > 0 && this.calFeedbackAt == 0 && bottom.contains(this.service.getProfile().getFeedbackMessage())) {
            this.calFeedbackAt = now;
            long ms = now - this.calChoicesAt - PaceModel.TAP_LATENCY_MS;
            if (!this.calThinkTaken && ms >= 300 && ms <= 30000) {
                this.calThinkTaken = true;
                this.calThink.add(new PaceModel.Think(ms, this.calPromptChars, this.calChoiceChars));
                if (this.calThink.size() >= CALIBRATION_TARGET) {
                    this.service.onCalibrationDone(finishCalibration());
                    return;
                }
                this.service.setStatus("측정 중 " + this.calThink.size() + "/" + CALIBRATION_TARGET + " · 계속 직접 풀어 주세요");
            }
        }
    }

    /** Seconds until the test is expected to be answered completely, from this run's own pace; -1 while unknown. */
    public final long getEtaSeconds() {
        if (!this.active || this.pauseState.isPaused() || !isInTest() || this.testStarted <= 0 || this.expectedQuestions <= 0) {
            return -1L;
        }
        int done = this.answered.size();
        if (done < 3 || done >= this.expectedQuestions) {
            return -1L;
        }
        long perQuestion = (SystemClock.elapsedRealtime() - this.testStarted) / done;
        return (perQuestion * (this.expectedQuestions - done)) / 1000L;
    }

    // ---- stability: self-hold when ClassCard is out of sight, watchdog, collection touch resync ----

    private boolean autoPaused;
    private long autoPausedAt;
    private String watchStamp = "";
    private long watchSince;
    private boolean watchResynced;

    public final boolean isAutoPaused() {
        return this.active && this.autoPaused && this.pauseState.isPaused();
    }

    public final long autoPausedFor(long now) {
        return isAutoPaused() ? now - this.autoPausedAt : 0L;
    }

    /** Waits (instead of stopping) while ClassCard cannot be seen or the screen is locked; resumes by itself later. */
    public final void autoHold(String reason) {
        if (!this.active || this.pauseState.isPaused()) {
            return;
        }
        pause(reason);
        if (this.pauseState.isPaused()) {
            this.autoPaused = true;
            this.autoPausedAt = SystemClock.elapsedRealtime();
            this.service.alert(0);
        }
    }

    /** A finger touched the screen while words were being collected: forget the half-read page and wait a moment. */
    public final void registerCollectionTouch() {
        if (!this.active || this.pauseState.isPaused() || this.phase == null || !this.phase.startsWith("collect")) {
            return;
        }
        this.previousSignature = "";
        this.unchanged = 0;
        this.collectionReadPending = false;
        this.inlineStability.reset();
        this.collectionStability.reset();
        this.service.hold(1500L);
        log("collection_touch", new QuietJson().put("phase", this.phase));
    }

    /** Screen rotated or similar: positions changed, so drop in-flight state and look again. */
    public final void resyncAfterChange() {
        if (this.active) {
            resync();
        }
    }

    private final void resync() {
        this.humanDue.clear();
        this.pending = null;
        this.collectionReadPending = false;
        this.previousSignature = "";
        this.unchanged = 0;
        this.inlineStability.reset();
        this.collectionStability.reset();
        this.gate = new SwipeGate(AdvancedSettings.gateMs(this.storage.getPrefs()), 2);
        this.service.markScreenSeen();
        log("resync", new QuietJson().put("phase", this.phase));
    }

    /** Nothing moved for a long time: first look again, then give up with a clear reason. */
    private final void watchdog(long now) {
        String stamp = this.phase + "|" + this.lastProgress + "|" + this.answered.size() + "|" + this.collected.size() + "|" + this.scrolls + "|" + (this.pending != null) + "|" + this.humanDue.size() + "|" + this.guessPick.size();
        if (!stamp.equals(this.watchStamp)) {
            this.watchStamp = stamp;
            this.watchSince = now;
            this.watchResynced = false;
            return;
        }
        long idle = now - this.watchSince;
        // word collection can scroll for a long time without any of the counted values changing
        boolean collecting = this.phase != null && this.phase.startsWith("collect");
        long resyncAfter = collecting ? 90000L : 30000L;
        long giveUpAfter = collecting ? 180000L : 60000L;
        if (idle > giveUpAfter) {
            throw new IllegalStateException("진행이 멈춰서 중지했습니다 (같은 화면에서 " + (giveUpAfter / 1000) + "초 변화 없음)");
        }
        if (idle > resyncAfter && !this.watchResynced) {
            this.watchResynced = true;
            resync();
        }
    }

    /** A touch by the user during the test; pauses the run when the configured number is reached. */
    public final void registerUserTouch() {
        if (!isInTest()) {
            return;
        }
        int limit = getTouchLimit();
        boolean reached = this.pauseState.addTouch(limit);
        QuietJson put = new QuietJson().put("count", this.pauseState.getTouches()).put("limit", limit);
        log("user_touch", put);
        if (reached) {
            pause("직접 터치 " + this.pauseState.getTouches() + "회");
        } else {
            this.service.refreshOverlay();
        }
    }
    private java.util.Set<Integer> wrongSet;
    private int wrongOwed;
    private final java.util.Set<String> wrongSkipped = new java.util.HashSet<>();
    private final java.util.Map<String, String> wrongPick = new java.util.HashMap<>();

    /**
     * The answer to tap. Normally the correct one; for the questions chosen to be answered wrong on purpose it is a
     * choice that definitely does not match. A question keeps the same wrong choice if the tap is repeated.
     */
    private final ChoiceAnswer decideAnswer(Solver solver, String prompt, List<UiNode> choices, String progress) {
        String guessed = this.guessPick.get(progress);
        if (guessed != null) {
            for (UiNode node : choices) {
                if (Intrinsics.areEqual(node.getText(), guessed)) {
                    return new ChoiceAnswer(node, new ArrayList<Card>(), 0);
                }
            }
        }
        ChoiceAnswer correct;
        try {
            correct = solver.answer(prompt, choices);
        } catch (AnswerResolutionException e) {
            if (this.guessNext && !choices.isEmpty()) {
                this.guessNext = false;
                UiNode pick = choices.get(this.rng.nextInt(choices.size()));
                this.guessPick.put(progress, pick.getText());
                QuietJson put = new QuietJson().put("progress", progress).put("reason", e.getFailure().name());
                log("resume_guess", put);
                return new ChoiceAnswer(pick, new ArrayList<Card>(), 0);
            }
            throw e;
        }
        this.guessNext = false;
        int wanted = AdvancedSettings.wrongCount(this.storage.getPrefs());
        if (wanted <= 0 || this.expectedQuestions <= 0) {
            return correct;
        }
        if (this.wrongSet == null) {
            this.wrongSet = WrongAnswers.chooseQuestions(this.rng, this.expectedQuestions, wanted);
            QuietJson planned = new QuietJson().put("count", this.wrongSet.size()).put("total", this.expectedQuestions);
            log("deliberate_wrong_planned", planned);
        }
        boolean planned = this.wrongSet.contains(Integer.valueOf(WrongAnswers.questionNumber(progress)));
        if (!planned && !this.wrongPick.containsKey(progress) && (this.wrongOwed <= 0 || this.wrongSkipped.contains(progress))) {
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
            // among the certainly-wrong choices avoid ones that look like the right one (shared words, near spelling)
            List<Integer> distinct = new ArrayList<>();
            for (Integer i : candidates) {
                if (Matcher.similarity(Matcher.loose(correct.getTarget().getText()), Matcher.loose(choices.get(i.intValue()).getText())) < 0.5) {
                    distinct.add(i);
                }
            }
            if (!distinct.isEmpty()) {
                candidates = distinct;
            }
            int index = WrongAnswers.pickWrong(this.rng, candidates);
            if (index < 0) {
                // no choice is certainly wrong here: leave it right and make up for it on a later question
                if (this.wrongSkipped.add(progress) && planned) {
                    this.wrongOwed++;
                }
                log("deliberate_wrong_skipped", new QuietJson().put("progress", progress).put("owed", this.wrongOwed));
                return correct;
            }
            pick = choices.get(index);
            if (!planned) {
                this.wrongOwed--;
            }
            this.wrongPick.put(progress, pick.getText());
            QuietJson put = new QuietJson().put("progress", progress);
            log("deliberate_wrong", put);
        }
        return new ChoiceAnswer(pick, new ArrayList<Card>(), 0);
    }

    /** Seconds until the run limit stops this run, or -1 when nothing is running. */
    public final long getRemainingSeconds() {
        if (!this.active) {
            return -1L;
        }
        return this.pauseState.remainingSeconds(this.started, runLimitMs(), SystemClock.elapsedRealtime());
    }

    private final long runLimitMs() {
        return AdvancedSettings.runLimitSeconds(this.storage.getPrefs()) * 1000L;
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
        FailureLog.append(this.storage.getRoot(), record);
    }

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
            android.content.SharedPreferences hp = this.storage.getPrefs();
            long wait = HumanTiming.thinkMillis(this.rng, mean, prompt.length(), choiceChars, this.answered.size(), HumanProfile.sigma(hp), HumanProfile.hesitation(hp), HumanProfile.fatiguePerQuestion(hp));
            due = Long.valueOf(now + wait);
            this.humanDue.put(key, due);
            QuietJson put = new QuietJson().put("progress", progress).put("think_ms", wait);
            log("think", put);
        }
        return now >= due.longValue();
    }

    /** A human-like wait before pressing a start button; a no-op unless a wait was measured or set. */
    private final boolean humanNavReady(String key, long now) {
        long median = HumanProfile.navMs(this.storage.getPrefs());
        if (median <= 0) {
            return true;
        }
        Long due = this.humanDue.get(key);
        if (due == null) {
            due = Long.valueOf(now + HumanTiming.navMillis(this.rng, median));
            this.humanDue.put(key, due);
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
            android.content.SharedPreferences hp = this.storage.getPrefs();
            // ":prompt" is the pause before opening the choices, ":feedback" the pause before swiping on
            long median = key.endsWith(":prompt") ? HumanProfile.revealMs(hp, mean) : HumanProfile.advanceMs(hp, mean);
            due = Long.valueOf(now + HumanTiming.pauseMillis(this.rng, median));
            this.humanDue.put(key, due);
        }
        return now >= due.longValue();
    }

    private final long scrollInterval() {
        return ScrollSettings.intervalMs(this.storage.getPrefs());
    }

    public final int getEpoch() {
        return this.epoch;
    }

    public final String getMode() {
        return this.mode;
    }

    /**
     * Run events are deliberately not recorded anywhere: not in a file, not in logcat. Keeping notes about ClassCard
     * screens would be retaining service content, so the call sites stay (they document the flow) but do nothing.
     */
    public final void log(String type, QuietJson data) {
    }

    public final void stop(String reason) {
        Intrinsics.checkNotNullParameter(reason, "reason");
        if (this.active) {
            QuietJson put = new QuietJson().put("reason", reason).put("phase", this.phase);
            Intrinsics.checkNotNullExpressionValue(put, "put(...)");
            log("stopped", put);
            writeSummary$default(this, "stopped", reason, null, null, 12, null);
            this.active = false;
            this.epoch++;
            if (!reason.startsWith("사용자")) {
                this.service.alert(1);
            }
            this.service.setStatus(reason);
        }
    }

    public final void tick(final List<UiNode> ns, long now) {
        Object obj;
        Object obj2;
        final UiNode id;
        final UiNode id2;
        Intrinsics.checkNotNullParameter(ns, "ns");
        if (this.paused()) {
            return;
        }
        if (this.active) {
            watchdog(now);
            if (now - this.started >= runLimitMs()) {
                throw new IllegalStateException(("실행 제한 시간(" + (runLimitMs() / 1000) + "초)을 초과했습니다.").toString());
            }
            if (SetsKt.setOf((Object[]) new String[]{"test", "fetch", "collect", "collect_top", "collect_back", "collect_restore"}).contains(this.phase)) {
                obj = "test";
            } else {
                obj = "test";
                if (now - this.entered >= AdvancedSettings.phaseSeconds(this.storage.getPrefs()) * 1000L) {
                    throw new IllegalStateException(("화면 전환 시간이 초과되었습니다: " + this.phase).toString());
                }
            }
            List<UiNode> list = ns;
            Iterator<?> it = list.iterator();
            while (true) {
                if (!it.hasNext()) {
                    obj2 = null;
                    break;
                }
                obj2 = it.next();
                UiNode uiNode = (UiNode) obj2;
                if (Intrinsics.areEqual(uiNode.getId(), "btn_cancel") && Intrinsics.areEqual(uiNode.getText(), "나중에 보기")) {
                    break;
                }
            }
            final UiNode uiNode2 = (UiNode) obj2;
            if (uiNode2 != null) {
                act("notice", now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda16
                    @Override // kotlin.jvm.functions.Function0
                    public final Object invoke() {
                        Unit tick$lambda$20$lambda$19;
                        tick$lambda$20$lambda$19 = BotEngine.tick$lambda$20$lambda$19(BotEngine.this, uiNode2);
                        return tick$lambda$20$lambda$19;
                    }
                });
                return;
            }
            String str = this.phase;
            switch (str.hashCode()) {
                case -1722062292:
                    if (str.equals("current_entry")) {
                        int i = WhenMappings.$EnumSwitchMapping$0[CoreKt.currentEntry(ns).ordinal()];
                        if (i == 1) {
                            throw new IllegalStateException("이미 시작된 테스트입니다. 응시 시작 전 화면에서 시작하세요.".toString());
                        }
                        if (i == 2) {
                            act("back-preview", now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda17
                                @Override // kotlin.jvm.functions.Function0
                                public final Object invoke() {
                                    Unit tick$lambda$21;
                                    tick$lambda$21 = BotEngine.tick$lambda$21(ns, BotEngine.this);
                                    return tick$lambda$21;
                                }
                            });
                            return;
                        }
                        if (i == 3) {
                            CoreKt.validateEntryStructure(ns, CurrentEntry.SET);
                            UiNode id3 = id(ns, "set_name");
                            Intrinsics.checkNotNull(id3);
                            this.name = id3.getText();
                            QuietJson put = new QuietJson().put("name", this.name).put("ignores_saved_day_and_title", true);
                            Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                            log("current_set", put);
                            beginCollection("finish_collect");
                            return;
                        }
                        if (i != 4) {
                            throw new NoWhenBranchMatchedException();
                        }
                        if ((list instanceof Collection) && list.isEmpty()) {
                            return;
                        }
                        Iterator<?> it2 = list.iterator();
                        while (it2.hasNext()) {
                            if (Intrinsics.areEqual(((UiNode) it2.next()).getId(), "txt_set_name")) {
                                throw new IllegalStateException("테스트할 세트의 단어 목록 또는 테스트 시작 전 화면을 먼저 열어 주세요.".toString());
                            }
                        }
                        return;
                    }
                    return;
                case -1573149105:
                    if (str.equals("start_test") && (id = id(ns, "btn_test_start")) != null) {
                        act("start", now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda19
                            @Override // kotlin.jvm.functions.Function0
                            public final Object invoke() {
                                Unit tick$lambda$28$lambda$27;
                                tick$lambda$28$lambda$27 = BotEngine.tick$lambda$28$lambda$27(BotEngine.this, id);
                                return tick$lambda$28$lambda$27;
                            }
                        });
                        return;
                    }
                    return;
                case -1059385223:
                    if (str.equals("collect_restore")) {
                        if ((list instanceof Collection) && list.isEmpty()) {
                            return;
                        }
                        for (UiNode uiNode3 : list) {
                            if (Intrinsics.areEqual(uiNode3.getId(), "txt_front")) {
                                String text = uiNode3.getText();
                                UiNode uiNode4 = this.collectingObserved;
                                if (Intrinsics.areEqual(text, uiNode4 != null ? uiNode4.getText() : null)) {
                                    Bounds bounds = uiNode3.getBounds();
                                    UiNode uiNode5 = this.collectingObserved;
                                    if (Intrinsics.areEqual(bounds, uiNode5 != null ? uiNode5.getBounds() : null)) {
                                        transition("collect", "단어 수집 " + this.collected.size() + "개");
                                        return;
                                    }
                                } else {
                                    continue;
                                }
                            }
                        }
                        return;
                    }
                    return;
                case -265268866:
                    if (str.equals("finish_collect")) {
                        UiNode id4 = id(ns, "set_name");
                        if (!Intrinsics.areEqual(id4 != null ? id4.getText() : null, this.name)) {
                            throw new IllegalStateException("수집 후 세트가 바뀌었습니다.".toString());
                        }
                        finishPreparation();
                        return;
                    }
                    return;
                case 3556498:
                    if (str.equals(obj)) {
                        test(ns, now);
                        return;
                    }
                    return;
                case 100361836:
                    if (str.equals("intro")) {
                        if (id(ns, "btn_test_start") != null) {
                            transition("start_test", "응시 시작");
                            return;
                        } else {
                            if (id(ns, "txt_title") == null || !SwipeGate.ready$default(this.gate, "intro", cardBounds(ns), now, false, 0, 24, null)) {
                                return;
                            }
                            this.service.swipeCard(cardBounds(ns), ns);
                            return;
                        }
                    }
                    return;
                case 949444906:
                    if (str.equals("collect")) {
                        collectFront(ns, now);
                        return;
                    }
                    return;
                case 1546335591:
                    if (str.equals("open_test") && (id2 = id(ns, "ly_bottom_test")) != null) {
                        act("open", now, new Function0() { // from class: net.classcardauto.mobile.BotEngine$$ExternalSyntheticLambda18
                            @Override // kotlin.jvm.functions.Function0
                            public final Object invoke() {
                                Unit tick$lambda$25$lambda$24;
                                tick$lambda$25$lambda$24 = BotEngine.tick$lambda$25$lambda$24(BotEngine.this, id2);
                                return tick$lambda$25$lambda$24;
                            }
                        });
                        return;
                    }
                    return;
                case 1626441436:
                    if (str.equals("collect_back")) {
                        collectBack(ns, now);
                        return;
                    }
                    return;
                case 1853598912:
                    if (str.equals("collect_top")) {
                        collectTop(ns, now);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }
}
