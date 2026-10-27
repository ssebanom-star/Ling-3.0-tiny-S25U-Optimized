exec(open('/tmp/claude-0/work/fix.py').read())
O=D+'OcrReader.java'
replace(O,'private final OrtSession getSession()','''    private final OrtSession getSession() {
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
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
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
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | ai.onnxruntime.OrtException e) {
            throw new IllegalStateException(e);
        }
    }''')
print('ok')
