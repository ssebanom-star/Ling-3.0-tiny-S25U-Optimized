exec(open('/tmp/claude-0/work/fix.py').read())
P=D+'OcrReader.java'
replace(P,'public static final List characters_delegate$lambda$2(OcrReader ocrReader)','''    public static final List characters_delegate$lambda$2(OcrReader ocrReader) {
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
    }''')
replace(P,'public void close()','''    public void close() {
        try {
            OrtSession ortSession = this.session;
            if (ortSession != null) {
                ortSession.close();
            }
            this.session = null;
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }''')
s=open(P).read()
s=s.replace('public final Read readDetailed(Bitmap bitmap) {','''public final Read readDetailed(Bitmap bitmap) {
        try {
            return readDetailed0(bitmap);
        } catch (Exception e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }
    }

    private final Read readDetailed0(Bitmap bitmap) throws Exception {''',1)
s=s.replace('''        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | ai.onnxruntime.OrtException e) {
            throw new IllegalStateException(e);
        }''','''        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | ai.onnxruntime.OrtException e) {
            throw Sneaky.<RuntimeException>rethrow(e);
        }''',1)
open(P,'w').write(s)
print('ok')
