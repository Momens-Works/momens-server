package works.momens.server.memory;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

@RequiredArgsConstructor
public enum MemoryErrorCode implements ErrorCode {
  MEMORY_CANDIDATE_NOT_FOUND(404, "메모리 후보를 찾을 수 없습니다."),
  MEMORY_NOT_FOUND(404, "메모리를 찾을 수 없습니다."),
  MEMORY_INVALID_STATE(409, "현재 메모리 상태에서는 요청을 처리할 수 없습니다."),
  MEMORY_INVALID_INPUT(400, "메모리 요청이 유효하지 않습니다.");

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
