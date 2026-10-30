package net.classcardauto.mobile;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Lets this phone hand out activation codes to other phones, so a new phone does not need a computer.
 *
 * The phone holds an EC key pair in the Android keystore (the private part cannot be exported). A computer, once,
 * signs an "issuer certificate" binding that public key to this phone; after that the phone signs activation codes
 * that other phones verify against the computer key they already trust. See {@link CertificateVerifier}.
 */
public final class PhoneIssuer {
    private static final String ALIAS = "mimic_issuer";
    private static final String CERT_FILE = "issuer-cert.json";

    private PhoneIssuer() {
    }

    private static KeyStore keystore() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }

    /** This phone's issuer public key (X.509, base64); the key pair is created on first use. */
    public static String publicKey() throws Exception {
        KeyStore store = keystore();
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build());
            generator.generateKeyPair();
        }
        PublicKey key = store.getCertificate(ALIAS).getPublicKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Text to give to the computer's issue.py (--issuer-request) once: this phone's public key and device code. */
    public static String requestText() throws Exception {
        JSONObject request = new JSONObject();
        request.put("kind", "issuer-request");
        request.put("issuerPub", publicKey());
        request.put("device", Certification.INSTANCE.deviceId());
        return request.toString();
    }

    private static File certFile(Context context) {
        return new File(context.getFilesDir(), CERT_FILE);
    }

    public static boolean hasIssuerCert(Context context) {
        File file = certFile(context);
        if (!file.isFile()) {
            return false;
        }
        try {
            checkIssuerCert(context, new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void checkIssuerCert(Context context, String text) throws Exception {
        CertificateVerifier.IssuerInfo info = new CertificateVerifier().parseIssuerCert(text, context.getPackageName());
        if (!info.publicKey.equals(publicKey()) || !info.deviceId.equals(Certification.INSTANCE.deviceId())) {
            throw new IllegalArgumentException("Wrong device or app");
        }
    }

    /** Stores the issuer certificate made on the computer, after checking it is for this phone and this key. */
    public static void installIssuerCert(Context context, String text) throws Exception {
        String trimmed = text.trim();
        checkIssuerCert(context, trimmed);
        Files.write(certFile(context).toPath(), trimmed.getBytes(StandardCharsets.UTF_8));
    }

    /** True when this phone's key is one of the issuers built into the app, so it can issue short codes without a certificate. */
    public static boolean isBuiltInIssuer() {
        try {
            String mine = publicKey();
            for (String root : CertificationIssuer.PHONE_ROOTS) {
                if (root.equals(mine)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** An activation code for {@code targetDevice} (the code the other phone shows), valid for {@code days} (0 = no end). */
    public static String activationCode(Context context, String targetDevice, int days) throws Exception {
        String device = targetDevice.replaceAll("[\\s\\-]", "").toLowerCase(java.util.Locale.ROOT);
        if (!device.matches("[0-9a-z]{8,64}")) {
            throw new IllegalArgumentException("기기 코드 형식이 올바르지 않아요.");
        }
        boolean builtIn = isBuiltInIssuer();
        String certText = null;
        if (!builtIn) {
            File file = certFile(context);
            if (!file.isFile()) {
                throw new IllegalStateException("발급자 인증서가 아직 없어요.");
            }
            certText = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            checkIssuerCert(context, certText);
        }
        OffsetDateTime now = OffsetDateTime.now().withNano(0);
        JSONObject payload = new JSONObject();
        payload.put("version", 1);
        payload.put("kind", "activation");
        payload.put("deviceId", device);
        payload.put("packageName", context.getPackageName());
        payload.put("issuedAt", now.toString());
        payload.put("number", UUID.randomUUID().toString());
        if (days > 0) {
            payload.put("expiresAt", now.plusDays(days).toString());
        }
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign((PrivateKey) keystore().getKey(ALIAS, null));
        signer.update(body);
        byte[] signature = signer.sign();
        JSONObject outer = new JSONObject();
        if (builtIn) {
            // short form: the app already trusts this phone's key, no certificate needs to travel with the code
            outer.put("k", CertificateVerifier.keyId(publicKey()));
            outer.put("p", Base64.getEncoder().encodeToString(body));
            outer.put("s", Base64.getEncoder().encodeToString(signature));
            return "MC2." + Base64.getUrlEncoder().withoutPadding().encodeToString(outer.toString().getBytes(StandardCharsets.UTF_8));
        }
        outer.put("issuer", new JSONObject(certText));
        outer.put("payload", Base64.getEncoder().encodeToString(body));
        outer.put("signature", Base64.getEncoder().encodeToString(signature));
        return "MC1." + Base64.getUrlEncoder().withoutPadding().encodeToString(outer.toString().getBytes(StandardCharsets.UTF_8));
    }
}
