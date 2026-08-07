package works.momens.server.project.task;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.ApplyTaskDraftCommand;
import works.momens.server.project.TaskDraftApplier;
import works.momens.server.project.TaskDraftApplyResult;
import works.momens.server.project.TaskDraftValues;

@Service
@RequiredArgsConstructor
class TaskDraftApplierImpl implements TaskDraftApplier {

  private final TaskRepository taskRepository;

  /**
   * 기본 전파로 호출자 트랜잭션에 합류합니다. {@code TaskEditorImpl.update}와 같은 방식이며, 여기에 별도 경계를 만들면 호출자의 원장 종료와 원자성이
   * 깨집니다.
   *
   * <p>조건 검사와 갱신을 native bulk update가 아니라 <b>행 잠금 후 엔티티 변경</b>으로 합니다. bulk update는 JPA Auditing을
   * 우회해 {@code tasks.updated_at}이 갱신되지 않고, 그러면 수정 테이블의 {@code updated_at}을 Auditing으로 관리하는
   * 규칙(persistence 규칙)과 어긋납니다. 잠금은 조건 검사와 갱신 사이에 사용자 편집이 끼어드는 것도 함께 막습니다.
   */
  @Override
  @Transactional
  public TaskDraftApplyResult apply(ApplyTaskDraftCommand command) {
    Task task = taskRepository.lockByIdAndDeletedAtIsNull(command.taskId()).orElse(null);
    if (task == null) {
      // 없는 것과 삭제된 것을 구분하지 않습니다. 어느 쪽이든 반영할 대상이 없고, 호출자가 취할 행동도 같습니다.
      return TaskDraftApplyResult.TASK_GONE;
    }
    TaskDraftValues baseline = command.baseline();
    if (!task.matchesDraft(baseline.title(), baseline.role(), baseline.priority())) {
      return TaskDraftApplyResult.BASELINE_MISMATCH;
    }
    TaskDraftValues draft = command.draft();
    task.applyDraft(draft.title(), draft.role(), draft.priority());
    return TaskDraftApplyResult.APPLIED;
  }
}
