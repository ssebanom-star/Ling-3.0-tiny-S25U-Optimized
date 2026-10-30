package net.classcardauto.auth;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;

/** MiMiC Auth: scan the QR a new phone shows, pick how long it is valid, read the code out to that phone. */
public final class AuthenticatorActivity extends Activity {
    private static final int INK = Color.rgb(20, 32, 28);
    private static final int MUTED = Color.rgb(98, 112, 106);
    private static final int ACCENT = Color.rgb(14, 124, 107);

    private LinearLayout content;
    private AuthMath.Challenge challenge;
    private RadioGroup validity;
    private LinearLayout resultBox;
    private TextView resultCode;
    private TextView resultNote;
    private TextView challengeInfo;
    private Button makeButton;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : ACCENT);
        b.setStateListAnimator(null);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(primary ? ACCENT : Color.argb(30, 14, 124, 107));
        b.setBackground(bg);
        b.setOnClickListener(click);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        c.setBackground(bg);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        return c;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(dp);
        return lp;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(244, 247, 245));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(244, 247, 245));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(28), dp(20), dp(28));
        scroll.addView(content);
        setContentView(scroll);

        content.addView(text("MiMiC Auth", 26, INK, true));
        content.addView(text("새 휴대폰의 QR을 찍으면 입력할 코드가 나와요.", 14.5f, MUTED, false), top(6));

        LinearLayout step1 = card();
        step1.addView(text("1. 새 휴대폰의 QR 찍기", 16, INK, true));
        step1.addView(text("새 휴대폰의 ‘인증이 필요해요’ 화면에 있는 방법 C의 QR을 찍어 주세요.", 13.5f, MUTED, false), top(6));
        step1.addView(button("QR 촬영", true, v -> {
            try {
                QrCapture.start(this);
            } catch (Throwable t) {
                Toast.makeText(this, "카메라를 열지 못했어요.", Toast.LENGTH_LONG).show();
            }
        }), top(12));
        step1.addView(button("클립보드의 QR 내용 붙여넣기", false, v -> accept(clipboard())), top(6));
        challengeInfo = text("", 13.5f, ACCENT, true);
        step1.addView(challengeInfo, top(10));
        content.addView(step1, top(18));

        LinearLayout step2 = card();
        step2.addView(text("2. 얼마나 쓰게 할까요", 16, INK, true));
        validity = new RadioGroup(this);
        for (int i = 0; i < AuthMath.LABELS.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(AuthMath.LABELS[i]);
            r.setId(100 + i);
            r.setTextSize(15);
            r.setTextColor(INK);
            validity.addView(r);
        }
        validity.check(102);
        step2.addView(validity, top(6));
        makeButton = button("코드 만들기", true, v -> make());
        makeButton.setEnabled(false);
        makeButton.setAlpha(0.45f);
        step2.addView(makeButton, top(10));
        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setGravity(Gravity.CENTER_HORIZONTAL);
        resultBox.setVisibility(View.GONE);
        resultCode = text("", 30, INK, true);
        resultCode.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        resultCode.setTextIsSelectable(true);
        resultCode.setGravity(Gravity.CENTER);
        resultBox.addView(resultCode, top(16));
        resultNote = text("", 13, MUTED, false);
        resultNote.setGravity(Gravity.CENTER);
        resultBox.addView(resultNote, top(6));
        step2.addView(resultBox);
        content.addView(step2, top(12));

        LinearLayout keyCard = card();
        keyCard.addView(text("내 인증용 앱 공개키 (처음 한 번)", 15, INK, true));
        keyCard.addView(text("아직 새 폰 앱이 이 인증용 앱을 모르면, 아래 키를 개발자에게 보내 앱에 등록(다시 빌드)해야 코드가 통해요.", 13, MUTED, false), top(6));
        final TextView key = text("", 11, INK, false);
        key.setTypeface(Typeface.MONOSPACE);
        key.setTextIsSelectable(true);
        try {
            key.setText(AuthKey.publicKey(this));
        } catch (Throwable t) {
            key.setText("키를 만들지 못했어요: " + t.getClass().getSimpleName());
        }
        keyCard.addView(key, top(8));
        keyCard.addView(text(AuthKey.inKeystore ? "개인키는 이 폰의 보안 저장소에 있어 꺼낼 수 없어요." : "이 기기에서는 개인키를 앱 전용 파일로 보관해요.", 12, MUTED, false), top(6));
        keyCard.addView(button("공개키 복사", false, v -> copy("MiMiC Auth 공개키", key.getText().toString())), top(8));
        content.addView(keyCard, top(12));
    }

    private String clipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip().getItemCount() == 0) {
            return "";
        }
        CharSequence t = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        return t == null ? "" : t.toString();
    }

    private void copy(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, label + " 복사 완료", Toast.LENGTH_SHORT).show();
        }
    }

    private void accept(String qrText) {
        try {
            challenge = AuthMath.parse(qrText);
            challengeInfo.setText("읽었어요 · 새 폰 코드 " + challenge.device);
            makeButton.setEnabled(true);
            makeButton.setAlpha(1f);
            resultBox.setVisibility(View.GONE);
        } catch (Throwable t) {
            challenge = null;
            challengeInfo.setText(t.getMessage() == null ? "QR을 읽지 못했어요." : t.getMessage());
            makeButton.setEnabled(false);
            makeButton.setAlpha(0.45f);
        }
    }

    private void make() {
        if (challenge == null) {
            return;
        }
        try {
            int index = validity.getCheckedRadioButtonId() - 100;
            byte[] secret = AuthKey.agree(this, challenge.peer);
            String code = AuthMath.code(secret, challenge, AuthMath.DIGITS[index], LocalDate.now());
            resultCode.setText(code);
            resultNote.setText("새 휴대폰의 코드 입력칸에 이 코드를 입력하세요. (" + AuthMath.LABELS[index] + " 동안 유효 · 같은 QR로만 통해요)");
            resultBox.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            resultCode.setText("");
            resultNote.setText("코드를 만들지 못했어요: " + t.getClass().getSimpleName());
            resultBox.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != QrCapture.REQUEST) {
            return;
        }
        String qr = QrCapture.handle(this, requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (qr == null || qr.isEmpty()) {
            Toast.makeText(this, "QR을 읽지 못했어요. 화면 전체가 또렷하게 나오게 다시 찍어 보세요.", Toast.LENGTH_LONG).show();
            return;
        }
        accept(qr);
    }
}
