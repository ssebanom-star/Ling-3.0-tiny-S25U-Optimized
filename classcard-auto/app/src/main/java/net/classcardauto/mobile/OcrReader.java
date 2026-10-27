package net.classcardauto.mobile;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.FloatBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.TuplesKt;
import kotlin.Unit;
import kotlin.collections.ArraysKt;
import kotlin.collections.CollectionsKt;
import kotlin.collections.IntIterator;
import kotlin.collections.MapsKt;
import kotlin.io.ByteStreamsKt;
import kotlin.io.CloseableKt;
import kotlin.io.ConstantsKt;
import kotlin.io.TextStreamsKt;
import kotlin.jdk7.AutoCloseableKt;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.math.MathKt;
import kotlin.ranges.IntRange;
import kotlin.ranges.RangesKt;
import kotlin.text.Charsets;
import org.json.JSONArray;
import org.json.JSONObject;

/* compiled from: OcrReader.kt */
/* loaded from: classes.dex */
public final class OcrReader implements Closeable {
    public static final String MODEL = "Qota-OCR";
    public static final String SHA256 = "27ccd15d20a753652273a49e36b75530477687738dd503edce5f4e2b819a4d36";

    /* renamed from: characters$delegate, reason: from kotlin metadata */
    private final Lazy characters;
    private final Context context;
    private final OrtEnvironment environment;
    private OrtSession session;

    /* compiled from: OcrReader.kt */
    @Metadata(d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0014\n\u0002\u0010\b\n\u0002\b\u0002\b\u0086\b\u0018\u00002\u00020\u0001B5\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\f\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0007\u0012\u0006\u0010\b\u001a\u00020\u0007\u0012\u0006\u0010\t\u001a\u00020\n¢\u0006\u0004\b\u000b\u0010\fJ\t\u0010\u0016\u001a\u00020\u0003HÆ\u0003J\u000f\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005HÆ\u0003J\t\u0010\u0018\u001a\u00020\u0007HÆ\u0003J\t\u0010\u0019\u001a\u00020\u0007HÆ\u0003J\t\u0010\u001a\u001a\u00020\nHÆ\u0003JA\u0010\u001b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\u000e\b\u0002\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00072\b\b\u0002\u0010\b\u001a\u00020\u00072\b\b\u0002\u0010\t\u001a\u00020\nHÆ\u0001J\u0013\u0010\u001c\u001a\u00020\n2\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u001e\u001a\u00020\u001fHÖ\u0001J\t\u0010 \u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000eR\u0017\u0010\u0004\u001a\b\u0012\u0004\u0012\u00020\u00030\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\u0010R\u0011\u0010\u0006\u001a\u00020\u0007¢\u0006\b\n\u0000\u001a\u0004\b\u0011\u0010\u0012R\u0011\u0010\b\u001a\u00020\u0007¢\u0006\b\n\u0000\u001a\u0004\b\u0013\u0010\u0012R\u0011\u0010\t\u001a\u00020\n¢\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015¨\u0006!"}, d2 = {"Lnet/classcardauto/mobile/OcrReader$Read;", "", "text", "", "lines", "", "confidence", "", "inferenceMs", "clipped", "", "<init>", "(Ljava/lang/String;Ljava/util/List;DDZ)V", "getText", "()Ljava/lang/String;", "getLines", "()Ljava/util/List;", "getConfidence", "()D", "getInferenceMs", "getClipped", "()Z", "component1", "component2", "component3", "component4", "component5", "copy", "equals", "other", "hashCode", "", "toString", "app_release"}, k = 1, mv = {2, 1, 0}, xi = 48)
    /* loaded from: classes.dex */
    public static final /* data */ class Read {
        private final boolean clipped;
        private final double confidence;
        private final double inferenceMs;
        private final List<String> lines;
        private final String text;

        public Read(String text, List<String> lines, double d, double d2, boolean z) {
            Intrinsics.checkNotNullParameter(text, "text");
            Intrinsics.checkNotNullParameter(lines, "lines");
            this.text = text;
            this.lines = lines;
            this.confidence = d;
            this.inferenceMs = d2;
            this.clipped = z;
        }

        public static /* synthetic */ Read copy$default(Read read, String str, List list, double d, double d2, boolean z, int i, Object obj) {
            if ((i & 1) != 0) {
                str = read.text;
            }
            if ((i & 2) != 0) {
                list = read.lines;
            }
            if ((i & 4) != 0) {
                d = read.confidence;
            }
            if ((i & 8) != 0) {
                d2 = read.inferenceMs;
            }
            if ((i & 16) != 0) {
                z = read.clipped;
            }
            boolean z2 = z;
            double d3 = d2;
            return read.copy(str, list, d, d3, z2);
        }


        public final List<String> component2() {
            return this.lines;
        }




        public final Read copy(String text, List<String> lines, double confidence, double inferenceMs, boolean clipped) {
            Intrinsics.checkNotNullParameter(text, "text");
            Intrinsics.checkNotNullParameter(lines, "lines");
            return new Read(text, lines, confidence, inferenceMs, clipped);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Read)) {
                return false;
            }
            Read read = (Read) other;
            return Intrinsics.areEqual(this.text, read.text) && Intrinsics.areEqual(this.lines, read.lines) && Double.compare(this.confidence, read.confidence) == 0 && Double.compare(this.inferenceMs, read.inferenceMs) == 0 && this.clipped == read.clipped;
        }

        public final boolean getClipped() {
            return this.clipped;
        }

        public final double getConfidence() {
            return this.confidence;
        }

        public final double getInferenceMs() {
            return this.inferenceMs;
        }

        public final List<String> getLines() {
            return this.lines;
        }

        public final String getText() {
            return this.text;
        }

        public int hashCode() {
            return (((((((this.text.hashCode() * 31) + this.lines.hashCode()) * 31) + Double.hashCode(this.confidence)) * 31) + Double.hashCode(this.inferenceMs)) * 31) + Boolean.hashCode(this.clipped);
        }

        public String toString() {
            return "Read(text=" + this.text + ", lines=" + this.lines + ", confidence=" + this.confidence + ", inferenceMs=" + this.inferenceMs + ", clipped=" + this.clipped + ")";
        }
    }

    public OcrReader(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        this.context = context;
        this.environment = OrtEnvironment.getEnvironment();
        this.characters = LazyKt.lazy(new Function0() { // from class: net.classcardauto.mobile.OcrReader$$ExternalSyntheticLambda2
            @Override // kotlin.jvm.functions.Function0
            public final Object invoke() {
                List characters_delegate$lambda$2;
                characters_delegate$lambda$2 = OcrReader.characters_delegate$lambda$2(OcrReader.this);
                return characters_delegate$lambda$2;
            }
        });
    }

    public static final List characters_delegate$lambda$2(OcrReader ocrReader) {
        try {
            String readText;
            try (InputStream open = ocrReader.context.getAssets().open("qota-ocr/preprocessor_config.json");
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(open, Charsets.UTF_8), ConstantsKt.DEFAULT_BUFFER_SIZE)) {
                readText = TextStreamsKt.readText(bufferedReader);
            }
            JSONArray jSONArray = new JSONObject(readText).getJSONArray("character_list");
            ArrayList arrayList = new ArrayList();
            for (int i = 0; i < jSONArray.length(); i++) {
                arrayList.add(jSONArray.getString(i));
            }
            return arrayList;
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final List<String> getCharacters() {
        return (List) this.characters.getValue();
    }

    private final OrtSession getSession() {
        OrtSession ortSession = this.session;
        if (ortSession != null) {
            return ortSession;
        }
        try {
            File file = new File(this.context.getFilesDir(), "ocr/qota-27ccd15d20a753652273a49e36b75530477687738dd503edce5f4e2b819a4d36.onnx");
            File parentFile = file.getParentFile();
            Intrinsics.checkNotNull(parentFile);
            parentFile.mkdirs();
            if (!file.exists()) {
                File file2 = new File(file.getParentFile(), "model.tmp");
                try (InputStream inputStream = this.context.getAssets().open("qota-ocr/model.onnx");
                     FileOutputStream fileOutputStream = new FileOutputStream(file2)) {
                    ByteStreamsKt.copyTo(inputStream, fileOutputStream, 8192);
                }
                if (!file2.renameTo(file)) {
                    throw new IllegalStateException("OCR 모델 저장 실패".toString());
                }
            }
            MessageDigest messageDigest = Sneaky.sha256();
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] bArr = new byte[65536];
                while (true) {
                    int read = fileInputStream.read(bArr);
                    if (read < 0) {
                        break;
                    }
                    messageDigest.update(bArr, 0, read);
                }
            }
            byte[] digest = messageDigest.digest();
            Intrinsics.checkNotNullExpressionValue(digest, "digest(...)");
            if (!Intrinsics.areEqual(ArraysKt.joinToString(digest, (CharSequence) "", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", new Function1() {
                @Override
                public final Object invoke(Object obj) {
                    return OcrReader.getSession$lambda$8(((Byte) obj).byteValue());
                }
            }), SHA256)) {
                throw new IllegalStateException("OCR 모델 무결성 확인 실패".toString());
            }
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            try {
                sessionOptions.setIntraOpNumThreads(4);
                sessionOptions.setInterOpNumThreads(1);
                this.session = this.environment.createSession(file.getAbsolutePath(), sessionOptions);
            } finally {
                sessionOptions.close();
            }
            OrtSession ortSession2 = this.session;
            Intrinsics.checkNotNull(ortSession2);
            return ortSession2;
        } catch (java.io.IOException | ai.onnxruntime.OrtException e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final CharSequence getSession$lambda$8(byte b) {
        String format = String.format("%02x", Arrays.copyOf(new Object[]{Byte.valueOf(b)}, 1));
        Intrinsics.checkNotNullExpressionValue(format, "format(...)");
        return format;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final CharSequence readDetailed$lambda$16(CtcRead it) {
        Intrinsics.checkNotNullParameter(it, "it");
        return it.getText();
    }

    public void close() {
        try {
            OrtSession ortSession = this.session;
            if (ortSession != null) {
                ortSession.close();
            }
            this.session = null;
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    public final String read(Bitmap bitmap) {
        Intrinsics.checkNotNullParameter(bitmap, "bitmap");
        return readDetailed(bitmap).getText();
    }

    public final Read readDetailed(Bitmap bitmap) {
        try {
            return readDetailed0(bitmap);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final Read readDetailed0(Bitmap bitmap) throws Exception {
        Intrinsics.checkNotNullParameter(bitmap, "bitmap");
        int[] iArr = new int[bitmap.getWidth() * bitmap.getHeight()];
        bitmap.getPixels(iArr, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        List<Bounds> lines = OcrLayout.INSTANCE.lines(bitmap.getWidth(), bitmap.getHeight(), iArr);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("카드 이미지에서 글자 줄을 찾지 못했습니다.".toString());
        }
        ArrayList arrayList = new ArrayList();
        Iterator it = lines.iterator();
        double d = 0.0d;
        while (it.hasNext()) {
            Bounds bounds = (Bounds) it.next();
            Bitmap createBitmap = Bitmap.createBitmap(bitmap, bounds.getLeft(), bounds.getTop(), bounds.getWidth(), bounds.getHeight());
            Intrinsics.checkNotNullExpressionValue(createBitmap, "createBitmap(...)");
            try {
                int coerceIn = RangesKt.coerceIn(MathKt.roundToInt((createBitmap.getWidth() * 48.0d) / createBitmap.getHeight()), 1, 3200);
                Bitmap createScaledBitmap = Bitmap.createScaledBitmap(createBitmap, coerceIn, 48, true);
                Intrinsics.checkNotNullExpressionValue(createScaledBitmap, "createScaledBitmap(...)");
                try {
                    int[] iArr2 = new int[coerceIn * 48];
                    createScaledBitmap.getPixels(iArr2, 0, coerceIn, 0, 0, coerceIn, 48);
                    int max = Math.max(320, coerceIn);
                    int i = max * 48;
                    float[] fArr = new float[max * 144];
                    int i2 = 0;
                    for (int i3 = 48; i2 < i3; i3 = 48) {
                        int i4 = 0;
                        while (i4 < coerceIn) {
                            int i5 = iArr2[(i2 * coerceIn) + i4];
                            int i6 = (i2 * max) + i4;
                            fArr[i6] = (Color.blue(i5) / 127.5f) - 1.0f;
                            fArr[i + i6] = (Color.green(i5) / 127.5f) - 1.0f;
                            fArr[(max * 96) + i6] = (Color.red(i5) / 127.5f) - 1.0f;
                            i4++;
                            it = it;
                        }
                        i2++;
                    }
                    Iterator it2 = it;
                    OrtSession session = getSession();
                    OcrReader ocrReader = this;
                    OnnxTensor inputTensor = OnnxTensor.createTensor(ocrReader.environment, FloatBuffer.wrap(fArr), new long[]{1, 3, 48, max});
                    try {
                        long nanoTime = System.nanoTime();
                        OrtSession.Result createTensor = session.run(MapsKt.mapOf(TuplesKt.to("x", inputTensor)));
                        try {
                            d += (System.nanoTime() - nanoTime) / 1000000.0d;
                            OnnxValue onnxValue = createTensor.get(0);
                            Intrinsics.checkNotNull(onnxValue, "null cannot be cast to non-null type ai.onnxruntime.OnnxTensor");
                            OnnxTensor onnxTensor = (OnnxTensor) onnxValue;
                            long[] shape = onnxTensor.getInfo().getShape();
                            if (shape.length != 3 || shape[0] != 1 || shape[2] != ocrReader.getCharacters().size()) {
                                throw new IllegalArgumentException("OCR 출력 형식이 다릅니다.".toString());
                            }
                            FloatBuffer floatBuffer = onnxTensor.getFloatBuffer();
                            int i7 = (int) shape[1];
                            int[] iArr3 = new int[i7];
                            float[] fArr2 = new float[i7];
                            int i8 = 0;
                            while (i8 < i7) {
                                int size = ocrReader.getCharacters().size() * i8;
                                float f = floatBuffer.get(size);
                                int size2 = ocrReader.getCharacters().size();
                                int i9 = i7;
                                int i10 = 1;
                                float f2 = f;
                                int i11 = 0;
                                while (i10 < size2) {
                                    int i12 = size2;
                                    float f3 = floatBuffer.get(size + i10);
                                    if (f3 > f2) {
                                        f2 = f3;
                                        i11 = i10;
                                    }
                                    i10++;
                                    size2 = i12;
                                }
                                iArr3[i8] = i11;
                                fArr2[i8] = f2;
                                i8++;
                                ocrReader = this;
                                i7 = i9;
                            }
                            arrayList.add(CtcDecode.INSTANCE.decode(iArr3, fArr2, getCharacters()));
                            it = it2;
                        } finally {
                            createTensor.close();
                        }
                    } finally {
                        inputTensor.close();
                    }
                } finally {
                    if (createScaledBitmap != createBitmap) {
                        createScaledBitmap.recycle();
                    }
                }
            } finally {
                if (createBitmap != bitmap) {
                    createBitmap.recycle();
                }
            }
        }
        ArrayList arrayList2 = arrayList;
        String joinToString$default = CollectionsKt.joinToString(arrayList2, (CharSequence) "\n", (CharSequence) "", (CharSequence) "", -1, (CharSequence) "...", new Function1() { // from class: net.classcardauto.mobile.OcrReader$$ExternalSyntheticLambda0
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                CharSequence readDetailed$lambda$16;
                readDetailed$lambda$16 = OcrReader.readDetailed$lambda$16((CtcRead) obj);
                return readDetailed$lambda$16;
            }
        });
        ArrayList arrayList3 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList2, 10));
        Iterator it3 = arrayList2.iterator();
        while (it3.hasNext()) {
            arrayList3.add(((CtcRead) it3.next()).getText());
        }
        ArrayList arrayList4 = arrayList3;
        ArrayList arrayList5 = new ArrayList(CollectionsKt.collectionSizeOrDefault(arrayList2, 10));
        Iterator it4 = arrayList2.iterator();
        while (it4.hasNext()) {
            arrayList5.add(Double.valueOf(((CtcRead) it4.next()).getConfidence()));
        }
        return new Read(joinToString$default, arrayList4, CollectionsKt.averageOfDouble(arrayList5), d, OcrLayout.INSTANCE.verticallyClipped(bitmap.getWidth(), bitmap.getHeight(), iArr));
    }
}
