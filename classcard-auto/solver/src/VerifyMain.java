package net.classcardauto.mobile;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Runs the app's real CertificateVerifier on a key file: VerifyMain <file> <deviceId> <package> [phoneRootKey,...]. */
public class VerifyMain {
    public static void main(String[] a) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        try {
            CertificateVerifier v = new CertificateVerifier();
            if (a.length > 3 && !a[3].isEmpty()) {
                v.withPhoneRoots(a[3].split(","));
            }
            CertifiedDocument d = v.verify(text, a[1], a[2]);
            System.out.println("OK number=" + d.getNumber());
        } catch (Throwable t) {
            System.out.println("REJECT " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
