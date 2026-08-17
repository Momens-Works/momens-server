package works.momens.server.workspace;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/**
 * workspace 도메인 에러 코드.
 *
 * <p>공통 코드를 재사용하지 않고 도메인 의미가 드러나는 코드를 모듈이 소유합니다(docs/spec/api-response-error-codes.md). 코드·status는
 * spec 코드표와 맞춥니다. {@link WorkspaceReader}는 Optional만 반환하므로, 이 코드를 던질지는 호출하는 쪽이 결정합니다.
 */
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements ErrorCode {
  WORKSPACE_NOT_FOUND(404, "워크스페이스를 찾을 수 없습니다.");

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
