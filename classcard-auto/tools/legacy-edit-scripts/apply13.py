import re
exec(open('/tmp/claude-0/work/fix.py').read())

def read(p):
    return open(D + p).read()

def write(p, s):
    open(D + p, 'w').write(s)

def sub(p, a, b, count=1):
    s = read(p)
    assert a in s, (p, a[:110])
    write(p, s.replace(a, b, count))

# ---------------- FailureLog: no question text at all ----------------
s = read('FailureLog.java')
s = s.replace('''        public int cards;
        // detailed (optional)
        public String prompt = "";
        public List<String> choices = new ArrayList<>();
        public List<String> nearest = new ArrayList<>();

        public boolean hasDetail() {
            return !prompt.isEmpty() || !choices.isEmpty() || !nearest.isEmpty();
        }
    }''', '''        public int cards;
    }''')
s = s.replace('''        if (r.hasDetail()) {
            o.put("prompt", r.prompt);
            o.put("choices", new JSONArray(r.choices));
            o.put("nearest", new JSONArray(r.nearest));
        }
        return o;''', '''        return o;''')
a = s.index('        r.prompt = o.optString("prompt", "");')
b = s.index('        return r;\n    }', a)
s = s[:a] + s[b:]
s = s.replace(' * A record always has the reason, time and progress. The text of the failing question (prompt, choices, closest\n * collected words) is only stored when the user has switched on detailed failure records.', ' * A record holds only when and why a run stopped (reason code, message, question number, phase, mode). The text of\n * questions, choices or words is never stored.')
s = s.replace('import org.json.JSONArray;\n', '')
# plain-text report for sending to the developer, and a scrub of any older detailed lines
s = s.replace('''    /** What to try next, by failure code. */''', '''    /** Rewrites the file keeping only the fields above, so lines written by older builds lose any stored question text. */
    public static synchronized void scrub(File root) {
        File f = file(root);
        if (!f.isFile()) {
            return;
        }
        List<Record> all = readAll(root);
        File tmp = new File(root, FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            for (Record r : all) {
                out.write((toJson(r).toString() + "\\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    /** One line per failure, newest first: time | reason | question | phase | mode | message. Contains no question text. */
    public static String report(List<Record> newestFirst) {
        StringBuilder sb = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        for (Record r : newestFirst) {
            sb.append(fmt.format(new java.util.Date(r.time))).append(" | ")
                    .append(r.code).append(" | ")
                    .append(r.progress.isEmpty() ? "-" : r.progress).append(" | ")
                    .append(r.phase.isEmpty() ? "-" : r.phase).append(" | ")
                    .append(r.pace.isEmpty() ? "-" : r.pace).append(" | ")
                    .append(r.message.replace('\\n', ' ')).append('\\n');
        }
        return sb.toString();
    }

    /** What to try next, by failure code. */''')
s = s.replace('수집한 단어장에서 제시어를 찾지 못했어요. 아래에 비슷한 단어가 있다면 화면 인식 오류일 수 있어요. ‘화면 이미지로 읽기’ 설정을 바꿔 다시 수집해 보세요.', '수집한 단어장에서 제시어를 찾지 못했어요. 화면 인식 오류이거나 단어장에 없는 단어일 수 있어요. ‘화면 이미지로 읽기’ 설정을 바꿔 다시 수집해 보세요.')
write('FailureLog.java', s)

# ---------------- Solver: drop nearest() ----------------
s = read('Solver.java')
a = s.index('    /** The collected cards whose word or meaning looks most like')
b = s.index('    public final ChoiceAnswer answer(String prompt, List<UiNode> choices) {')
write('Solver.java', s[:a] + s[b:])

# ---------------- BotEngine: no capture of the question ----------------
s = read('BotEngine.java')
a = s.index('    private FailureLog.Record failureDetail;')
b = s.index('    private final void writeFailureRecord(String reason, AnswerFailure failure) {')
s = s[:a] + s[b:]
s = s.replace('''        FailureLog.Record detail = this.failureDetail;
        if (detail != null && this.storage.getPrefs().getBoolean("failure_detail", false)) {
            record.prompt = detail.prompt;
            record.choices = detail.choices;
            record.nearest = detail.nearest;
        }
        this.failureDetail = null;
''', '')
s = s.replace('final ChoiceAnswer answer = answerOrRecord(solver, prompt, choices);', 'final ChoiceAnswer answer = solver.answer(prompt, choices);')
s = s.replace('\n        this.failureDetail = null;', '')
assert 'failureDetail' not in s and 'answerOrRecord' not in s
write('BotEngine.java', s)

# ---------------- app start: scrub older detailed lines ----------------
sub('BotApplication.java', '        retentionPolicy.removeLegacyData(filesDir);', '        retentionPolicy.removeLegacyData(filesDir);\n        FailureLog.scrub(filesDir);')

# ---------------- Screens ----------------
s = read('Screens.java')
# settings: remove the failure-detail card
a = s.index('        add(content, s.sectionLabel(c, "실패 기록"), 24);')
b = s.index('        add(content, s.sectionLabel(c, "안내 및 기록"), 24);')
s = s[:a] + s[b:]
# failure card: no detail block
a = s.index('        if (r.hasDetail()) {')
b = s.index('        return card;\n    }\n\n    /** The failure history screen.')
s = s[:a] + s[b:]
# failures screen: no detail flag, add copy button
s = s.replace('public static LinearLayout failures(Context c, java.util.List<FailureLog.Record> records, boolean detailOn, Function0<Unit> onBack, Function0<Unit> onClear) {', 'public static LinearLayout failures(Context c, java.util.List<FailureLog.Record> records, Function0<Unit> onBack, Function0<Unit> onCopy, Function0<Unit> onClear) {')
s = s.replace('''            TextView d = s.caption(c, detailOn ? "실행이 멈추면 이유와 문제 글이 여기에 쌓여요." : "실행이 멈추면 이유와 시각이 여기에 쌓여요. 문제 글까지 보려면 설정에서 ‘실패 상세 기록 저장’을 켜세요.");''', '''            TextView d = s.caption(c, "실행이 멈추면 멈춘 시각과 이유가 여기에 쌓여요. 문제나 단어의 글은 저장하지 않아요.");''')
s = s.replace('''        add(content, s.caption(c, "최근 " + records.size() + "건 · 최신순" + (detailOn ? "" : " · 문제 글은 저장 꺼짐")), 8);''', '''        add(content, s.caption(c, "최근 " + records.size() + "건 · 최신순 · 멈춘 시각과 이유만 저장해요"), 8);''')
s = s.replace('''        add(content, s.button(c, "기록 모두 지우기", AppStyle.Tone.DANGER, onClear), 20);''', '''        add(content, s.button(c, "개발자용으로 기록 복사", AppStyle.Tone.TONAL, onCopy), 20);
        add(content, s.button(c, "기록 모두 지우기", AppStyle.Tone.DANGER, onClear), 10);''')
assert 'hasDetail' not in s and 'failure_detail' not in s
write('Screens.java', s)

# ---------------- MainActivity ----------------
s = read('MainActivity.java')
s = s.replace('문항·단어·뜻 원문이나 OCR 이미지는 기본적으로 기록하지 않습니다. 설정의 ‘실패 상세 기록 저장’을 직접 켠 경우에만 실패한 문제의 제시어·보기·비슷한 단어를 이 휴대폰 안에 최대 100건 저장하며 외부로 전송하지 않습니다. 실패 기록 화면에서 언제든 지울 수 있습니다.', '문항·단어·뜻 원문이나 OCR 이미지는 기록하지 않습니다. 실행이 멈춘 시각·이유·문항 번호는 개발 참고용으로 이 휴대폰 안에 최대 100건 저장하며 외부로 전송하지 않습니다. 실패 기록 화면에서 언제든 지울 수 있습니다.')
s = s.replace('''        boolean detailOn = this.storage.getPrefs().getBoolean("failure_detail", false);
        setContentView(Screens.failures(this, records, detailOn, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, guard(new Function0<Unit>() {''', '''        setContentView(Screens.failures(this, records, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("실패 기록", FailureLog.report(records));
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {''')
assert 'failure_detail' not in s and 'detailOn' not in s
write('MainActivity.java', s)
print('ok')
