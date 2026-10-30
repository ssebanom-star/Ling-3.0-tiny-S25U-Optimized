package net.classcardauto.mobile;

import java.util.Random;

/** Round trip: text -> QR modules -> pixels (as a photo would be) -> text. Uses the pure-array parts of QrCodec. */
public class QrCodecTest {
    static int pass = 0, fail = 0;
    static void ok(String name, boolean cond) { if (cond) pass++; else { fail++; System.out.println("FAIL " + name); } }

    static String roundTrip(String text, int size, int noise) throws Exception {
        boolean[][] m = QrCodec.matrix(text);
        int[] side = new int[1];
        int[] px = QrCodec.pixels(m, size, side);
        if (noise > 0) {
            Random r = new Random(5);
            for (int i = 0; i < px.length; i++) {
                int g = px[i] & 0xFF;
                g = Math.max(0, Math.min(255, g + r.nextInt(noise * 2 + 1) - noise));
                px[i] = 0xFF000000 | (g << 16) | (g << 8) | g;
            }
        }
        return QrCodec.decodePixels(px, side[0], side[0]);
    }

    public static void main(String[] a) throws Exception {
        ok("device qr", ("MCDEV:f881a45eedbd33af").equals(roundTrip("MCDEV:f881a45eedbd33af", 400, 0)));
        // a code the size of a short activation code
        StringBuilder sb = new StringBuilder("MC2.");
        Random r = new Random(1);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        while (sb.length() < 560) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        String code = sb.toString();
        ok("short activation code qr", code.equals(roundTrip(code, 900, 0)));
        ok("survives sensor noise", code.equals(roundTrip(code, 900, 40)));
        ok("module count stays moderate", QrCodec.matrix(code).length <= 110);
        // the long certificate-carrying code is much bigger
        StringBuilder lg = new StringBuilder("MC1.");
        while (lg.length() < 2200) lg.append(alphabet.charAt(r.nextInt(alphabet.length())));
        ok("long code still encodes", lg.toString().equals(roundTrip(lg.toString(), 1800, 0)));
        System.out.println("long code modules: " + QrCodec.matrix(lg.toString()).length + ", short: " + QrCodec.matrix(code).length);
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
