import re
exec(open('/tmp/claude-0/work/fix.py').read())
M = D + 'MainActivity.java'
s = open(M).read()
s = s.replace('import android.app.AlertDialog;', 'import android.app.AlertDialog;\nimport android.app.Dialog;\nimport android.content.SharedPreferences;', 1)
s = s.replace('    private AlertDialog messageDialog;', '    private Dialog messageDialog;\n    private Screens.Home home;\n    private boolean inSettings;', 1)
s = s.replace('    private AlertDialog settingsDialog;', '    private Dialog settingsDialog;', 1)
s = s.replace('''        AlertDialog alertDialog = this.settingsDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
        AlertDialog alertDialog2 = this.messageDialog;
        if (alertDialog2 != null) {
            alertDialog2.dismiss();
        }''', '''        Dialog alertDialog = this.settingsDialog;
        if (alertDialog != null) {
            alertDialog.dismiss();
        }
        Dialog alertDialog2 = this.messageDialog;
        if (alertDialog2 != null) {
            alertDialog2.dismiss();
        }
        this.inSettings = false;''', 1)
open(M, 'w').write(s)

replace(M, 'private final void message(String text, String title)', '''    private final void message(String text, String title) {
        Dialog dialog = AppStyle.INSTANCE.sheet(this, title, Screens.textBody(this, text), "닫기", null, null, null);
        this.messageDialog = dialog;
        dialog.show();
    }''')

replace(M, 'public final void refreshStatus()', '''    public final void refreshStatus() {
        BotEngine engine;
        Screens.Home h = this.home;
        if (h == null || !UsageConsent.INSTANCE.getAllowed()) {
            return;
        }
        BotService companion = BotService.INSTANCE.getInstance();
        boolean running = (companion == null || (engine = companion.getEngine()) == null || !engine.getActive()) ? false : true;
        if (companion == null) {
            h.render(Screens.Link.NO_SERVICE, null);
        } else if (running) {
            h.render(Screens.Link.RUNNING, companion.getStatus());
        } else {
            h.render(Screens.Link.READY, null);
        }
    }''')

replace(M, 'private final void settings()', '''    private final void settings() {
        showSettings();
    }

    private final Function0<Unit> guard(final Function0<Unit> action) {
        return new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.button$lambda$0(action, MainActivity.this);
            }
        };
    }

    private final void showSettings() {
        this.inSettings = true;
        this.home = null;
        java.util.List<Screens.Nav> help = new java.util.ArrayList<>();
        help.add(new Screens.Nav("접근성 설정", "‘단어 테스트 도우미’ 권한을 켜거나 끕니다", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$38(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("사용 안내 다시 보기", null, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$39(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("약관 동의 기록", "동의 시각 확인 · 동의 철회", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$40(MainActivity.this);
            }
        })));
        help.add(new Screens.Nav("개인정보처리방침", null, guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$41(MainActivity.this);
            }
        })));
        if (Certification.INSTANCE.getRequired()) {
            help.add(new Screens.Nav("인증서 보기", null, guard(new Function0<Unit>() {
                @Override
                public Unit invoke() {
                    return MainActivity.settings$lambda$42(MainActivity.this);
                }
            })));
        }
        help.add(new Screens.Nav("최근 실행", "마지막 실행의 결과와 오류 요약", guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.settings$lambda$47(MainActivity.this);
            }
        })));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "";
        }
        SharedPreferences prefs = this.storage.getPrefs();
        setContentView(Screens.settings(this, prefs, version, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.showHome();
                return Unit.INSTANCE;
            }
        }, help));
    }''')

replace(M, 'private final void showConsentRecord()', '''    private final void showConsentRecord() {
        String str;
        ConsentRecord record = UsageConsent.INSTANCE.record();
        if (record == null) {
            str = "저장된 동의 기록이 없습니다.";
        } else {
            str = "두 단계 동의 완료\\n\\n동의 시각: " + DateFormat.getDateTimeInstance().format(new Date(record.getAcceptedAtMillis())) + "\\n약관 버전: 2026-10-26\\n\\n이 휴대폰의 앱 내부에 저장된 기록입니다.";
        }
        final MainActivity self = this;
        Dialog dialog = AppStyle.INSTANCE.sheet(this, "약관 동의 기록", Screens.textBody(this, str), "닫기", null, "동의 철회", new Runnable() {
            @Override
            public void run() {
                MainActivity.showConsentRecord$lambda$50(self, null, 0);
            }
        });
        this.messageDialog = dialog;
        dialog.show();
    }''')

s = open(M).read()
a = s.index('        final boolean z = UsageConsent.INSTANCE.getSession().getStep() == 1;')
b = s.index('    /* JADX INFO: Access modifiers changed from: private */\n    public static final Unit showConsent$lambda$19')
new = '''        final boolean z = UsageConsent.INSTANCE.getSession().getStep() == 1;
        final MainActivity mainActivity = this;
        this.home = null;
        Screens.Consent consent = Screens.consent(this, z, UsageConsent.INSTANCE.getNotice(), UsageConsent.INSTANCE.getPermissionDisclosure(), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$19(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$20(z, mainActivity);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showConsent$lambda$21(MainActivity.this);
            }
        }));
        setContentView(consent.root);
        consent.root.requestFocus();
    }

'''
s = s[:a] + new + s[b:]
open(M, 'w').write(s)

replace(M, 'private final void showHome()', '''    private final void showHome() {
        if (!UsageConsent.INSTANCE.getAllowed()) {
            throw new IllegalStateException("Check failed.");
        }
        this.inSettings = false;
        Screens.Home h = Screens.home(this, this.storage.getPrefs(), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$32(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$26(MainActivity.this);
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$28();
            }
        }), guard(new Function0<Unit>() {
            @Override
            public Unit invoke() {
                return MainActivity.showHome$lambda$30(MainActivity.this);
            }
        }));
        this.home = h;
        setContentView(h.root);
        refreshStatus();
    }''')

# password
replace(M, 'private final void showPassword()', '''    private final void showPassword() {
        this.home = null;
        this.status = null;
        this.primary = null;
        this.stopButton = null;
        this.accessibilityButton = null;
        getWindow().addFlags(ConstantsKt.DEFAULT_BUFFER_SIZE);
        final Screens.Password[] holder = new Screens.Password[1];
        holder[0] = Screens.password(this, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.showPassword$unlock(holder[0].input, MainActivity.this, holder[0].error);
                return Unit.INSTANCE;
            }
        }, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                MainActivity.this.closeApp();
                return Unit.INSTANCE;
            }
        });
        holder[0].input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                if (i != 6) {
                    return false;
                }
                MainActivity.showPassword$unlock(holder[0].input, MainActivity.this, holder[0].error);
                return true;
            }
        });
        setContentView(holder[0].root);
    }''')

# back button inside settings goes home
replace(M, 'public void onBackPressed()', '''    public void onBackPressed() {
        if (this.inSettings && UsageConsent.INSTANCE.getAllowed()) {
            showHome();
            return;
        }
        closeApp();
    }''')
print('ok')
