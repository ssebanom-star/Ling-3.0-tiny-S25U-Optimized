package net.classcardauto.auth;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.KeyAgreement;

/**
 * This authenticator's own EC key. On Android 12+ it lives in the Android keystore and cannot be exported; on older
 * versions (or if the keystore refuses) it falls back to a key file in the app's private storage.
 */
public final class AuthKey {
    private static final String ALIAS = "mimic_auth";
    private static final String FILE = "auth-key.json";

    private AuthKey() {
    }

    private static KeyPair keystorePair() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                    .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                    .build());
            generator.generateKeyPair();
        }
        return new KeyPair(store.getCertificate(ALIAS).getPublicKey(), (PrivateKey) store.getKey(ALIAS, null));
    }

    private static KeyPair filePair(Context context) throws Exception {
        File file = new File(context.getFilesDir(), FILE);
        KeyFactory factory = KeyFactory.getInstance("EC");
        if (file.isFile()) {
            String[] parts = new String(Files.readAllBytes(file.toPath()), "UTF-8").split("\n");
            return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(parts[0]))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(parts[1]))));
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();
        Files.write(file.toPath(), (Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n"
                + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())).getBytes("UTF-8"));
        return pair;
    }

    /** True when the private key sits in the hardware-backed keystore (cannot be copied out of the phone). */
    public static boolean inKeystore;

    public static synchronized KeyPair pair(Context context) throws Exception {
        if (Build.VERSION.SDK_INT >= 31 && !new File(context.getFilesDir(), FILE).isFile()) {
            try {
                KeyPair pair = keystorePair();
                inKeystore = true;
                return pair;
            } catch (Throwable ignored) {
                // fall through to the file key
            }
        }
        inKeystore = false;
        return filePair(context);
    }

    /** The public key to give to whoever builds the app (X.509, base64). */
    public static String publicKey(Context context) throws Exception {
        PublicKey key = pair(context).getPublic();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static byte[] agree(Context context, PublicKey peer) throws Exception {
        PrivateKey key = pair(context).getPrivate();
        if (inKeystore) {
            try {
                return AuthMath.agree(key, peer, KeyAgreement.getInstance("ECDH", "AndroidKeyStore"));
            } catch (Throwable ignored) {
                // some providers want the default implementation
            }
        }
        return AuthMath.agree(key, peer, KeyAgreement.getInstance("ECDH"));
    }
}
