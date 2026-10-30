package net.classcardauto.auth;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.InputStream;

/**
 * Reads a QR code from a photo: the system camera app takes the picture (no camera permission needed here), or on old
 * Android versions the user picks a photo. The temporary picture is deleted right after it has been read.
 */
public final class QrCapture {
    public static final int REQUEST = 4711;
    private static Uri pending;

    private QrCapture() {
    }

    /** Starts the camera (Android 10+) or the photo picker; the result goes to {@link #handle}. */
    public static void start(Activity activity) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "mimic-auth-qr-" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MiMiC-Auth");
            pending = activity.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (pending != null) {
                Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(MediaStore.EXTRA_OUTPUT, pending);
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivityForResult(camera, REQUEST);
                return;
            }
        }
        pending = null;
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.setType("image/*");
        activity.startActivityForResult(Intent.createChooser(pick, "QR이 찍힌 사진 선택"), REQUEST);
    }

    /** The QR text from an {@code onActivityResult}, or null (cancelled, no code found). */
    public static String handle(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST) {
            return null;
        }
        Uri uri = pending;
        pending = null;
        try {
            if (resultCode != Activity.RESULT_OK) {
                return null;
            }
            Uri source = uri != null ? uri : (data != null ? data.getData() : null);
            return source == null ? null : decode(activity, source);
        } finally {
            if (uri != null) {
                try {
                    activity.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String decode(Activity activity, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2200) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, options);
            }
            return bitmap == null ? null : QrCodec.decode(bitmap);
        } catch (Exception e) {
            return null;
        }
    }
}
