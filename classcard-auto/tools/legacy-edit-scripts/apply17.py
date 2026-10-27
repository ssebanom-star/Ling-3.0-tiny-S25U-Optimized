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

# ---------------- Screens.Home ----------------
sub('Screens.java', 'public enum Link { NO_SERVICE, READY, RUNNING }', 'public enum Link { NO_SERVICE, READY, RUNNING, PAUSED }')
sub('Screens.java', '''        public final LinearLayout banner;
        public final TextView bannerText;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText) {
            this.banner = banner;''', '''        public final LinearLayout banner;
        public final TextView bannerText;
        public final Button pause;
        public final Button resume;

        Home(LinearLayout root, View dot, TextView chip, TextView statusTitle, TextView statusBody, Button primary, Button stop, Button accessibility, LinearLayout banner, TextView bannerText, Button pause, Button resume) {
            this.pause = pause;
            this.resume = resume;
            this.banner = banner;''')
sub('Screens.java', '''        public void render(Link link, String detail) {
            render(link, detail, -1L);
        }''', '''        public void render(Link link, String detail) {
            render(link, detail, -1L, null);
        }

        public void render(Link link, String detail, long remainingSeconds) {
            render(link, detail, remainingSeconds, null);
        }''')
sub('Screens.java', '        public void render(Link link, String detail, long remainingSeconds) {\n            Context c = root.getContext();', '        public void render(Link link, String detail, long remainingSeconds, String note) {\n            Context c = root.getContext();') if False else None
s = read('Screens.java')
a = '''        public void render(Link link, String detail, long remainingSeconds) {
            AppStyle s = AppStyle.INSTANCE;'''
assert a in s
s = s.replace(a, '''        public void render(Link link, String detail, long remainingSeconds, String note) {
            AppStyle s = AppStyle.INSTANCE;''', 1)
# running: note replaces the usual hint; paused: its own copy
a = '''                    statusBody.setText(AppStyle.keepWords("화면의 플로팅 버튼이나 아래 버튼으로 중지할 수 있어요."));
                    break;'''
assert a in s
s = s.replace(a, '''                    statusBody.setText(AppStyle.keepWords(note != null ? note : "화면의 플로팅 버튼이나 아래 버튼으로 일시정지·중지할 수 있어요."));
                    break;
                case PAUSED:
                    dotColor = s.getWarn();
                    chipText = remainingSeconds < 0 ? "일시정지" : "일시정지 · 남은 " + AppStyle.formatClock(remainingSeconds);
                    chipFg = s.getWarn();
                    chipBg = s.getSoftWarn();
                    statusTitle.setText(AppStyle.keepWords(detail == null || detail.isEmpty() ? "일시정지됨" : detail));
                    statusBody.setText(AppStyle.keepWords(note != null ? note : "직접 조작한 뒤 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요."));
                    break;''', 1)
a = '''            boolean running = link == Link.RUNNING;
            boolean ready = link == Link.READY;
            primary.setEnabled(ready);
            primary.setAlpha(ready ? 1.0f : 0.45f);
            primary.setVisibility(running ? View.GONE : View.VISIBLE);
            stop.setVisibility(running ? View.VISIBLE : View.GONE);'''
assert a in s
s = s.replace(a, '''            boolean running = link == Link.RUNNING;
            boolean paused = link == Link.PAUSED;
            boolean ready = link == Link.READY;
            primary.setEnabled(ready);
            primary.setAlpha(ready ? 1.0f : 0.45f);
            primary.setVisibility((running || paused) ? View.GONE : View.VISIBLE);
            pause.setVisibility(running ? View.VISIBLE : View.GONE);
            resume.setVisibility(paused ? View.VISIBLE : View.GONE);
            stop.setVisibility((running || paused) ? View.VISIBLE : View.GONE);''', 1)
a = 'public static Home home(Context c, SharedPreferences prefs, Function0<Unit> onSettings, Function0<Unit> onStart, Function0<Unit> onStop, Function0<Unit> onAccessibility, Function0<Unit> onFailures) {'
assert a in s
s = s.replace(a, 'public static Home home(Context c, SharedPreferences prefs, Function0<Unit> onSettings, Function0<Unit> onStart, Function0<Unit> onStop, Function0<Unit> onAccessibility, Function0<Unit> onFailures, Function0<Unit> onPause, Function0<Unit> onResume) {', 1)
a = '''        Button stop = s.button(c, "실행 중지", AppStyle.Tone.DANGER, onStop);'''
assert a in s
s = s.replace(a, '''        Button resume = s.button(c, "재개", AppStyle.Tone.PRIMARY, onResume);
        resume.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
        resume.getCompoundDrawablesRelative()[0].setTint(-1);
        resume.setCompoundDrawablePadding(dp(c, 8));
        resume.setMinHeight(dp(c, 58));
        resume.setMinimumHeight(dp(c, 58));
        resume.setTextSize(16.0f);
        resume.setVisibility(View.GONE);
        add(content, resume, 20);
        Button pause = s.button(c, "일시정지", AppStyle.Tone.TONAL, onPause);
        pause.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
        pause.getCompoundDrawablesRelative()[0].setTint(s.getAccentDark());
        pause.setCompoundDrawablePadding(dp(c, 8));
        pause.setVisibility(View.GONE);
        add(content, pause, 20);
        Button stop = s.button(c, "실행 중지", AppStyle.Tone.DANGER, onStop);''', 1)
s = s.replace('Home h = new Home(page.root, dot, chip, statusTitle, statusBody, primary, stop, accessibility, banner, bannerText);', 'Home h = new Home(page.root, dot, chip, statusTitle, statusBody, primary, stop, accessibility, banner, bannerText, pause, resume);', 1)
s = s.replace('        add(content, stop, 10);\n\n        // scrolling controls', '        add(content, stop, 10);\n\n        // scrolling controls', 1)
write('Screens.java', s)

# ---------------- MainActivity ----------------
s = read('MainActivity.java')
a = '''        Screens.Link link = companion == null ? Screens.Link.NO_SERVICE : (running ? Screens.Link.RUNNING : Screens.Link.READY);
        if (link == Screens.Link.RUNNING) {
            h.render(link, companion.getStatus(), companion.getEngine().getRemainingSeconds());
        } else {
            h.render(link, null);
        }'''
assert a in s
s = s.replace(a, '''        boolean paused = running && companion.getEngine().isPaused();
        Screens.Link link = companion == null ? Screens.Link.NO_SERVICE : (paused ? Screens.Link.PAUSED : (running ? Screens.Link.RUNNING : Screens.Link.READY));
        if (link == Screens.Link.RUNNING || link == Screens.Link.PAUSED) {
            BotEngine eng = companion.getEngine();
            String note = eng.isInTest() ? "직접 터치 " + eng.getUserTouches() + " / " + eng.getTouchLimit() + "회 · 한도에 닿으면 자동으로 일시정지해요." : null;
            if (link == Screens.Link.PAUSED) {
                note = "직접 터치 " + eng.getUserTouches() + "회. 직접 조작한 뒤 ‘재개’를 누르면 이어서 진행해요. 재개 후 처음 모르는 문제는 대충 찍고, 터치 횟수는 0으로 돌아가요.";
            }
            h.render(link, companion.getStatus(), eng.getRemainingSeconds(), note);
        } else {
            h.render(link, null);
        }''', 1)
a = '''        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        }));
        this.home = h;'''
assert a in s
s = s.replace(a, '''        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showFailures();
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
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
        this.home = h;''', 1)
a = '실행이 멈춘 시각·이유·문항 번호는 개발 참고용으로'
assert a in s
s = s.replace(a, '시험 중에는 사용자가 화면을 직접 터치한 횟수(위치·내용 제외)만 세어 일시정지 판단에 쓰며 저장하지 않습니다. 실행이 멈춘 시각·이유·문항 번호는 개발 참고용으로', 1)
write('MainActivity.java', s)
print('ok')
