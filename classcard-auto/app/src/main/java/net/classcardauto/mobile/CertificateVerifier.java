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

    public final CertifiedDocument verify(String envelope, String deviceId, String packageName) {
        try {
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
