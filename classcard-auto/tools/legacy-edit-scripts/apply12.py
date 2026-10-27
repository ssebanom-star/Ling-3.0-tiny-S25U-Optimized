import re
exec(open('/tmp/claude-0/work/fix.py').read())
M = D + 'MainActivity.java'
s = open(M).read()

def sub(a, b, count=1):
    global s
    assert a in s, a[:100]
    s = s.replace(a, b, count)

# home: pass the failures callback, keep the link to update the banner on transitions
sub('''        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$30(MainActivity.this);
            }
        }));
        this.home = h;''', '''        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$30(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        }));
        this.home = h;
        this.lastLink = null;''')

# refreshStatus: update banner when the link state changes
sub('''        if (companion == null) {
            h.render(Screens.Link.NO_SERVICE, null);
        } else if (running) {
            h.render(Screens.Link.RUNNING, companion.getStatus());
        } else {
            h.render(Screens.Link.READY, null);
        }''', '''        Screens.Link link = companion == null ? Screens.Link.NO_SERVICE : (running ? Screens.Link.RUNNING : Screens.Link.READY);
        if (link == Screens.Link.RUNNING) {
            h.render(link, companion.getStatus());
        } else {
            h.render(link, null);
        }
        if (link != this.lastLink) {
            this.lastLink = link;
            updateFailureBanner(h, link);
        }''')
sub('    private boolean inSettings;', '''    private boolean inSettings;
    private Screens.Link lastLink;''')

# settings nav: failure log first
sub('''        help.add(new Screens.Nav("접근성 설정",''', '''        help.add(new Screens.Nav("실패 기록", "멈춘 이유와 시각 확인", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        })));
        help.add(new Screens.Nav("접근성 설정",''')

# privacy text
sub('문항·단어·뜻 원문이나 OCR 이미지는 기록하지 않습니다.', '문항·단어·뜻 원문이나 OCR 이미지는 기본적으로 기록하지 않습니다. 설정의 ‘실패 상세 기록 저장’을 직접 켠 경우에만 실패한 문제의 제시어·보기·비슷한 단어를 이 휴대폰 안에 최대 100건 저장하며 외부로 전송하지 않습니다. 실패 기록 화면에서 언제든 지울 수 있습니다.')

# new screen + banner helper
sub('    private final void showSettings() {', '''    private final void updateFailureBanner(Screens.Home h, Screens.Link link) {
        String text = null;
        if (link != Screens.Link.RUNNING && this.storage != null) {
            java.util.List<FailureLog.Record> all = FailureLog.newestFirst(this.storage.getRoot());
            if (!all.isEmpty()) {
                FailureLog.Record last = all.get(0);
                if (System.currentTimeMillis() - last.time < 10 * 60 * 1000L) {
                    text = "방금 실행이 멈췄어요 · " + FailureLog.label(last.code);
                }
            }
        }
        h.showFailure(text);
    }

    private final void showFailures() {
        this.inSettings = true;
        this.home = null;
        final java.util.List<FailureLog.Record> records = FailureLog.newestFirst(this.storage.getRoot());
        boolean detailOn = this.storage.getPrefs().getBoolean("failure_detail", false);
        setContentView(Screens.failures(this, records, detailOn, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.confirmClearFailures();
                return Unit.INSTANCE;
            }
        })));
    }

    private final void confirmClearFailures() {
        Dialog dialog = AppStyle.INSTANCE.sheet(this, "기록을 모두 지울까요?", Screens.textBody(this, "저장된 실패 기록이 이 휴대폰에서 삭제되고 되돌릴 수 없어요."), "지우기", new Runnable() {
            @Override
            public void run() {
                FailureLog.clear(MainActivity.this.storage.getRoot());
                MainActivity.this.showFailures();
            }
        }, "취소", null);
        this.messageDialog = dialog;
        dialog.show();
    }

    private final void showSettings() {''')

open(M, 'w').write(s)
print('ok')
