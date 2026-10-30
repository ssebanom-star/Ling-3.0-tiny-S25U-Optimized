package net.classcardauto.mobile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Keeps the reasons runs stopped, newest last, in a small file inside the app's private storage.
 * A record holds only when and why a run stopped (reason code, message, question number, phase, mode). The text of
 * questions, choices or words is never stored.
 */
public final class FailureLog {
    public static final int MAX_RECORDS = 100;
    private static final String FILE = "failures.jsonl";

    private FailureLog() {
    }

    public static final class Record {
        public long time;
        public String code = "UNCLASSIFIED";
        public String message = "";
        public String progress = "";
        public String phase = "";
        public String pace = "";
        public int cards;
    }

    private static File file(File root) {
        return new File(root, FILE);
    }

    static JSONObject toJson(Record r) {
        QuietJson o = new QuietJson();
        o.put("time", r.time).put("code", r.code).put("message", r.message).put("progress", r.progress)
                .put("phase", r.phase).put("pace", r.pace).put("cards", r.cards);
        return o;
    }

    static Record fromJson(JSONObject o) {
        Record r = new Record();
        r.time = o.optLong("time");
        r.code = o.optString("code", "UNCLASSIFIED");
        r.message = o.optString("message", "");
        r.progress = o.optString("progress", "");
        r.phase = o.optString("phase", "");
        r.pace = o.optString("pace", "");
        r.cards = o.optInt("cards");
        return r;
    }

    /** Oldest first, as stored. Unreadable lines are skipped. */
    public static synchronized List<Record> readAll(File root) {
        List<Record> out = new ArrayList<>();
        File f = file(root);
        if (!f.isFile()) {
            return out;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    out.add(fromJson(new JSONObject(line)));
                } catch (Exception skip) {
                    // a damaged line must not hide the rest
                }
            }
        } catch (Exception e) {
            return out;
        }
        return out;
    }

    /** Newest first, for display. */
    public static List<Record> newestFirst(File root) {
        List<Record> all = readAll(root);
        java.util.Collections.reverse(all);
        return all;
    }

    public static synchronized void append(File root, Record record) {
        List<Record> all = readAll(root);
        all.add(record);
        while (all.size() > MAX_RECORDS) {
            all.remove(0);
        }
        File tmp = new File(root, FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            for (Record r : all) {
                out.write((toJson(r).toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        File f = file(root);
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    public static synchronized void clear(File root) {
        file(root).delete();
    }

    /** Rewrites the file keeping only the fields above, so lines written by older builds lose any stored question text. */
    public static synchronized void scrub(File root) {
        File f = file(root);
        if (!f.isFile()) {
            return;
        }
        List<Record> all = readAll(root);
        File tmp = new File(root, FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            for (Record r : all) {
                out.write((toJson(r).toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    /** One line per failure, newest first: time | reason | question | phase | mode | message. Contains no question text. */
    public static String report(List<Record> newestFirst) {
        StringBuilder sb = new StringBuilder();
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        for (Record r : newestFirst) {
            sb.append(fmt.format(new java.util.Date(r.time))).append(" | ")
                    .append(r.code).append(" | ")
                    .append(r.progress.isEmpty() ? "-" : r.progress).append(" | ")
                    .append(r.phase.isEmpty() ? "-" : r.phase).append(" | ")
                    .append(r.pace.isEmpty() ? "-" : r.pace).append(" | ")
                    .append(r.message.replace('\n', ' ')).append('\n');
        }
        return sb.toString();
    }

    /** What to try next, by failure code. */
    public static String hint(String code) {
        switch (code) {
            case "PROMPT_NOT_FOUND":
                return "수집한 단어장에서 제시어를 찾지 못했어요. 화면 인식 오류이거나 단어장에 없는 단어일 수 있어요. ‘화면 이미지로 읽기’ 설정을 바꿔 다시 수집해 보세요.";
            case "CHOICE_NOT_FOUND":
                return "정답에 해당하는 보기를 찾지 못했어요. 뜻 표기가 다르거나 보기가 잘렸을 수 있어요. ‘잘린 선택지 보조 매칭’을 켜 보세요.";
            case "CHOICE_AMBIGUOUS":
                return "정답 후보가 여러 개라 구분하지 못했어요. 같은 뜻의 단어가 단어장에 겹쳐 있을 수 있어요.";
            case "PARTIAL_TOO_SHORT":
                return "잘린 보기와 겹치는 글자가 너무 적어 고를 수 없었어요.";
            case "PARTIAL_TIE":
                return "잘린 보기 중 겹치는 글자 수가 같은 것이 여럿이라 고를 수 없었어요.";
            default:
                return "화면 처리 중 오류로 멈췄어요. 클래스카드 화면이 바뀌었거나 접근성 연결이 끊겼을 수 있어요.";
        }
    }

    /** Short label for the code chip. */
    public static String label(String code) {
        switch (code) {
            case "PROMPT_NOT_FOUND":
                return "제시어 없음";
            case "CHOICE_NOT_FOUND":
                return "정답 보기 없음";
            case "CHOICE_AMBIGUOUS":
                return "정답 모호";
            case "PARTIAL_TOO_SHORT":
                return "잘린 보기 부족";
            case "PARTIAL_TIE":
                return "잘린 보기 동점";
            case "CRASH":
                return "앱 오류(강제 종료)";
            default:
                return "오류";
        }
    }
}
