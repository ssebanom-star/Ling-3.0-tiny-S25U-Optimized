package net.classcardauto.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

/**
 * "발급자 도구": lets a phone that has been authorised once from the computer issue activation codes for other phones.
 * Everything runs on the phone; the signing key never leaves the Android keystore.
 */
public final class IssuerScreen {
    private IssuerScreen() {
    }

    public static String clipboardText(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService("clipboard");
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null || clipboard.getPrimaryClip().getItemCount() == 0) {
            return "";
        }
        CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString().trim();
    }

    private static void copy(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService("clipboard");
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(context, label + " 복사 완료", Toast.LENGTH_SHORT).show();
        }
    }

    private static LinearLayout.LayoutParams top(Context c, int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = AppStyle.INSTANCE.dp(c, dp);
        return lp;
    }

    private static EditText field(Context c, String hint, boolean numbers) {
        AppStyle s = AppStyle.INSTANCE;
        EditText edit = new EditText(c);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextSize(15.0f);
        edit.setTextColor(s.getInk());
        edit.setHintTextColor(s.getMuted());
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setInputType(numbers ? InputType.TYPE_CLASS_NUMBER : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        edit.setBackground(s.roundRect(c, s.getBackground(), 12, s.getBorder()));
        edit.setPadding(s.dp(c, 14), s.dp(c, 10), s.dp(c, 14), s.dp(c, 10));
        return edit;
    }

    public static View build(final Context context, final Function0<Unit> onBack) {
        final AppStyle s = AppStyle.INSTANCE;
        final Screens.Page page = Screens.page(context, Screens.titleBar(context, "발급자 도구", onBack));
        final LinearLayout content = page.content;
        content.addView(s.label(context, "다른 휴대폰이 컴퓨터 없이 인증받을 수 있도록, 이 휴대폰이 활성화 코드를 발급합니다. 처음 한 번만 컴퓨터가 필요해요.", 14.5f, s.getMuted(), false), top(context, 6));

        // step 1: ask the computer to authorise this phone as an issuer
        LinearLayout step1 = s.card(context);
        step1.addView(s.label(context, "1. 발급자 요청 (처음 한 번)", 15.5f, s.getInk(), true));
        step1.addView(s.caption(context, "‘요청 복사’ 후 컴퓨터의 issuer 폴더에서  python issue.py --issuer-request \"붙여넣기\"  를 실행하면 mimic-issuer.cert 가 만들어져요."), top(context, 6));
        Button copyRequest = s.button(context, "요청 복사", AppStyle.Tone.TONAL, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                try {
                    copy(context, "발급자 요청", PhoneIssuer.requestText());
                } catch (Throwable t) {
                    Toast.makeText(context, "키를 만들지 못했어요: " + t.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
                }
                return Unit.INSTANCE;
            }
        });
        step1.addView(copyRequest, top(context, 10));
        content.addView(step1, top(context, 16));

        // step 2: store the certificate that came back
        final TextView state = s.label(context, "", 13.5f, s.getMuted(), true);
        LinearLayout step2 = s.card(context);
        step2.addView(s.label(context, "2. 발급자 인증서 등록", 15.5f, s.getInk(), true));
        step2.addView(s.caption(context, "mimic-issuer.cert 파일의 내용을 이 휴대폰으로 보내 복사한 뒤 붙여넣으세요."), top(context, 6));
        step2.addView(state, top(context, 8));
        Button paste = s.button(context, "복사한 인증서 붙여넣기", AppStyle.Tone.TONAL, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                String text = clipboardText(context);
                if (text.isEmpty()) {
                    Toast.makeText(context, "클립보드가 비어 있어요.", Toast.LENGTH_SHORT).show();
                    return Unit.INSTANCE;
                }
                try {
                    PhoneIssuer.installIssuerCert(context, text);
                    Toast.makeText(context, "발급자 인증서를 등록했어요.", Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(context, Certification.describe(t), Toast.LENGTH_LONG).show();
                }
                state.setText(stateText(context));
                return Unit.INSTANCE;
            }
        });
        step2.addView(paste, top(context, 10));
        content.addView(step2, top(context, 12));
        state.setText(stateText(context));

        // step 3: issue a code for another phone
        LinearLayout step3 = s.card(context);
        step3.addView(s.label(context, "3. 다른 휴대폰 활성화 코드 만들기", 15.5f, s.getInk(), true));
        step3.addView(s.caption(context, "상대 휴대폰의 ‘인증이 필요해요’ 화면에 있는 기기 코드를 입력하세요. 코드는 그 휴대폰에서만 통해요."), top(context, 6));
        final EditText device = field(context, "상대 기기 코드", false);
        step3.addView(device, top(context, 10));
        final EditText days = field(context, "유효 일수 (비우면 기간 없음)", true);
        days.setText("7");
        step3.addView(days, top(context, 8));
        final TextView codeView = s.label(context, "", 11.5f, s.getInk(), false);
        codeView.setTypeface(Typeface.MONOSPACE);
        codeView.setTextIsSelectable(true);
        codeView.setVisibility(View.GONE);
        final Button copyCode = s.button(context, "활성화 코드 복사", AppStyle.Tone.PRIMARY, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                copy(context, "활성화 코드", codeView.getText().toString());
                return Unit.INSTANCE;
            }
        });
        copyCode.setVisibility(View.GONE);
        Button make = s.button(context, "활성화 코드 만들기", AppStyle.Tone.PRIMARY, new Function0<Unit>() {
            @Override
            public Unit invoke() {
                try {
                    int n = days.getText().toString().trim().isEmpty() ? 0 : Integer.parseInt(days.getText().toString().trim());
                    String code = PhoneIssuer.activationCode(context, device.getText().toString(), Math.max(0, Math.min(n, 3650)));
                    codeView.setText(code);
                    codeView.setVisibility(View.VISIBLE);
                    copyCode.setVisibility(View.VISIBLE);
                    copy(context, "활성화 코드", code);
                } catch (Throwable t) {
                    String why = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                    Toast.makeText(context, why.contains("Wrong") || why.contains("Invalid") ? Certification.describe(t) : why, Toast.LENGTH_LONG).show();
                }
                return Unit.INSTANCE;
            }
        });
        step3.addView(make, top(context, 12));
        step3.addView(codeView, top(context, 10));
        step3.addView(copyCode, top(context, 8));
        content.addView(step3, top(context, 12));

        content.addView(s.caption(context, "발급자 휴대폰을 잃어버리면 새 코드를 만들 수 없어요. 이미 인증된 휴대폰은 그대로 쓸 수 있어요. 이 휴대폰의 서명 키는 앱 밖으로 꺼낼 수 없게 보관돼요."), top(context, 14));
        return page.root;
    }

    private static String stateText(Context context) {
        return PhoneIssuer.hasIssuerCert(context) ? "상태: 등록됨 · 아래에서 코드를 만들 수 있어요" : "상태: 아직 등록 안 됨";
    }
}
