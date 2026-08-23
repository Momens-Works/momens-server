package works.momens.server.context;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * context 기능에서 사용하는 에러 코드입니다.
 *
 * <p>연결 생성 또는 삭제 요청이 실패하는 경우는 두 가지입니다. 연결할 두 대상이 서로 다른 워크스페이스에 속하거나 삭제할 연결이 이미 없는 경우입니다. 공통 에러 코드로도
 * 응답할 수 있지만 실패 원인이 코드에 드러나도록 별도 코드로 정의합니다.
 *
 * <p>HTTP status는 레거시와 동일하게 유지합니다. 서로 다른 워크스페이스의 대상을 연결하면 400, 존재하지 않는 연결을 삭제하면 404로 응답합니다.
 */
@RequiredArgsConstructor
public enum ContextErrorCode implements ErrorCode {
  CONTEXT_CROSS_WORKSPACE_LINK_NOT_ALLOWED(400, "서로 다른 워크스페이스의 대상은 연결할 수 없습니다."),
  CONTEXT_LINK_NOT_FOUND(404, "연결을 찾을 수 없습니다.");

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
