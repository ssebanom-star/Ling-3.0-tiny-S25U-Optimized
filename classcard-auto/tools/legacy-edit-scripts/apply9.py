import re
exec(open('/tmp/claude-0/work/fix.py').read())
M = D + 'MainActivity.java'
s = open(M).read()

# fields
s = s.replace('    private boolean inSettings;', '''    private boolean inSettings;
    private Screens.Activation activation;
    private final Runnable activationPoll = new Runnable() {
        @Override
        public void run() {
            MainActivity.this.pollActivation();
        }
    };''', 1)
open(M, 'w').write(s)

replace(M, 'private final void showUncertified()', '''    private final void showUncertified() {
        this.status = null;
        this.primary = null;
        this.stopButton = null;
        this.accessibilityButton = null;
        this.home = null;
        getWindow().setStatusBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().setNavigationBarColor(AppStyle.INSTANCE.getBackground());
        getWindow().getDecorView().setSystemUiVisibility(8208);
        final String code = Certification.INSTANCE.deviceId();
        final String folder = Certification.INSTANCE.dropPathLabel();
        Screens.Activation a = Screens.activation(this, code, folder, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.checkActivation(true);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("기기 코드", code);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.copyText("폴더 경로", folder);
                return Unit.INSTANCE;
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.closeApp();
                return Unit.INSTANCE;
            }
        }));
        this.activation = a;
        setContentView(a.root);
        this.handler.removeCallbacks(this.activationPoll);
        this.handler.postDelayed(this.activationPoll, 2000L);
    }

    private final void copyText(String label, String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService("clipboard");
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
            android.widget.Toast.makeText(this, label + "를 복사했어요", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private final void pollActivation() {
        if (this.activation == null) {
            return;
        }
        checkActivation(false);
        if (this.activation != null) {
            this.handler.postDelayed(this.activationPoll, 2000L);
        }
    }

    /** Looks for a key file in the drop folder; moves on to the normal entry flow once authorised. */
    private final void checkActivation(boolean manual) {
        Screens.Activation a = this.activation;
        if (a == null) {
            return;
        }
        if (Certification.INSTANCE.scanDropFolder()) {
            this.activation = null;
            this.handler.removeCallbacks(this.activationPoll);
            beginEntry$default(this, false, 1, null);
            return;
        }
        String message = Certification.INSTANCE.lastDropMessage();
        if (message.isEmpty() && manual) {
            message = "폴더에서 키 파일을 찾지 못했어요. 파일을 넣었는지, 이름이 달라도 괜찮으니 폴더 위치가 맞는지 확인하세요.";
        }
        a.status.setText(AppStyle.keepWords(message));
    }''')

# leaving the activation screen: stop polling (beginEntry resets screens)
s = open(M).read()
anchor = '        AppPassword.INSTANCE.getSession().enter(newLaunch);'
assert anchor in s
s = s.replace(anchor, '''        this.activation = null;
        this.handler.removeCallbacks(this.activationPoll);
''' + anchor, 1)
open(M, 'w').write(s)
print('ok')
