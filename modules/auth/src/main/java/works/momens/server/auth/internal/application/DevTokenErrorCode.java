package works.momens.server.auth.internal.application;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * dev 전용 토큰 발급 엔드포인트의 에러 코드. 공용 {@code AuthErrorCode}와 분리합니다.
 *
 * <p>공용 enum에 두면 {@code @ApiExceptions(AuthErrorCode.class)}를 쓰는 기존 인증 API의 OpenAPI 문서에도 dev 전용 코드가
 * 예제로 섞입니다(SwaggerErrorExampleGenerator가 enum의 모든 상수를 예제로 추가). 별도 enum으로 두면 dev 토큰 문서에서만 참조됩니다.
 */
@RequiredArgsConstructor
public enum DevTokenErrorCode implements ErrorCode {
  AUTH_DEV_TOKEN_SECRET_INVALID(401, "dev 토큰 시크릿이 유효하지 않습니다."),
  AUTH_DEV_TOKEN_EMAIL_NOT_ALLOWED(403, "허용되지 않은 테스트 사용자입니다.");

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
