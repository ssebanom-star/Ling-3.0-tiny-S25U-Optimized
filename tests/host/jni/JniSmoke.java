// 호스트 JVM 에서 실제 Kotlin 컴파일 결과(LingNative.class)를 통해 JNI 를 호출해
// 함수 이름/시그니처/UTF-8 처리/콜백이 맞는지 실모델로 검증한다.
// 실행: tools/run_host_jni_test.sh
import io.github.ssebanom.ling.engine.LingNative;
import io.github.ssebanom.ling.engine.NativeCallbacks;
import java.util.Arrays;

public class JniSmoke {
    static int fail = 0;
    static void check(boolean ok, String msg) {
        System.out.println((ok ? "  [ OK ] " : "  [FAIL] ") + msg);
        if (!ok) fail++;
    }

    public static void main(String[] args) {
        String model = args[0];
        LingNative n = LingNative.INSTANCE;
        // args[1]: 동적 백엔드 디렉터리(앱의 nativeLibraryDir 역할). 없으면 정적 링크 빌드
        String backendDir = args.length > 1 ? args[1] : "";
        LingNative.nativeInit(backendDir);
        String[] devs = LingNative.nativeDevices();
        System.out.println("devices: " + Arrays.toString(devs));
        check(devs.length >= 1 && devs[0].startsWith("CPU"), "CPU backend registered");
        if (!backendDir.isEmpty()) check(!LingNative.nativeLoadBackend("opencl"), "absent backend load returns false");
        long h = LingNative.nativeCreate();
        final float[] lastProgress = {0};
        String err = LingNative.nativeLoad(h, model,
            new int[]{4096, 512, 512, 4, 4, 50, 0, 8},
            new boolean[]{false, true, false, true, false, false /* x86 AMX repack off */},
            "", "", p -> { lastProgress[0] = p; return true; });
        check(err == null, "nativeLoad " + err);
        check(lastProgress[0] > 0.99f, "load progress callback reached 1.0");
        String[] info = LingNative.nativeModelInfo(h);
        System.out.println("model: " + Arrays.toString(info));

        String ko = "안녕하세요 😀 Ling";
        check(ko.equals(LingNative.nativeDetokenize(h, LingNative.nativeTokenize(h, ko))), "UTF-8 round trip incl. emoji");

        String sys = "<role>SYSTEM</role>detailed thinking off<|role_end|>";
        String user = "<role>HUMAN</role>한국의 수도는? 한 단어로.<|role_end|>";
        String gp = "<role>ASSISTANT</role>\n<think></think>";
        final int[] prog = {0, 0};
        double[] s = LingNative.nativeSync(h, new String[]{sys, user, gp}, new int[][]{null, null, null},
            new boolean[]{true, false, false}, (d, t) -> { prog[0] = d; prog[1] = t; });
        check(s[0] == 1.0 && prog[0] == prog[1] && prog[1] > 0, "nativeSync + prefill progress " + Arrays.toString(s));

        StringBuilder sb = new StringBuilder();
        int[] toks = LingNative.nativeGenerate(h, new float[]{0f, 1f, 0f, 1f}, new int[]{32, 20, 64, 1},
            (piece, t) -> { sb.append(piece); return true; });
        double[] g = LingNative.nativeLastGenerate(h);
        System.out.println("answer: " + sb + "  stats=" + Arrays.toString(g));
        check(toks.length > 0 && sb.toString().equals(LingNative.nativeDetokenize(h, toks)), "streamed text == detokenized tokens");
        check(sb.toString().contains("서울") || sb.toString().toLowerCase().contains("seoul"), "answer mentions Seoul");

        // 다음 턴: 생성 토큰을 그대로 넣어 캐시 재사용
        double[] s2 = LingNative.nativeSync(h,
            new String[]{sys, user, gp, null, "<|role_end|>", "<role>HUMAN</role>일본은?<|role_end|>", gp},
            new int[][]{null, null, null, toks, null, null, null},
            new boolean[]{true, false, false, false, true, false, false}, null);
        check(s2[0] == 1.0 && s2[2] >= s[1] + toks.length, "second turn reuses previous prompt + generated tokens " + Arrays.toString(s2));
        long[] st = LingNative.nativeState(h);
        System.out.println("state: " + Arrays.toString(st));
        check(st[0] >= 1, "checkpoints exist");

        float[] top = LingNative.nativeEvalTopK(h, LingNative.nativeTokenize(h, sys + user + gp), 5);
        check(top.length == 10 && top[1] <= 0f && top[1] >= top[3], "eval top-k log-probs sorted");

        double[] b = LingNative.nativeBench(h, 64, 16, 1);
        System.out.println("bench pp64 " + b[0] + " tg16 " + b[1]);
        check(b[0] > 0 && b[1] > 0, "bench");

        LingNative.nativeSetThreads(h, 2, 4, "0,1");
        double[] b2 = LingNative.nativeBench(h, 0, 8, 1);
        check(b2[1] > 0, "setThreads with cpumask then decode");

        LingNative.nativeUnload(h);
        LingNative.nativeDestroy(h);
        System.out.println(fail == 0 ? "ALL PASSED" : ("FAILED " + fail));
        System.exit(fail == 0 ? 0 : 1);
    }
}
