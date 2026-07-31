package works.momens.server.signal;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * signal 도메인 에러 코드.
 *
 * <p>공통 코드를 재사용하지 않고 도메인 의미가 드러나는 코드를 모듈이 소유합니다(docs/spec/api-response-error-codes.md). 코드·status는
 * spec 코드표와 맞춥니다. {@code SIGNAL_INVALID_STATE}(409)는 이미 다른 action으로 처리된 Signal에 다른 action을 요청할 때
 * 반환합니다. 같은 action 재요청은 에러가 아니라 200 멱등 응답입니다.
 */
@RequiredArgsConstructor
public enum SignalErrorCode implements ErrorCode {
  SIGNAL_NOT_FOUND(404, "시그널을 찾을 수 없습니다."),
  SIGNAL_INVALID_STATE(409, "이미 다른 action으로 처리된 시그널입니다.");

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
