package net.classcardauto.mobile;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.json.JSONObject;

/* compiled from: CertificateVerifier.kt */
/* loaded from: classes.dex */
public final class CertificateVerifier {
    private final String issuerId;
    private final String publicKey;
    private String[] authenticatorKeys = CertificationIssuer.AUTHENTICATOR_KEYS;

    /** For tests: trusts these authenticator keys instead of the build's list. */
    public CertificateVerifier withAuthenticatorKeys(String[] keys) {
        this.authenticatorKeys = keys;
        return this;
    }

    /** Crockford base32 without I, L, O, U: what the authenticator shows and a person types. */
    static final String CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    /** The first character of a code says how long it is valid (days; 0 = no end). */
    static final int[] CODE_DAYS = {0, 1, 3, 7, 30, 90, 365};

    /**
     * The 10-character proof of a challenge answer: the first 50 bits of HMAC-SHA256 (keyed with the secret both sides
     * derive by ECDH) over this app, the phone, the challenge nonce, the day of issue and the validity digit.
     */
    public static String challengeMac(byte[] secret, String packageName, String deviceId, String nonce, String yyyymmdd, char validity) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
        byte[] h = mac.doFinal(("MCA1|" + packageName + "|" + deviceId + "|" + nonce + "|" + yyyymmdd + "|" + validity).getBytes(Charsets.UTF_8));
        long v = 0;
        for (int i = 0; i < 7; i++) {
            v = (v << 8) | (h[i] & 0xFF);
        }
        v >>>= 6;
        StringBuilder out = new StringBuilder();
        for (int i = 9; i >= 0; i--) {
            out.append(CODE_ALPHABET.charAt((int) ((v >>> (5 * i)) & 31)));
        }
        return out.toString();
    }

    /**
     * A challenge answer kept on the phone: "MCA1." + base64url of {e: this phone's one-time private key, n: nonce,
     * d: day of issue, u: validity digit, c: the 10 typed characters}. It is re-checked on every start, so changing
     * the validity or the day in the file makes the proof fail.
     */
    private final CertifiedDocument verifyChallengeRecord(String record, String deviceId, String packageName) throws Exception {
        if (record.length() > 4096 || StringsKt.isBlank(deviceId) || this.authenticatorKeys.length == 0) {
            throw new IllegalArgumentException("Wrong issuing computer");
        }
        JSONObject p = new JSONObject(new String(Base64.getUrlDecoder().decode(record.trim().substring(5)), Charsets.UTF_8));
        java.security.PrivateKey own = KeyFactory.getInstance("EC").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(p.getString("e"))));
        String nonce = p.getString("n");
        String day = p.getString("d");
        String validity = p.getString("u");
        String typed = p.getString("c");
        if (validity.length() != 1 || typed.length() != 10 || !day.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        int index = Integer.parseInt(validity);
        if (index < 0 || index >= CODE_DAYS.length) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        boolean proven = false;
        for (String key : this.authenticatorKeys) {
            javax.crypto.KeyAgreement agreement = javax.crypto.KeyAgreement.getInstance("ECDH");
            agreement.init(own);
            agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))), true);
            String expected = challengeMac(agreement.generateSecret(), packageName, deviceId, nonce, day, validity.charAt(0));
            if (java.security.MessageDigest.isEqual(expected.getBytes(Charsets.UTF_8), typed.getBytes(Charsets.UTF_8))) {
                proven = true;
                break;
            }
        }
        if (!proven) {
            throw new IllegalArgumentException("Invalid signature");
        }
        java.time.LocalDate issued = java.time.LocalDate.parse(day, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        java.time.LocalDate today = java.time.LocalDate.now();
        if (issued.isAfter(today.plusDays(1))) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        int days = CODE_DAYS[index];
        if (days > 0 && today.isAfter(issued.plusDays(days))) {
            throw new IllegalArgumentException("Certificate expired");
        }
        String number = UUID.nameUUIDFromBytes((nonce + typed).getBytes(Charsets.UTF_8)).toString();
        return new CertifiedDocument(issued + "T00:00:00Z", number, "인증용 앱", "코드 입력", "authenticator", deviceId, packageName);
    }

    /* JADX WARN: Multi-variable type inference failed */
    public CertificateVerifier() {
        this(CertificationIssuer.PUBLIC_KEY, CertificationIssuer.MACHINE_ID);
    }

    public CertificateVerifier(String publicKey, String issuerId) {
        Intrinsics.checkNotNullParameter(publicKey, "publicKey");
        Intrinsics.checkNotNullParameter(issuerId, "issuerId");
        this.publicKey = publicKey;
        this.issuerId = issuerId;
    }

    public /* synthetic */ CertificateVerifier(String str, String str2, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this((i & 1) != 0 ? CertificationIssuer.PUBLIC_KEY : str, (i & 2) != 0 ? CertificationIssuer.MACHINE_ID : str2);
    }

    private static final String verify$field(JSONObject jSONObject, String str) throws org.json.JSONException {
        String string = jSONObject.getString(str);
        Intrinsics.checkNotNull(string);
        if (!StringsKt.isBlank(string) && string.length() <= 256) {
            boolean clean = true;
            for (int i = 0; i < string.length(); i++) {
                if (Character.isISOControl(string.charAt(i))) {
                    clean = false;
                    break;
                }
            }
            if (clean) {
                return string;
            }
        }
        throw new IllegalArgumentException("Failed requirement.".toString());
    }

    public final CertifiedDocument verify(String envelope, String deviceId, String packageName) {
        try {
            if (envelope.trim().startsWith("MCA1.")) {
                return verifyChallengeRecord(envelope, deviceId, packageName);
            }
            return verify0(envelope, deviceId, packageName);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final CertifiedDocument verify0(String envelope, String deviceId, String packageName) throws Exception {
        Intrinsics.checkNotNullParameter(envelope, "envelope");
        Intrinsics.checkNotNullParameter(deviceId, "deviceId");
        Intrinsics.checkNotNullParameter(packageName, "packageName");
        if (envelope.length() > 16384 || StringsKt.isBlank(deviceId)) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        JSONObject jSONObject = new JSONObject(envelope);
        byte[] decode = Base64.getDecoder().decode(jSONObject.getString("payload"));
        byte[] decode2 = Base64.getDecoder().decode(jSONObject.getString("signature"));
        PublicKey generatePublic = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(this.publicKey)));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(generatePublic);
        signature.update(decode);
        if (!signature.verify(decode2)) {
            throw new IllegalArgumentException("Invalid signature".toString());
        }
        Intrinsics.checkNotNull(decode);
        JSONObject jSONObject2 = new JSONObject(new String(decode, Charsets.UTF_8));
        if (jSONObject2.getInt("version") != 1) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        if (!Intrinsics.areEqual(jSONObject2.getString("deviceId"), deviceId) || !Intrinsics.areEqual(jSONObject2.getString("packageName"), packageName)) {
            throw new IllegalArgumentException("Wrong device or app".toString());
        }
        if (!Intrinsics.areEqual(jSONObject2.getString("computerId"), this.issuerId)) {
            throw new IllegalArgumentException("Wrong issuing computer".toString());
        }
        String verify$field = verify$field(jSONObject2, "issuedAt");
        OffsetDateTime.parse(verify$field);
        // optional expiry: a certificate without "expiresAt" never expires
        String expiresAt = jSONObject2.optString("expiresAt", "");
        if (!expiresAt.isEmpty()) {
            if (OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())) {
                throw new IllegalArgumentException("Certificate expired");
            }
        }
        String verify$field2 = verify$field(jSONObject2, "number");
        if (!Intrinsics.areEqual(UUID.fromString(verify$field2).toString(), verify$field2)) {
            throw new IllegalArgumentException("Failed requirement.".toString());
        }
        Intrinsics.checkNotNull(verify$field);
        String verify$field3 = verify$field(jSONObject2, "computerName");
        Intrinsics.checkNotNullExpressionValue(verify$field3, "verify$field(...)");
        String verify$field4 = verify$field(jSONObject2, "computerModel");
        Intrinsics.checkNotNullExpressionValue(verify$field4, "verify$field(...)");
        String verify$field5 = verify$field(jSONObject2, "computerId");
        Intrinsics.checkNotNullExpressionValue(verify$field5, "verify$field(...)");
        return new CertifiedDocument(verify$field, verify$field2, verify$field3, verify$field4, verify$field5, deviceId, packageName);
    }
}
