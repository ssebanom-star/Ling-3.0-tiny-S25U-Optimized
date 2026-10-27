exec(open('/tmp/claude-0/work/fix.py').read())
B=D+'BotEngine.java'
replace(B,'public static final Unit readCollectionNode$lambda$105(final BotEngine botEngine, int i,', '''    public static final Unit readCollectionNode$lambda$105(final BotEngine botEngine, int i, String str, UiNode uiNode, Function1 function1, Object result) {
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
                JSONObject put = new JSONObject().put("side", str).put("word", text).put("recognized", read.getText()).put("reason", "glyph_touches_image_edge");
                Intrinsics.checkNotNullExpressionValue(put, "put(...)");
                botEngine.log("collection_clipped", put);
                if (Intrinsics.areEqual(str, "back")) {
                    botEngine.service.tap(uiNode, new Function0() {
                        @Override
                        public final Object invoke() {
                            return BotEngine.readCollectionNode$lambda$105$lambda$100(BotEngine.this);
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
                    JSONObject put4 = new JSONObject().put("side", str).put("recognized", obj).put("screen_text", uiNode.getText()).put("used_screen_text", usedScreenText).put("ocr_mode", "qota-single-model").put("ocr_model", OcrReader.MODEL).put("confidence", read.getConfidence()).put("inference_ms", read.getInferenceMs());
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
            JSONObject put5 = new JSONObject().put("side", str).put("reason", "screen_moved").put("attempt", intValue);
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
    }''')
replace(B,'private final void readInlinePage(final List<InlinePair> pairs, long now)','''    private final void readInlinePage(final List<InlinePair> pairs, long now) {
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
        JSONObject put = new JSONObject().put("pairs", pairs.size()).put("used_screen_text", true);
        Intrinsics.checkNotNullExpressionValue(put, "put(...)");
        log("inline_batch", put);
    }''')
replace(B,'private final void writeSummary(String status, String reason, String score, AnswerFailure failure)','''    private final void writeSummary(String status, String reason, String score, AnswerFailure failure) {
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
        Object maxOrNull = CollectionsKt.maxOrNull((Iterable<? extends Object>) this.tapTimes);
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
        JSONObject summary = new JSONObject().put("status", status).put("reason", StringsKt.isBlank(reason) ? "" : "실행 중지 · 자료 삭제").put("score", score == null ? JSONObject.NULL : score).put("set_name", this.name).put("set_code", this.code).put("answers", this.answered.size()).put("expected_questions", this.expectedQuestions).put("runtime", "android-accessibility").put("requires_adb", false).put("requires_pc", false).put("pace", this.natural ? "natural" : "fast").put("total_seconds", (elapsedRealtime - this.started) / 1000.0d).put("test_seconds", testSeconds).put("first_prompt_to_last_tap_seconds", firstToLast).put("read_to_tap_median_ms", median).put("read_to_tap_max_ms", maxOrNull).put("run_directory", this.run != null ? this.run.getName() : null).put("finished_at", Instant.now().toString()).put("mode", this.mode).put("source", source).put("error_code", errorCode).put("last_progress", this.lastProgress).put("collected_cards", this.collected.size()).put("ocr_reads", this.ocrReads).put("inline_pairs", this.inlinePairCount).put("ocr_captures", this.ocrCaptures).put("collection_mode", getInlineCollection() ? "inline" : "flip").put("collection_indexed", this.collectionCursor.getIndexed()).put("collection_reused_rows", this.collectionReusedRows).put("collection_strategy", this.collectionCursor.getStrategy()).put("ocr_mode", "qota-single-model").put("ocr_model", OcrReader.MODEL).put("ocr_only", this.ocrOnly);
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
        File runDir = this.run;
        if (runDir != null) {
            FilesKt.writeText$default(new File(runDir, "summary.json"), summary.toString(2), null, 2, null);
        }
        FilesKt.writeText$default(new File(this.storage.getRoot(), "last-summary.json"), summary.toString(2), null, 2, null);
    }''')
# lambda signatures use Result -> Object
s=open(B).read()
s=s.replace('(Result) obj)','obj)').replace('List list2, Result result)','List list2, Object result)')
s=s.replace('''        Object value = result.getValue();
        if (Result.m17isFailureimpl(value)) {
            value = null;
        }
        List list3 = (List) value;''','''        List list3 = (List) ResultCompat.valueOrNull(result);''')
open(B,'w').write(s)
# UiProfile
U=D+'UiProfile.java'
replace(U,'public UiProfile(Context context)','''    public UiProfile(Context context) {
        this(load(context));
    }

    private static JSONObject load(Context context) {
        try {
            InputStream open = context.getAssets().open("classcard-profile.json");
            Intrinsics.checkNotNullExpressionValue(open, "open(...)");
            Reader inputStreamReader = new InputStreamReader(open, Charsets.UTF_8);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                String readText = TextStreamsKt.readText(bufferedReader);
                CloseableKt.closeFinally(bufferedReader, null);
                return new JSONObject(readText);
            } catch (Throwable th) {
                CloseableKt.closeFinally(bufferedReader, th);
                throw th;
            }
        } catch (java.io.IOException | org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
    }''')
# MainActivity
M=D+'MainActivity.java'
s=open(M).read()
s=s.replace('''            String optString = jSONObject.optString("score");
            if (!Intrinsics.areEqual(optString, "null")) {
                Intrinsics.checkNotNull(optString);
            }
            optString = null;''','''            String optString = jSONObject.optString("score");
            if (Intrinsics.areEqual(optString, "null") || StringsKt.isBlank(optString)) {
                optString = null;
            }''')
open(M,'w').write(s)
# CertificationReceiver stub
open(D+'CertificationReceiver.java','w').write('''package net.classcardauto.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Disabled in the manifest of this build (android:enabled="false"). The original onReceive could not be
 * decompiled; it is intentionally a no-op here.
 */
public final class CertificationReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
    }
}
''')
print('done')
