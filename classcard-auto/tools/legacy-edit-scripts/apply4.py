exec(open('/tmp/claude-0/work/fix.py').read())
B=D+'BotEngine.java'
replace(B,'public static final Unit readInlinePage$lambda$99(BotEngine botEngine, int i, List list, List list2, Object result)','''    public static final Unit readInlinePage$lambda$99(BotEngine botEngine, int i, List list, List list2, Object result) {
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
    }''')
print('ok')
