package net.classcardauto.mobile;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.KeyAgreement;
import net.classcardauto.auth.AuthMath;
import org.json.JSONObject;

/**
 * The whole MiMiC Auth exchange on a JVM: the phone's challenge (AuthChallenge), the authenticator's answer
 * (AuthMath from the authenticator module) and the phone's check (CertificateVerifier), plus the ways it must fail.
 */
public class AuthFlowTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean cond) { if (cond) pass++; else { fail++; System.out.println("FAIL " + name); } }

    static final String PKG = "net.classcardauto.mobile.nocert";
    static final String DEVICE = "f881a45eedbd33af";

    static KeyPair ec() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    static String pubText(KeyPair p) { return Base64.getEncoder().encodeToString(p.getPublic().getEncoded()); }

    /** What the authenticator shows for a QR text. */
    static String codeFor(KeyPair authenticator, String qr, char validity, LocalDate day) throws Exception {
        AuthMath.Challenge c = AuthMath.parse(qr);
        byte[] secret = AuthMath.agree(authenticator.getPrivate(), c.peer, KeyAgreement.getInstance("ECDH"));
        return AuthMath.code(secret, c, validity, day);
    }

    static boolean accepts(JSONObject state, String device, String typed, KeyPair trusted, LocalDate today) throws Exception {
        CertificateVerifier v = new CertificateVerifier().withAuthenticatorKeys(new String[]{pubText(trusted)});
        return AuthChallenge.findValidRecord(state, AuthChallenge.normalize(typed), device, PKG, v, today) != null;
    }

    public static void main(String[] a) throws Exception {
        KeyPair authenticator = ec();
        KeyPair stranger = ec();
        LocalDate today = LocalDate.now();

        JSONObject state = AuthChallenge.create(0);
        String qr = AuthChallenge.qrText(state, DEVICE);
        System.out.println("QR text length: " + qr.length());
        String code = codeFor(authenticator, qr, '3', today);
        System.out.println("code: " + code);
        ok("code format", code.matches("[0-6]-[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}"));
        ok("right code accepted", accepts(state, DEVICE, code, authenticator, today));
        ok("typed in lower case without dashes", accepts(state, DEVICE, code.toLowerCase().replace("-", " "), authenticator, today));
        ok("0/O and 1/I/L typed loosely", accepts(state, DEVICE, code.replace('0', 'o').replace('1', 'l'), authenticator, today));
        ok("no-expiry code accepted", accepts(state, DEVICE, codeFor(authenticator, qr, '0', today), authenticator, today));

        // clock differences of a day are tolerated, a week is not for a 1-day code
        ok("authenticator one day behind", accepts(state, DEVICE, codeFor(authenticator, qr, '3', today.minusDays(1)), authenticator, today));
        ok("authenticator one day ahead", accepts(state, DEVICE, codeFor(authenticator, qr, '3', today.plusDays(1)), authenticator, today));
        ok("a code made 5 days ago is not accepted today (validity day outside the window)", !accepts(state, DEVICE, codeFor(authenticator, qr, (char) 51, today.minusDays(5)), authenticator, today));

        // wrong everything
        String flipped = code.substring(0, code.length() - 1) + (code.endsWith("0") ? "1" : "0");
        ok("one wrong character rejected", !accepts(state, DEVICE, flipped, authenticator, today));
        ok("code from another authenticator rejected", !accepts(state, DEVICE, codeFor(stranger, qr, '3', today), authenticator, today));
        ok("authenticator not trusted by this build", !accepts(state, DEVICE, code, stranger, today));
        ok("code for a different phone's QR rejected", !accepts(state, DEVICE, codeFor(authenticator, AuthChallenge.qrText(state, "1234567890abcdef"), '3', today), authenticator, today));
        JSONObject other = AuthChallenge.create(0);
        ok("code from another challenge (replay) rejected", !accepts(other, DEVICE, code, authenticator, today));
        ok("another phone with the same code rejected", !accepts(state, "1234567890abcdef", code, authenticator, today));

        // a code that is too old for its validity: the proof is genuine, the time is up
        CertificateVerifier v = new CertificateVerifier().withAuthenticatorKeys(new String[]{pubText(authenticator)});
        String oneDay = codeFor(authenticator, qr, '1', today.minusDays(3));
        ok("1-day code issued 3 days ago is expired", AuthChallenge.findValidRecord(state, AuthChallenge.normalize(oneDay), DEVICE, PKG, v, today.minusDays(3)) == null);
        ok("1-day code issued today is accepted", AuthChallenge.findValidRecord(state, AuthChallenge.normalize(codeFor(authenticator, qr, '1', today)), DEVICE, PKG, v, today) != null);
        ok("30-day code issued 10 days ago is still valid", AuthChallenge.findValidRecord(state, AuthChallenge.normalize(codeFor(authenticator, qr, '4', today.minusDays(10))), DEVICE, PKG, v, today.minusDays(10)) != null);

        // the stored record cannot be edited to extend or move the validity
        String record = AuthChallenge.findValidRecord(state, AuthChallenge.normalize(code), DEVICE, PKG, v, today);
        JSONObject parsed = new JSONObject(new String(Base64.getUrlDecoder().decode(record.substring(5)), StandardCharsets.UTF_8));
        parsed.put("u", "0");
        String tamperedValidity = "MCA1." + Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.toString().getBytes(StandardCharsets.UTF_8));
        boolean tvOk;
        try { v.verify(tamperedValidity, DEVICE, PKG); tvOk = false; } catch (Throwable t) { tvOk = true; }
        ok("edited validity digit rejected", tvOk);
        parsed.put("u", "3");
        parsed.put("d", today.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE));
        String tamperedDay = "MCA1." + Base64.getUrlEncoder().withoutPadding().encodeToString(parsed.toString().getBytes(StandardCharsets.UTF_8));
        boolean tdOk;
        try { v.verify(tamperedDay, DEVICE, PKG); tdOk = false; } catch (Throwable t) { tdOk = true; }
        ok("edited issue day rejected", tdOk);
        ok("genuine record verifies", v.verify(record, DEVICE, PKG) != null);
        boolean otherPhone;
        try { v.verify(record, "1234567890abcdef", PKG); otherPhone = false; } catch (Throwable t) { otherPhone = true; }
        ok("record copied to another phone rejected", otherPhone);
        boolean noKeys;
        try { new CertificateVerifier().withAuthenticatorKeys(new String[0]).verify(record, DEVICE, PKG); noKeys = false; } catch (Throwable t) { noKeys = true; }
        ok("build without authenticator keys rejects", noKeys);

        // the keys built into the app are well-formed EC P-256 public keys
        boolean builtInOk = CertificationIssuer.AUTHENTICATOR_KEYS.length > 0;
        for (String key : CertificationIssuer.AUTHENTICATOR_KEYS) {
            try {
                java.security.interfaces.ECPublicKey k = (java.security.interfaces.ECPublicKey) java.security.KeyFactory.getInstance("EC")
                        .generatePublic(new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(key)));
                builtInOk &= k.getParams().getOrder().bitLength() == 256;
            } catch (Exception e) {
                builtInOk = false;
            }
        }
        ok("built-in authenticator keys are valid P-256 keys", builtInOk);

        // garbage QR for the authenticator
        boolean garbage;
        try { AuthMath.parse("hello"); garbage = false; } catch (Exception e) { garbage = true; }
        ok("authenticator rejects a foreign QR", garbage);
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
