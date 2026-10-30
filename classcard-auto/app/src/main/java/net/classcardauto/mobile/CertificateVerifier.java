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

    /** What an issuer certificate (made on the computer for one phone's key) says about that phone. */
    public static final class IssuerInfo {
        public final String publicKey;
        public final String deviceId;
        public final String computerName;
        public final String computerId;

        IssuerInfo(String publicKey, String deviceId, String computerName, String computerId) {
            this.publicKey = publicKey;
            this.deviceId = deviceId;
            this.computerName = computerName;
            this.computerId = computerId;
        }
    }

    /**
     * Checks an issuer certificate: signed by the computer key this app trusts, marked as an issuer certificate for this
     * app, not expired. The phone key it carries may then sign activation codes for other phones.
     */
    public final IssuerInfo parseIssuerCert(String envelope, String packageName) {
        try {
            return parseIssuerCert0(envelope, packageName);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final IssuerInfo parseIssuerCert0(String envelope, String packageName) throws Exception {
        if (envelope.length() > 16384 || StringsKt.isBlank(packageName)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        JSONObject outer = new JSONObject(envelope);
        byte[] body = Base64.getDecoder().decode(outer.getString("payload"));
        byte[] sig = Base64.getDecoder().decode(outer.getString("signature"));
        PublicKey root = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(this.publicKey)));
        Signature check = Signature.getInstance("SHA256withRSA");
        check.initVerify(root);
        check.update(body);
        if (!check.verify(sig)) {
            throw new IllegalArgumentException("Invalid signature");
        }
        JSONObject p = new JSONObject(new String(body, Charsets.UTF_8));
        if (p.getInt("version") != 1 || !"issuer".equals(p.optString("kind", ""))) {
            throw new IllegalArgumentException("Not an issuer certificate");
        }
        if (!Intrinsics.areEqual(p.getString("packageName"), packageName)) {
            throw new IllegalArgumentException("Wrong device or app");
        }
        if (!Intrinsics.areEqual(p.getString("computerId"), this.issuerId)) {
            throw new IllegalArgumentException("Wrong issuing computer");
        }
        OffsetDateTime.parse(p.getString("issuedAt"));
        String expiresAt = p.optString("expiresAt", "");
        if (!expiresAt.isEmpty() && OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Certificate expired");
        }
        String issuerPub = verify$field(p, "issuerPub");
        String issuerDevice = verify$field(p, "issuerDeviceId");
        String name = p.optString("computerName", "unknown");
        return new IssuerInfo(issuerPub, issuerDevice, name.length() > 64 ? name.substring(0, 64) : name, p.getString("computerId"));
    }

    /**
     * An activation code is "MC1." + base64url of {issuer: issuer certificate, payload, signature}: the issuer
     * certificate proves the signing phone was authorised from the computer, the signature (ECDSA P-256) proves that
     * phone issued this code for exactly this device and app.
     */
    private final CertifiedDocument verifyActivation(String code, String deviceId, String packageName) throws Exception {
        if (code.length() > 16384 || StringsKt.isBlank(deviceId)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        String raw = code.trim().substring(4).replaceAll("\\s", "");
        JSONObject outer = new JSONObject(new String(Base64.getUrlDecoder().decode(raw), Charsets.UTF_8));
        IssuerInfo issuer = parseIssuerCert0(outer.getJSONObject("issuer").toString(), packageName);
        byte[] body = Base64.getDecoder().decode(outer.getString("payload"));
        byte[] sig = Base64.getDecoder().decode(outer.getString("signature"));
        PublicKey phoneKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(issuer.publicKey)));
        Signature check = Signature.getInstance("SHA256withECDSA");
        check.initVerify(phoneKey);
        check.update(body);
        if (!check.verify(sig)) {
            throw new IllegalArgumentException("Invalid signature");
        }
        JSONObject p = new JSONObject(new String(body, Charsets.UTF_8));
        if (p.getInt("version") != 1 || !"activation".equals(p.optString("kind", ""))) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        if (!Intrinsics.areEqual(p.getString("deviceId"), deviceId) || !Intrinsics.areEqual(p.getString("packageName"), packageName)) {
            throw new IllegalArgumentException("Wrong device or app");
        }
        String issuedAt = verify$field(p, "issuedAt");
        OffsetDateTime.parse(issuedAt);
        String expiresAt = p.optString("expiresAt", "");
        if (!expiresAt.isEmpty() && OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Certificate expired");
        }
        String number = verify$field(p, "number");
        if (!Intrinsics.areEqual(UUID.fromString(number).toString(), number)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        return new CertifiedDocument(issuedAt, number, "발급자 휴대폰(" + issuer.computerName + " 승인)", "휴대폰 발급", issuer.computerId, deviceId, packageName);
    }

    public final CertifiedDocument verify(String envelope, String deviceId, String packageName) {
        try {
            if (envelope.trim().startsWith("MC1.")) {
                return verifyActivation(envelope, deviceId, packageName);
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
