package works.momens.server.minsu.draft.ledger;

import java.util.ArrayList;
import java.util.List;
import works.momens.server.project.task.ApplyTaskDraftCommand;
import works.momens.server.project.task.TaskDraftApplier;
import works.momens.server.project.task.TaskDraftApplyResult;

/**
 * 반영 결과를 지정하고 호출을 기록하는 {@code project} 반영 API 대역.
 *
 * <p>실제 CAS를 여기서 재현하지 않는다. {@code minsu} 슬라이스가 볼 것은 반영 결과를 종료 사유와 event 발행으로 옮기는 매핑이고, CAS 자체는
 * {@code project}의 테스트와 app 레벨 통합 테스트가 본다.
 */
class FakeTaskDraftApplier implements TaskDraftApplier {

  private final List<ApplyTaskDraftCommand> commands = new ArrayList<>();
  private TaskDraftApplyResult result = TaskDraftApplyResult.APPLIED;
  private RuntimeException failure;

  @Override
  public TaskDraftApplyResult apply(ApplyTaskDraftCommand command) {
    commands.add(command);
    if (failure != null) {
      throw failure;
    }
    return result;
  }

  void willReturn(TaskDraftApplyResult result) {
    this.result = result;
  }

  void willThrow(RuntimeException failure) {
    this.failure = failure;
  }

  List<ApplyTaskDraftCommand> commands() {
    return List.copyOf(commands);
  }

  void reset() {
    commands.clear();
    result = TaskDraftApplyResult.APPLIED;
    failure = null;
  }
}
