package works.momens.server.minsu.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.MinsuTaskDraft;

/** draft JSON 파싱·정규화 검증(Vertex 호출 없이 순수 로직만). Client는 파싱 경로에서 쓰지 않아 null로 둔다. */
class GeminiMinsuParseDraftTest {

  private final GeminiMinsu minsu = new GeminiMinsu(null, "gemini-2.5-flash", new ObjectMapper());

  @Test
  @DisplayName("정상 JSON은 title·role·priority를 그대로 파싱한다")
  void parsesWellFormedJson() {
    MinsuTaskDraft draft =
        minsu.parseDraft(
            "{\"title\":\"권한 이탈 점검\",\"role\":\"backend\",\"priority\":\"high\"}", "폴백 제목");

    assertThat(draft.title()).isEqualTo("권한 이탈 점검");
    assertThat(draft.role()).isEqualTo("backend");
    assertThat(draft.priority()).isEqualTo("high");
  }

  @Test
  @DisplayName("허용되지 않은 role·priority는 기본값(pm·medium)으로 정규화한다")
  void normalizesInvalidChoicesToDefaults() {
    MinsuTaskDraft draft =
        minsu.parseDraft(
            "{\"title\":\"제목\",\"role\":\"marketing\",\"priority\":\"urgent\"}", "폴백 제목");

    assertThat(draft.role()).isEqualTo("pm");
    assertThat(draft.priority()).isEqualTo("medium");
  }

  @Test
  @DisplayName("role·priority 대소문자·공백을 정규화한다")
  void normalizesCaseAndWhitespace() {
    MinsuTaskDraft draft =
        minsu.parseDraft("{\"title\":\"제목\",\"role\":\" Design \",\"priority\":\"LOW\"}", "폴백 제목");

    assertThat(draft.role()).isEqualTo("design");
    assertThat(draft.priority()).isEqualTo("low");
  }

  @Test
  @DisplayName("title이 비면 Signal 제목으로 되돌린다")
  void fallsBackToSignalTitleWhenBlank() {
    MinsuTaskDraft draft =
        minsu.parseDraft("{\"title\":\"  \",\"role\":\"pm\",\"priority\":\"medium\"}", "폴백 제목");

    assertThat(draft.title()).isEqualTo("폴백 제목");
  }

  @Test
  @DisplayName("코드블록·설명이 섞여 와도 첫 JSON object만 뽑아 파싱한다")
  void stripsMarkdownFencesAroundJson() {
    MinsuTaskDraft draft =
        minsu.parseDraft(
            "```json\n{\"title\":\"제목\",\"role\":\"frontend\",\"priority\":\"high\"}\n```",
            "폴백 제목");

    assertThat(draft.role()).isEqualTo("frontend");
    assertThat(draft.title()).isEqualTo("제목");
  }
}
