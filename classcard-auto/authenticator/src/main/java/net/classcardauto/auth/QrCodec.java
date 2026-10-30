package net.classcardauto.auth;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.EnumMap;
import java.util.Map;

/** QR code encoding and decoding (ZXing core); the pure-array parts are used by the JVM tests. */
public final class QrCodec {
    private QrCodec() {
    }

    /** The QR modules for {@code text} (true = dark), with a quiet zone of 2 modules. Low error correction keeps long codes small. */
    public static boolean[][] matrix(String text) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix bits = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        boolean[][] out = new boolean[bits.getHeight()][bits.getWidth()];
        for (int y = 0; y < bits.getHeight(); y++) {
            for (int x = 0; x < bits.getWidth(); x++) {
                out[y][x] = bits.get(x, y);
            }
        }
        return out;
    }

    /** Grey pixels (ARGB) of the code scaled to about {@code size} px, whole modules only. */
    public static int[] pixels(boolean[][] modules, int size, int[] outSide) {
        int scale = Math.max(1, size / modules.length);
        int side = modules.length * scale;
        int[] px = new int[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                px[y * side + x] = modules[y / scale][x / scale] ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        outSide[0] = side;
        return px;
    }

    public static Bitmap render(String text, int size) throws Exception {
        int[] side = new int[1];
        int[] px = pixels(matrix(text), size, side);
        return Bitmap.createBitmap(px, side[0], side[0], Bitmap.Config.ARGB_8888);
    }

    /** Text of the QR code in the image, or null when none is found. */
    public static String decodePixels(int[] pixels, int width, int height) {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "ISO-8859-1");
        try {
            return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))), hints).getText();
        } catch (Exception e) {
            return null;
        }
    }

    public static String decode(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        return decodePixels(px, w, h);
    }
}
