import re
exec(open('/tmp/claude-0/work/fix.py').read())
def sub(p,a,b,count=0):
    s=open(D+p).read(); assert a in s,(p,a[:90]); s=s.replace(a,b) if not count else s.replace(a,b,count); open(D+p,'w').write(s)

replace(D+'CollectionCursor.java','public final void observe(List<UiNode> nodes, boolean moved)','''    public final void observe(List<UiNode> nodes, boolean moved) {
        Intrinsics.checkNotNullParameter(nodes, "nodes");
        if (nodes.isEmpty()) {
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (UiNode node : nodes) {
            indices.add(node.getCollectionIndex());
        }
        if (!this.rejected) {
            boolean allIndexed = true;
            for (Integer num : indices) {
                if (num == null || num.intValue() < 0) {
                    allIndexed = false;
                    break;
                }
            }
            if (allIndexed) {
                boolean increasing = true;
                for (int i = 1; i < indices.size(); i++) {
                    if (indices.get(i).intValue() <= indices.get(i - 1).intValue()) {
                        increasing = false;
                        break;
                    }
                }
                if (!increasing) {
                    this.rejected = true;
                    this.indexed = false;
                    observeOverlap(nodes, moved);
                    return;
                }
                for (UiNode node : nodes) {
                    String previous = this.observed.get(node.getCollectionIndex());
                    if (previous != null && !Intrinsics.areEqual(previous, node.getText())) {
                        this.rejected = true;
                        this.indexed = false;
                        observeOverlap(nodes, moved);
                        return;
                    }
                }
                for (UiNode node : nodes) {
                    LinkedHashMap<Integer, String> linkedHashMap = this.observed;
                    Integer collectionIndex = node.getCollectionIndex();
                    Intrinsics.checkNotNull(collectionIndex);
                    linkedHashMap.put(collectionIndex, node.getText());
                }
                if (!Intrinsics.areEqual(this.strategy, "item-index")) {
                    this.last = null;
                }
                this.indexed = true;
                this.strategy = "item-index";
                return;
            }
        }
        this.indexed = false;
        observeOverlap(nodes, moved);
    }''')
sub('CollectionCursor.java','ArrayList emptyList;','List emptyList;')

replace(D+'CertificateVerifier.java','private static final String verify$field(JSONObject jSONObject, String str)','''    private static final String verify$field(JSONObject jSONObject, String str) throws org.json.JSONException {
        String string = jSONObject.getString(str);
        Intrinsics.checkNotNull(string);
        if (!StringsKt.isBlank(string) && string.length() <= 256) {
            boolean clean = true;
            for (int i = 0; i < string.length(); i++) {
                if (Character.isISOControl(string.charAt(i))) {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                return string;
            }
        }
        throw new IllegalArgumentException("Failed requirement.".toString());
    }''')
sub('CertificateVerifier.java','this(null, 0 == true ? 1 : 0, 3, 0 == true ? 1 : 0);','this(CertificationIssuer.PUBLIC_KEY, CertificationIssuer.MACHINE_ID);')
sub('Certification.java','new CertificateVerifier(null, 0 == true ? 1 : 0, 3, 0 == true ? 1 : 0)','new CertificateVerifier()')
replace(D+'Certification.java','public final void refresh()','''    public final void refresh() {
        if (!getRequired()) {
            document = null;
            return;
        }
        try {
            AtomicFile atomicFile = new AtomicFile(certificateFile());
            CertificateVerifier certificateVerifier = new CertificateVerifier();
            byte[] readFully = atomicFile.readFully();
            Intrinsics.checkNotNullExpressionValue(readFully, "readFully(...)");
            document = certificateVerifier.verify(new String(readFully, Charsets.UTF_8), deviceId(), targetPackage);
        } catch (Throwable th) {
            document = null;
        }
    }''')
replace(D+'UsageConsent.java','public final ConsentRecord record()','''    public final ConsentRecord record() {
        SharedPreferences sharedPreferences = prefs;
        if (sharedPreferences == null) {
            return null;
        }
        try {
            if (!Intrinsics.areEqual(sharedPreferences.getString("policy_version", ""), POLICY_VERSION)) {
                throw new IllegalStateException("Check failed.");
            }
            String string = sharedPreferences.getString("policy_digest", "");
            if (string == null) {
                string = "";
            }
            ConsentRecord consentRecord = new ConsentRecord(string, sharedPreferences.getLong("accepted_at", 0L), sharedPreferences.getInt("steps", 0));
            if (!consentRecord.validFor(getPolicyDigest())) {
                return null;
            }
            return consentRecord;
        } catch (Throwable th) {
            return null;
        }
    }''')
# comparators
for f in ['BotEngine.java','InlineCollectionKt.java']:
    sub(f,'compare(T t, T t2)','compare(Object t, Object t2)')
sub('InlineCollectionKt.java','for (UiNode uiNode : CollectionsKt.sortedWith(fronts,','for (UiNode uiNode : (List<UiNode>) CollectionsKt.sortedWith(fronts,')
sub('BotEngine.java','botEngine.pending = new Pending(str, str2, choiceAnswer, SystemClock.elapsedRealtime(), r4 - j, i);','''long now = SystemClock.elapsedRealtime();
        botEngine.pending = new Pending(str, str2, choiceAnswer, now, now - j, i);''')
sub('BotEngine.java','''            for (Object obj2 : list2) {
                int i4 = r12 + 1;
                if (r12 < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                InlinePair inlinePair = (InlinePair) obj2;
                int i5 = r12 * 2;''','''            int r12 = 0;
            for (Object obj2 : list2) {
                int i4 = r12 + 1;
                if (r12 < 0) {
                    CollectionsKt.throwIndexOverflow();
                }
                InlinePair inlinePair = (InlinePair) obj2;
                int i5 = r12 * 2;''')
sub('BotEngine.java','CollectionsKt.maxOrNull((Iterable<? extends Object>) this.tapTimes)','CollectionsKt.maxOrNull((Iterable<Double>) this.tapTimes)')
sub('BotEngine.java','tick$lambda$21(ns, this)','tick$lambda$21(ns, BotEngine.this)')
sub('UiProfile.java','''        Sequence<String> asSequence = SequencesKt.asSequence(keys);
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        for (String str : asSequence) {
            Pair pair = TuplesKt.to(jSONObject.getString(str), str);
            linkedHashMap.put(pair.getFirst(), pair.getSecond());
        }''','''        LinkedHashMap linkedHashMap = new LinkedHashMap();
        while (keys.hasNext()) {
            String str = keys.next();
            linkedHashMap.put(jSONObject.getString(str), str);
        }''')
sub('TextRules.java','return fold;','return (Function1<String, String>) (Function1) fold;')
sub('CoreKt.java','(r6 + card.getBottom()) / 2.0f','((num2 != null ? num2.intValue() : card.getTop()) + card.getBottom()) / 2.0f')
s=open(D+'RetentionPolicy.java').read()
m=re.search(r'SetsKt\.setOf\(\(Object\[\]\) (new String\[\]\{[^}]*\})\)',s)
s=s.replace(m.group(0),'new java.util.LinkedHashSet<String>(java.util.Arrays.asList('+m.group(1)+'))',1)
open(D+'RetentionPolicy.java','w').write(s)
print('ok')
