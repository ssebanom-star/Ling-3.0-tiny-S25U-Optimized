package net.classcardauto.mobile;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Runs the app's real CertificateVerifier on a key file: VerifyMain <file> <deviceId> <package>. */
public class VerifyMain {
    public static void main(String[] a) throws Exception {
        String text = new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8");
        try {
            CertifiedDocument d = new CertificateVerifier().verify(text, a[1], a[2]);
            System.out.println("OK number=" + d.getNumber());
        } catch (Throwable t) {
            System.out.println("REJECT " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }
}
