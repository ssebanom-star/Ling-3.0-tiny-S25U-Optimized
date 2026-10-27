package net.classcardauto.mobile;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class FailureLogTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    static FailureLog.Record rec(long t, String code) {
        FailureLog.Record r = new FailureLog.Record();
        r.time = t;
        r.code = code;
        r.message = "msg " + t;
        r.progress = "3 | 20";
        r.phase = "test";
        r.pace = "human";
        r.cards = 20;
        return r;
    }

    public static void main(String[] a) throws Exception {
        File dir = Files.createTempDirectory("faillog").toFile();
        ok("empty read", FailureLog.readAll(dir).isEmpty());

        FailureLog.append(dir, rec(1, "PROMPT_NOT_FOUND"));
        FailureLog.append(dir, rec(2, "CHOICE_AMBIGUOUS"));
        List<FailureLog.Record> got = FailureLog.readAll(dir);
        ok("two records", got.size() == 2);
        ok("order oldest first", got.get(0).time == 1 && got.get(1).time == 2);
        ok("fields", got.get(0).code.equals("PROMPT_NOT_FOUND") && got.get(0).progress.equals("3 | 20") && got.get(0).cards == 20 && got.get(0).pace.equals("human") && got.get(0).phase.equals("test"));
        ok("newest first", FailureLog.newestFirst(dir).get(0).time == 2);

        // the stored file never holds question text, only the listed fields
        String raw = new String(Files.readAllBytes(new File(dir, "failures.jsonl").toPath()), "UTF-8");
        ok("only metadata keys", !raw.contains("prompt") && !raw.contains("choices") && !raw.contains("nearest"));

        // a damaged line is skipped, the others survive
        File f = new File(dir, "failures.jsonl");
        Files.write(f.toPath(), (raw + "{not json\n").getBytes("UTF-8"));
        ok("damaged line skipped", FailureLog.readAll(dir).size() == 2);

        // lines written by an older build with question text are rewritten without it
        String legacy = "{\"time\":5,\"code\":\"PROMPT_NOT_FOUND\",\"message\":\"m\",\"progress\":\"1 | 2\",\"phase\":\"test\",\"pace\":\"fast\",\"cards\":3,"
                + "\"prompt\":\"approach\",\"choices\":[\"접근하다\",\"승인하다\"],\"nearest\":[\"approach → 접근하다 (86%)\"]}\n";
        Files.write(f.toPath(), legacy.getBytes("UTF-8"));
        FailureLog.scrub(dir);
        String scrubbed = new String(Files.readAllBytes(f.toPath()), "UTF-8");
        ok("scrub removes question text", !scrubbed.contains("approach") && !scrubbed.contains("접근하다") && scrubbed.contains("PROMPT_NOT_FOUND"));
        ok("scrub keeps the record", FailureLog.readAll(dir).size() == 1 && FailureLog.readAll(dir).get(0).time == 5);

        // keeps only the newest MAX_RECORDS
        for (int i = 10; i < 10 + FailureLog.MAX_RECORDS + 20; i++) {
            FailureLog.append(dir, rec(i, "UNCLASSIFIED"));
        }
        List<FailureLog.Record> capped = FailureLog.readAll(dir);
        ok("capped at max", capped.size() == FailureLog.MAX_RECORDS);
        ok("keeps newest", capped.get(capped.size() - 1).time == 10 + FailureLog.MAX_RECORDS + 19 && capped.get(0).time > 10);
        ok("no temp file left", !new File(dir, "failures.jsonl.tmp").exists());

        String report = FailureLog.report(FailureLog.newestFirst(dir));
        ok("report one line per record", report.split("\n").length == FailureLog.MAX_RECORDS && report.contains("UNCLASSIFIED | 3 | 20 | test | human | msg"));

        FailureLog.clear(dir);
        ok("cleared", FailureLog.readAll(dir).isEmpty());
        ok("scrub on missing file is a no-op", noThrowScrub(dir));

        ok("hint per code", FailureLog.hint("PROMPT_NOT_FOUND").contains("제시어") && FailureLog.hint("???").contains("오류"));
        ok("label per code", FailureLog.label("CHOICE_AMBIGUOUS").equals("정답 모호") && FailureLog.label("x").equals("오류"));
        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }

    static boolean noThrowScrub(File dir) {
        try {
            FailureLog.scrub(dir);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
