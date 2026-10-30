package net.classcardauto.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The authenticator's arithmetic, with no Android types so it can be tested on a JVM against the app's verifier
 * (CertificateVerifier.challengeMac must stay identical to {@link #mac}).
 */
public final class AuthMath {
    private AuthMath() {
    }

    public static final String PACKAGE = "net.classcardauto.mobile.nocert";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    /** Choices offered to the person: label and the digit that starts the code. */
    public static final String[] LABELS = {"1일", "3일", "7일", "30일", "90일", "1년", "기간 없음"};
    public static final char[] DIGITS = {'1', '2', '3', '4', '5', '6', '0'};
    // X.509 SubjectPublicKeyInfo prefix of an uncompressed secp256r1 point
    private static final byte[] SPKI_PREFIX = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200");

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    /** A challenge scanned from the new phone's QR: "MCA1:device:nonce:one-time public key". */
    public static final class Challenge {
        public final String device;
        public final String nonce;
        public final PublicKey peer;

        Challenge(String device, String nonce, PublicKey peer) {
            this.device = device;
            this.nonce = nonce;
            this.peer = peer;
        }
    }

    public static Challenge parse(String text) throws Exception {
        String[] parts = text.trim().split(":");
        if (parts.length != 4 || !parts[0].equals("MCA1") || !parts[1].matches("[0-9a-z]{8,64}") || !parts[2].matches("[A-Za-z0-9_-]{11}")) {
            throw new IllegalArgumentException("이 QR은 MiMiC 인증 QR이 아니에요.");
        }
        byte[] point = Base64.getUrlDecoder().decode(parts[3]);
        if (point.length != 65 || point[0] != 4) {
            throw new IllegalArgumentException("QR 내용이 올바르지 않아요.");
        }
        byte[] spki = new byte[SPKI_PREFIX.length + 65];
        System.arraycopy(SPKI_PREFIX, 0, spki, 0, SPKI_PREFIX.length);
        System.arraycopy(point, 0, spki, SPKI_PREFIX.length, 65);
        PublicKey peer = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
        return new Challenge(parts[1], parts[2], peer);
    }

    public static byte[] agree(PrivateKey own, PublicKey peer, KeyAgreement agreement) throws Exception {
        agreement.init(own);
        agreement.doPhase(peer, true);
        return agreement.generateSecret();
    }

    public static String mac(byte[] secret, String device, String nonce, String yyyymmdd, char validity) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] h = mac.doFinal(("MCA1|" + PACKAGE + "|" + device + "|" + nonce + "|" + yyyymmdd + "|" + validity).getBytes(StandardCharsets.UTF_8));
        long v = 0;
        for (int i = 0; i < 7; i++) {
            v = (v << 8) | (h[i] & 0xFF);
        }
        v >>>= 6;
        StringBuilder out = new StringBuilder();
        for (int i = 9; i >= 0; i--) {
            out.append(ALPHABET.charAt((int) ((v >>> (5 * i)) & 31)));
        }
        return out.toString();
    }

    /** The code to show: validity digit + 10 characters, grouped like "3-ABCDE-FGH12". */
    public static String code(byte[] secret, Challenge challenge, char validity, LocalDate day) throws Exception {
        String proof = mac(secret, challenge.device, challenge.nonce, day.format(DateTimeFormatter.BASIC_ISO_DATE), validity);
        return validity + "-" + proof.substring(0, 5) + "-" + proof.substring(5);
    }
}
