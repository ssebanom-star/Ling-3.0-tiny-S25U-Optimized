package net.classcardauto.mobile;

import java.util.Arrays;
import java.util.List;

/* compiled from: Core.kt */
public enum AnswerFailure {
    PROMPT_NOT_FOUND("제시어를 수집 자료에서 찾지 못했습니다. 화면 원문과 OCR 인식 결과를 확인해 주세요."),
    CHOICE_NOT_FOUND("제시어에 대응하는 보기를 찾지 못했습니다."),
    CHOICE_AMBIGUOUS("여러 보기가 자료와 일치해 답을 구분할 수 없습니다."),
    PARTIAL_TOO_SHORT("잘린 선택지와 일치하는 글자가 부족합니다."),
    PARTIAL_TIE("잘린 선택지의 일치 글자 수가 동점입니다.");

    private final String explanation;

    AnswerFailure(String explanation) {
        this.explanation = explanation;
    }

    public static List<AnswerFailure> getEntries() {
        return Arrays.asList(values());
    }

    public final String getExplanation() {
        return this.explanation;
    }
}
