package net.classcardauto.mobile;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import org.json.JSONObject;

/**
 * The phone's side of the "MiMiC Auth" flow: it shows a QR (device code, one-time nonce, one-time public key); the
 * authenticator app scans it and shows a short code; the person types that code here. Only the holder of the
 * authenticator's private key can compute the code, so nothing secret is built into this app.
 */
public final class AuthChallenge {
    private static final String FILE = "auth-challenge.json";
    private static final long LIFETIME_MS = 30 * 60 * 1000L;
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MS = 5 * 60 * 1000L;

    private AuthChallenge() {
    }

    private static File file(Context context) {
        return new File(context.getFilesDir(), FILE);
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static JSONObject create(long lockedUntil) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        byte[] spki = pair.getPublic().getEncoded();
        byte[] point = Arrays.copyOfRange(spki, spki.length - 65, spki.length);
        byte[] nonce = new byte[8];
        new SecureRandom().nextBytes(nonce);
        JSONObject state = new JSONObject();
        state.put("e", Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        state.put("p", b64url(point));
        state.put("n", b64url(nonce));
        state.put("t", System.currentTimeMillis());
        state.put("f", 0);
        state.put("l", lockedUntil);
        return state;
    }

    private static synchronized JSONObject load(Context context) throws Exception {
        File file = file(context);
        JSONObject state = null;
        if (file.isFile()) {
            try {
                state = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
                state = null;
            }
        }
        if (state == null || System.currentTimeMillis() - state.optLong("t") > LIFETIME_MS) {
            state = create(state == null ? 0L : state.optLong("l"));
            save(context, state);
        }
        return state;
    }

    private static void save(Context context, JSONObject state) throws Exception {
        Files.write(file(context).toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** True when this build trusts at least one authenticator app. */
    public static boolean available() {
        return CertificationIssuer.AUTHENTICATOR_KEYS.length > 0;
    }

    /** Text of the QR the authenticator app scans: MCA1:&lt;device&gt;:&lt;nonce&gt;:&lt;one-time public key&gt;. */
    public static String qrText(Context context) throws Exception {
        return qrText(load(context), Certification.INSTANCE.deviceId());
    }

    static String qrText(JSONObject state, String device) throws Exception {
        return "MCA1:" + device + ":" + state.getString("n") + ":" + state.getString("p");
    }

    /**
     * The record for the first day (today, yesterday, tomorrow: the phones' clocks may differ) for which the typed
     * code verifies, or null. {@code typed} is the normalized 11 characters.
     */
    static String findValidRecord(JSONObject state, String typed, String device, String packageName, CertificateVerifier verifier, LocalDate today) throws Exception {
        for (int shift = 0; shift <= 2; shift++) {
            LocalDate day = today.plusDays(shift == 0 ? 0 : (shift == 1 ? -1 : 1));
            JSONObject record = new JSONObject();
            record.put("e", state.getString("e"));
            record.put("n", state.getString("n"));
            record.put("d", day.format(DateTimeFormatter.BASIC_ISO_DATE));
            record.put("u", String.valueOf(typed.charAt(0)));
            record.put("c", typed.substring(1));
            String text = "MCA1." + b64url(record.toString().getBytes(StandardCharsets.UTF_8));
            try {
                verifier.verify(text, device, packageName);
                return text;
            } catch (Throwable notThisDay) {
                // try the next day
            }
        }
        return null;
    }

    /** What a person types, tidied: upper case, no separators, O/I/L read as 0/1/1. */
    static String normalize(String typed) {
        return typed.toUpperCase(java.util.Locale.ROOT).replaceAll("[^0-9A-Z]", "").replace('O', '0').replace('I', '1').replace('L', '1');
    }

    /** Checks the typed code and, if right, authorises this phone. Returns null on success, else a message to show. */
    public static synchronized String submit(Context context, String typed) {
        try {
            if (!available()) {
                return "이 빌드에는 인증용 앱이 등록되어 있지 않아요.";
            }
            JSONObject state = load(context);
            long now = System.currentTimeMillis();
            if (now < state.optLong("l")) {
                return "시도가 너무 많았어요. " + ((state.optLong("l") - now + 59999) / 60000) + "분 뒤에 다시 해 주세요.";
            }
            String code = normalize(typed);
            if (code.length() != 11) {
                return "코드는 11자예요 (예: 3-ABCDE-FGH12).";
            }
            String device = Certification.INSTANCE.deviceId();
            String found = findValidRecord(state, code, device, context.getPackageName(), new CertificateVerifier(), LocalDate.now());
            if (found != null) {
                Certification.INSTANCE.install(found);
                file(context).delete();
                return null;
            }
            int failures = state.optInt("f") + 1;
            if (failures >= MAX_FAILURES) {
                save(context, create(now + LOCK_MS));
                return "코드가 " + MAX_FAILURES + "번 틀렸어요. 5분 뒤에 새 QR로 다시 해 주세요.";
            }
            state.put("f", failures);
            save(context, state);
            return "코드가 맞지 않아요 (" + failures + "/" + MAX_FAILURES + "). 인증용 앱에서 방금 이 QR을 찍어 만든 코드인지 확인하세요.";
        } catch (Throwable t) {
            return Certification.describe(t);
        }
    }
}
