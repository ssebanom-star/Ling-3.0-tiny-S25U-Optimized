import re
exec(open('/tmp/claude-0/work/fix.py').read())

def sub(p, a, b, count=1):
    s = open(D + p).read()
    assert a in s, (p, a[:100])
    s = s.replace(a, b, count)
    open(D + p, 'w').write(s)

# ---------------- Screens: home banner ----------------
sub('Screens.java', '''        public final Button accessibility;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility) {
            this.root = root;''', '''        public final Button accessibility;
        public final LinearLayout banner;
        public final TextView bannerText;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText) {
            this.banner = banner;
            this.bannerText = bannerText;
            this.root = root;''')
sub('Screens.java', '''        /** Updates the status card and which buttons are usable.''', '''        /** Shows the "last run stopped" notice above the status card; pass null to hide it. */
        public void showFailure(String text) {
            if (text == null) {
                banner.setVisibility(View.GONE);
                return;
            }
            bannerText.setText(AppStyle.keepWords(text));
            banner.setVisibility(View.VISIBLE);
        }

        /** Updates the status card and which buttons are usable.''')
sub('Screens.java', 'public static Home home(Context c, SharedPreferences prefs, Function0<Unit> onSettings, Function0<Unit> onStart, Function0<Unit> onStop, Function0<Unit> onAccessibility) {',
    'public static Home home(Context c, SharedPreferences prefs, Function0<Unit> onSettings, Function0<Unit> onStart, Function0<Unit> onStop, Function0<Unit> onAccessibility, Function0<Unit> onFailures) {')
sub('Screens.java', '''        // status card
        LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);''', '''        // notice about the previous run (hidden until MainActivity finds a recent failure)
        LinearLayout banner = new LinearLayout(c);
        banner.setOrientation(0);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(s.roundRect(c, s.getSoftDanger(), 16, 0));
        banner.setPadding(dp(c, 16), dp(c, 8), dp(c, 8), dp(c, 8));
        TextView bannerText = s.label(c, "", 14.0f, s.getDanger(), true);
        banner.addView(bannerText, new LinearLayout.LayoutParams(0, -2, 1.0f));
        Button bannerButton = s.button(c, "보기", AppStyle.Tone.TEXT, onFailures);
        bannerButton.setTextColor(s.getDanger());
        bannerButton.setMinHeight(dp(c, 40));
        bannerButton.setMinimumHeight(dp(c, 40));
        banner.addView(bannerButton, new LinearLayout.LayoutParams(-2, -2));
        banner.setVisibility(View.GONE);
        add(content, banner, 18);

        // status card
        LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);''')
sub('Screens.java', 'Home h = new Home(page.root, dot, chip, statusTitle, statusBody, primary, stop, accessibility);', 'Home h = new Home(page.root, dot, chip, statusTitle, statusBody, primary, stop, accessibility, banner, bannerText);')
sub('Screens.java', 'add(card, s.caption(c, "자료는 이번 실행에만 사용하고 끝나면 기기에 남기지 않습니다."), 14);', 'add(card, s.caption(c, "수집한 단어장은 이번 실행에만 사용하고 끝나면 지웁니다."), 14);')

# ---------------- Screens: settings extras (detail switch) ----------------
sub('Screens.java', '''        add(content, s.sectionLabel(c, "안내 및 기록"), 24);''', '''        add(content, s.sectionLabel(c, "실패 기록"), 24);
        LinearLayout failureCard = s.card(c);
        failureCard.addView(s.switchRow(c, "실패 상세 기록 저장", "켜면 실패한 문제의 글(제시어·보기·비슷한 단어)을 이 휴대폰 안에만 최대 100건 저장해요. 끄면 이유와 시각만 남아요. 다음 실행부터 적용됩니다.", p.getBoolean("failure_detail", false), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                p.edit().putBoolean("failure_detail", checked).apply();
            }
        }));
        add(content, failureCard, 8);

        add(content, s.sectionLabel(c, "안내 및 기록"), 24);''')

# ---------------- Screens: failure log screen ----------------
sub('Screens.java', '''    public static final class Activation {''', '''    private static String formatTime(long millis) {
        return new java.text.SimpleDateFormat("M월 d일 HH:mm:ss", java.util.Locale.KOREA).format(new java.util.Date(millis));
    }

    private static int codeColor(String code) {
        AppStyle s = AppStyle.INSTANCE;
        switch (code) {
            case "PROMPT_NOT_FOUND":
            case "CHOICE_NOT_FOUND":
                return s.getDanger();
            case "UNCLASSIFIED":
                return s.getWarn();
            default:
                return s.getInfo();
        }
    }

    private static int codeSoft(String code) {
        AppStyle s = AppStyle.INSTANCE;
        switch (code) {
            case "PROMPT_NOT_FOUND":
            case "CHOICE_NOT_FOUND":
                return s.getSoftDanger();
            case "UNCLASSIFIED":
                return s.getSoftWarn();
            default:
                return s.getSoftInfo();
        }
    }

    /** One failure as a card: reason chip + time, explanation, progress and, when stored, the question's text. */
    public static LinearLayout failureCard(Context c, FailureLog.Record r) {
        AppStyle s = AppStyle.INSTANCE;
        LinearLayout card = s.card(c);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(0);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(s.chip(c, FailureLog.label(r.code), codeColor(r.code), codeSoft(r.code)));
        View spacer = new View(c);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));
        head.addView(s.caption(c, formatTime(r.time)));
        card.addView(head);

        String meta = r.progress.isEmpty() ? "" : "문항 " + r.progress.replace(" | ", " / ");
        if (r.cards > 0) {
            meta += (meta.isEmpty() ? "" : "  ·  ") + "수집한 단어 " + r.cards + "개";
        }
        if (!r.pace.isEmpty()) {
            meta += (meta.isEmpty() ? "" : "  ·  ") + (r.pace.equals("human") ? "사람처럼" : r.pace.equals("natural") ? "자연 모드" : "빠른 모드");
        }
        if (!meta.isEmpty()) {
            add(card, s.label(c, meta, 15.0f, s.getInk(), true), 12);
        }
        add(card, s.caption(c, FailureLog.hint(r.code)), 6);

        if (r.hasDetail()) {
            add(card, s.divider(c), 14);
            if (!r.prompt.isEmpty()) {
                add(card, s.sectionLabel(c, "제시어"), 14);
                add(card, codeBox(c, r.prompt, 14.0f), 6);
            }
            if (!r.choices.isEmpty()) {
                add(card, s.sectionLabel(c, "보기"), 14);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.choices.size(); i++) {
                    if (i > 0) {
                        sb.append('\\n');
                    }
                    sb.append(i + 1).append(". ").append(r.choices.get(i));
                }
                add(card, codeBox(c, sb.toString(), 13.0f), 6);
            }
            if (!r.nearest.isEmpty()) {
                add(card, s.sectionLabel(c, "단어장에서 가장 비슷한 단어"), 14);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.nearest.size(); i++) {
                    if (i > 0) {
                        sb.append('\\n');
                    }
                    sb.append(r.nearest.get(i));
                }
                add(card, codeBox(c, sb.toString(), 13.0f), 6);
            }
        } else {
            add(card, s.caption(c, "문제 글은 저장하지 않았어요. 설정에서 ‘실패 상세 기록 저장’을 켜면 다음부터 보여요."), 10);
        }
        return card;
    }

    /** The failure history screen. Records are given newest first. */
    public static LinearLayout failures(Context c, java.util.List<FailureLog.Record> records, boolean detailOn, Function0<Unit> onBack, Function0<Unit> onClear) {
        AppStyle s = AppStyle.INSTANCE;
        Page page = page(c, titleBar(c, "실패 기록", onBack));
        LinearLayout content = page.content;
        if (records.isEmpty()) {
            LinearLayout empty = s.card(c);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(s.icon(c, R.drawable.ic_check, s.getAccent(), 36));
            TextView t = s.title(c, "기록된 실패가 없어요");
            t.setGravity(Gravity.CENTER);
            add(empty, t, 12);
            TextView d = s.caption(c, detailOn ? "실행이 멈추면 이유와 문제 글이 여기에 쌓여요." : "실행이 멈추면 이유와 시각이 여기에 쌓여요. 문제 글까지 보려면 설정에서 ‘실패 상세 기록 저장’을 켜세요.");
            d.setGravity(Gravity.CENTER);
            add(empty, d, 8);
            add(content, empty, 16);
            return page.root;
        }
        add(content, s.caption(c, "최근 " + records.size() + "건 · 최신순" + (detailOn ? "" : " · 문제 글은 저장 꺼짐")), 8);
        for (FailureLog.Record r : records) {
            add(content, failureCard(c, r), 12);
        }
        add(content, s.button(c, "기록 모두 지우기", AppStyle.Tone.DANGER, onClear), 20);
        return page.root;
    }

    public static final class Activation {''')
print('ok')
