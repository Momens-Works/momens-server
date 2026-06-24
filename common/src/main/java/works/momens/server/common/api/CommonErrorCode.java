package works.momens.server.common.api;

import lombok.RequiredArgsConstructor;

/**
 * 도메인에 속하지 않는 공통 에러 코드.
 *
 * <p>코드·HTTP status는 docs/spec/api-response-error-codes.md의 공통 에러 코드표와 1:1로 맞춥니다. 도메인 의미가 드러나는 에러는
 * 이 코드를 재사용하지 않고 도메인 모듈이 자기 코드를 추가합니다.
 *
 * <p>{@code AUTH_*} 코드는 규격상 함께 정의하지만, 인증/인가 거부 본문 배선(SecurityFilterChain의 entry point/handler)은
 * 인증/인가 구현 시점(MOM-8)에 추가합니다.
 */
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
  COMMON_BAD_REQUEST(400, "요청 형식이 올바르지 않습니다."),
  COMMON_VALIDATION_FAILED(400, "요청 값이 유효하지 않습니다."),
  AUTH_UNAUTHORIZED(401, "인증이 필요합니다."),
  AUTH_INVALID_TOKEN(401, "토큰이 유효하지 않습니다."),
  AUTH_FORBIDDEN(403, "권한이 없습니다."),
  COMMON_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
  COMMON_METHOD_NOT_ALLOWED(405, "허용되지 않은 HTTP 메서드입니다."),
  COMMON_CONFLICT(409, "요청이 현재 상태와 충돌합니다."),
  COMMON_UNSUPPORTED_MEDIA_TYPE(415, "지원하지 않는 미디어 타입입니다."),
  COMMON_INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),
  COMMON_BAD_GATEWAY(502, "외부 서비스 호출에 실패했습니다.");

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
