import re
exec(open('/tmp/claude-0/work/fix.py').read())

def read(p):
    return open(D + p).read()

def write(p, s):
    open(D + p, 'w').write(s)

# ---------------- Screens: no pause/resume buttons on the home screen ----------------
s = read('Screens.java')
s = s.replace('''        public final Button pause;
        public final Button resume;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText, Button pause, Button resume) {
            this.pause = pause;
            this.resume = resume;
            this.banner = banner;''', '''
        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText) {
            this.banner = banner;''', 1)
s = s.replace('''            pause.setVisibility(running ? View.VISIBLE : View.GONE);
            resume.setVisibility(paused ? View.VISIBLE : View.GONE);
''', '', 1)
s = s.replace(', Function0<Unit> onFailures, Function0<Unit> onPause, Function0<Unit> onResume) {', ', Function0<Unit> onFailures) {', 1)
a = s.index('        Button resume = s.button(c, "재개", AppStyle.Tone.PRIMARY, onResume);')
b = s.index('        Button stop = s.button(c, "실행 중지", AppStyle.Tone.DANGER, onStop);')
s = s[:a] + s[b:]
s = s.replace('accessibility, banner, bannerText, pause, resume);', 'accessibility, banner, bannerText);', 1)
# the paused copy points at the floating panel, where the resume button is
s = s.replace('"직접 조작한 뒤 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요."', '"클래스카드에서 직접 조작한 뒤 화면의 플로팅 버튼 ‘재개’를 누르면 이어서 진행해요. 앱 화면으로 돌아올 필요는 없어요."', 1)
s = s.replace('"화면의 플로팅 버튼이나 아래 버튼으로 일시정지·중지할 수 있어요."', '"일시정지와 재개는 클래스카드 위의 플로팅 버튼에서 해요. 중지는 아래 버튼으로도 할 수 있어요."', 1)
assert 'onPause' not in s and 'onResume' not in s
write('Screens.java', s)

# ---------------- MainActivity ----------------
s = read('MainActivity.java')
a = '''        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                BotService service = BotService.INSTANCE.getInstance();
                if (service != null) {
                    service.getEngine().pause("직접 일시정지");
                }
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                BotService service = BotService.INSTANCE.getInstance();
                if (service != null) {
                    service.getEngine().resume();
                }
                return Unit.INSTANCE;
            }
        }));
        this.home = h;'''
assert a in s
s = s.replace(a, '''        }));
        this.home = h;''', 1)
s = s.replace('note = "직접 터치 " + eng.getUserTouches() + "회. 직접 조작한 뒤 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요.";', 'note = "직접 터치 " + eng.getUserTouches() + "회. 클래스카드에서 직접 조작한 뒤 화면의 플로팅 버튼 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요.";', 1)
write('MainActivity.java', s)

# ---------------- BotService: the panel always comes back while paused; resume does not look like a lost screen ----------------
s = read('BotService.java')
a = '''        if (!active) {
            // 실행이 끝나면 꺼 두었던 UI를 다시 쓸 수 있게 되돌린다.
            this.overlayDismissed = false;
        }'''
assert a in s
s = s.replace(a, '''        if (!active || getEngine().isPaused()) {
            // 실행이 끝나면 꺼 두었던 UI를 다시 쓸 수 있게 되돌린다.
            // 일시정지 중에는 재개 버튼이 필요하므로 패널을 항상 보여 준다(앱 화면으로 돌아오지 않아도 되게).
            this.overlayDismissed = false;
        }''', 1)
s = s.replace('''    public final void back() {''', '''    /** The ClassCard screen counts as "seen now"; used after a pause so a stale timestamp cannot stop the run. */
    public final void markScreenSeen() {
        this.lastActive = SystemClock.elapsedRealtime();
    }

    public final void back() {''', 1)
write('BotService.java', s)

# ---------------- BotEngine.resume: refresh the seen-screen clock ----------------
s = read('BotEngine.java')
a = '        this.intervened = true;\n        this.guessNext = true;'
assert a in s
s = s.replace(a, '        this.service.markScreenSeen();\n' + a, 1)
write('BotEngine.java', s)
print('ok')
