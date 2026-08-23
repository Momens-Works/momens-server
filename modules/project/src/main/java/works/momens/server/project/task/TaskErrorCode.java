package works.momens.server.project.task;

import lombok.RequiredArgsConstructor;
import works.momens.server.common.api.ErrorCode;

/** task 도메인 에러 코드. */
@RequiredArgsConstructor
public enum TaskErrorCode implements ErrorCode {
  TASK_NOT_FOUND(404, "태스크를 찾을 수 없습니다."),
  TASK_CHECKLIST_ITEM_NOT_FOUND(404, "완료기준 항목을 찾을 수 없습니다.");

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
