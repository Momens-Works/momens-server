package works.momens.server.minsu;

/**
 * 민수 생성 능력의 공개 API.
 *
 * <p>Signal 컨텍스트로부터 태스크 draft와 상세 suggestion을 생성합니다. 구현체는 Vertex AI(Gemini)를 호출하며, 미설정·실패 시 {@link
 * MinsuErrorCode}를 던집니다(하드 의존, 목 폴백 없음).
 */
public interface Minsu {

  /**
   * convert-to-task 시점의 task draft(title·role·priority)를 생성합니다(ADR-0011). role은 {@code
   * pm/design/backend/frontend}, priority는 {@code low/medium/high} 중 하나로 정규화되어 반환됩니다.
   */
  MinsuTaskDraft draftTask(MinsuSignalContext context);

  /** Signal 상세 화면에 노출할 민수 제안 문구를 생성합니다. */
  String suggest(MinsuSignalContext context);
}
