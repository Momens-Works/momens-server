package works.momens.server.user;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * user 도메인 에러 코드.
 *
 * <p>공통 코드를 재사용하지 않고 도메인 의미가 드러나는 코드를 모듈이 소유합니다(docs/spec/api-response-error-codes.md). 코드·status는
 * spec 코드표와 맞춥니다.
 */
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
  USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다."),
  USER_EMAIL_LINKED_TO_ANOTHER_IDENTITY(409, "이미 다른 계정에 연결된 이메일입니다.");

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
