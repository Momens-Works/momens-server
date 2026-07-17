package works.momens.server.minsu;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * 민수 도메인 에러 코드.
 *
 * <p>민수는 하드 의존이라 미설정·실패 시 목값 폴백 없이 에러를 던집니다. 코드·status는 docs/spec/api-response-error-codes.md 코드표와
 * 맞춥니다. {@code MINSU_UNAVAILABLE}(503)는 민수가 비활성이거나 배선되지 않은 환경에서, {@code
 * MINSU_GENERATION_FAILED}(502)는 Vertex 호출·응답 파싱이 실패했을 때 반환합니다.
 */
@RequiredArgsConstructor
public enum MinsuErrorCode implements ErrorCode {
  MINSU_UNAVAILABLE(503, "민수를 사용할 수 없습니다."),
  MINSU_GENERATION_FAILED(502, "민수 응답 생성에 실패했습니다.");

  private final int status;
  private final String defaultMessage;

  @Override
  public String code() {
    return name();
  }

  @Override
  public int status() {
    return status;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }
}
