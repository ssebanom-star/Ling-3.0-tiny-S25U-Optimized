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

# clock formatting
sub('AppStyle.java', '    /** Values stored in milliseconds are shown in seconds', '''    /** 572 -> "9:32", 3725 -> "1:02:05". */
    public static String formatClock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    /** Values stored in milliseconds are shown in seconds''')

# engine: time left before the run limit stops the run
sub('BotEngine.java', '    private final long runLimitMs() {', '''    /** Seconds until the run limit stops this run, or -1 when nothing is running. */
    public final long getRemainingSeconds() {
        if (!this.active) {
            return -1L;
        }
        long left = (this.started + runLimitMs()) - SystemClock.elapsedRealtime();
        return Math.max(0L, left / 1000L);
    }

    private final long runLimitMs() {''')

# ticker: keep the floating panel's clock current
sub('BotService.java', '''                } else if (BotService.this.getEngine().getActive()) {
                    long elapsedRealtime = SystemClock.elapsedRealtime();''', '''                } else if (BotService.this.getEngine().getActive()) {
                    FloatingPanel clockPanel = BotService.this.floating;
                    if (clockPanel != null) {
                        clockPanel.setRemaining(BotService.this.getEngine().getRemainingSeconds());
                    }
                    long elapsedRealtime = SystemClock.elapsedRealtime();''')

# floating panel
s = read('FloatingPanel.java')
s = s.replace('    private final TextView settingsLabel;\n', '''    private final TextView settingsLabel;
    private boolean running;
    private long remainingSeconds = -1;
''', 1)
s = s.replace('''    public TextView getStatusText() {''', '''    /** Seconds left before the run limit; shown next to "실행 중" and turns amber in the last minute. */
    public void setRemaining(long seconds) {
        if (seconds == remainingSeconds) {
            return;
        }
        remainingSeconds = seconds;
        if (running) {
            updateCaption();
        }
    }

    private void updateCaption() {
        AppStyle s = AppStyle.INSTANCE;
        if (!running) {
            caption.setText("단어 도우미");
            caption.setTextColor(ON_PANEL_DIM);
            return;
        }
        if (remainingSeconds < 0) {
            caption.setText("실행 중");
            caption.setTextColor(ON_PANEL_DIM);
            return;
        }
        caption.setText("실행 중 · 남은 " + AppStyle.formatClock(remainingSeconds));
        caption.setTextColor(remainingSeconds <= 60 ? Color.rgb(255, 196, 92) : ON_PANEL_DIM);
    }

    public TextView getStatusText() {''', 1)
s = s.replace('        caption.setText(active ? "실행 중" : "단어 도우미");\n', '        running = active;\n        updateCaption();\n', 1)
write('FloatingPanel.java', s)

# home status card
s = read('Screens.java')
s = s.replace('''        /** Updates the status card and which buttons are usable. {@code detail} is the live status while running. */
        public void render(Link link, String detail) {''', '''        public void render(Link link, String detail) {
            render(link, detail, -1L);
        }

        /**
         * Updates the status card and which buttons are usable. {@code detail} is the live status while running and
         * {@code remainingSeconds} the time left before the run limit (-1 = unknown).
         */
        public void render(Link link, String detail, long remainingSeconds) {''', 1)
s = s.replace('''                    chipText = "실행 중";
                    chipFg = s.getInfo();
                    chipBg = s.getSoftInfo();''', '''                    chipText = remainingSeconds < 0 ? "실행 중" : "실행 중 · 남은 " + AppStyle.formatClock(remainingSeconds);
                    chipFg = s.getInfo();
                    chipBg = s.getSoftInfo();
                    if (remainingSeconds >= 0 && remainingSeconds <= 60) {
                        chipFg = s.getWarn();
                        chipBg = s.getSoftWarn();
                    }''', 1)
write('Screens.java', s)

# main activity feeds the remaining time
sub('MainActivity.java', '''        if (link == Screens.Link.RUNNING) {
            h.render(link, companion.getStatus());''', '''        if (link == Screens.Link.RUNNING) {
            h.render(link, companion.getStatus(), companion.getEngine().getRemainingSeconds());''')
print('ok')
