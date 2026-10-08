// 실모델이 툴 목록이 든 프롬프트에 대해 파싱 가능한 툴 호출을 내는지 확인(호스트).
//   java ... ToolCallProbe model.gguf prompt1.txt [prompt2.txt ...]
import io.github.ssebanom.ling.engine.LingNative;
import java.nio.file.Files;
import java.nio.file.Path;

public class ToolCallProbe {
    public static void main(String[] args) throws Exception {
        LingNative.nativeInit("");
        long h = LingNative.nativeCreate();
        String err = LingNative.nativeLoad(h, args[0], new int[]{8192, 512, 512, 4, 4, 50, 0, 8},
            new boolean[]{false, true, false, true, false, false}, "", "", "", p -> true);
        if (err != null) throw new RuntimeException(err);
        // -Dgrammar=file.gbnf [-Dtrigger=regex] : 툴 호출 제약 문법 시험
        String gf = System.getProperty("grammar");
        if (gf != null) LingNative.nativeSetGrammar(h, Files.readString(Path.of(gf)), System.getProperty("trigger"));
        for (int i = 1; i < args.length; i++) {
            String prompt = Files.readString(Path.of(args[i]));
            LingNative.nativeSync(h, new String[]{prompt}, new int[][]{null}, new boolean[]{false}, null);
            StringBuilder sb = new StringBuilder();
            // greedy, 최대 700 토큰
            LingNative.nativeGenerate(h, new float[]{0f, 1f, 0f, 1f}, new int[]{Integer.getInteger("maxgen", 700), 1, 0, 1}, (piece, t) -> { sb.append(piece); return true; });
            System.out.println("=== " + Path.of(args[i]).getFileName());
            System.out.println(sb);
        }
        LingNative.nativeUnload(h);
        LingNative.nativeDestroy(h);
    }
}
