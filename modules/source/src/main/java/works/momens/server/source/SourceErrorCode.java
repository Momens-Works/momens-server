package works.momens.server.source;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * source 도메인에서 사용하는 에러 코드입니다.
 *
 * <p>공통 에러 코드를 재사용하지 않고 도메인 의미가 드러나는 코드를 이 모듈에서 소유합니다. 에러 코드와 HTTP status는 에러 코드 문서의 표와 일치해야 합니다.
 *
 * <p>레거시는 provider가 설정되지 않은 경우 501, provider 호출에 실패한 경우 502로 응답합니다. 신규 서버에서 사용하는 status 목록에는 501이
 * 없으므로 500으로 변환하고, 이 차이는 이관 목록 문서에 기록했습니다. 502는 지원되는 status이며 {@code :auth} 모듈에도 같은 성격의 에러 코드가 있으므로
 * 유지합니다.
 */
@RequiredArgsConstructor
public enum SourceErrorCode implements ErrorCode {
  SOURCE_UNSUPPORTED_PROVIDER(400, "지원하지 않는 provider입니다."),
  SOURCE_PROVIDER_UNCONFIGURED(500, "provider 설정이 서버에 없습니다."),
  SOURCE_OAUTH_INVALID_REQUEST(400, "승인 요청에 필요한 값이 없습니다."),
  SOURCE_OAUTH_INVALID_STATE(400, "승인 요청을 확인할 수 없습니다."),
  SOURCE_OAUTH_EXCHANGE_FAILED(502, "provider와 토큰을 주고받지 못했습니다."),
  SOURCE_REF_NOT_FOUND(404, "source-ref를 찾을 수 없습니다.");

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
