package works.momens.server.signal.action;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.TaskCreator;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalSnapshot;

/**
 * Signal action의 원자 쓰기 트랜잭션.
 *
 * <p>convert-to-task는 {@code tasks insert + signal_actions insert}, dismiss는 {@code signal_actions
 * insert}만 한 트랜잭션에 커밋한다. facade({@link SignalActionServiceImpl})와 빈을 분리해 self-invocation 없이
 * {@code @Transactional} 프록시가 걸리게 한다. MOM-66의 outbox 이벤트 insert는 이 메서드들에 추가되는 것으로 같은 트랜잭션에 합류한다.
 */
@Component
@RequiredArgsConstructor
class SignalActionExecutor {

  private final SignalActionRepository signalActionRepository;
  private final TaskCreator taskCreator;

  @Transactional
  SignalActionResult convert(
      SignalSnapshot signal, UUID userId, String title, String role, String priority) {
    CreatedTask created =
        taskCreator.create(
            new CreateTaskCommand(signal.projectId(), signal.workspaceId(), title, role, priority));
    signalActionRepository.save(
        SignalAction.builder()
            .workspaceId(signal.workspaceId())
            .signalId(signal.id())
            .actionType("convert_to_task")
            .resultTaskId(created.id())
            .processedByUserId(userId)
            .build());
    return new SignalActionResult(
        signal.id(),
        "convert_to_task",
        true,
        new SignalActionResult.TaskResult(created.id(), created.title(), created.status()));
  }

  @Transactional
  SignalActionResult dismiss(SignalSnapshot signal, UUID userId) {
    signalActionRepository.save(
        SignalAction.builder()
            .workspaceId(signal.workspaceId())
            .signalId(signal.id())
            .actionType("dismiss")
            .processedByUserId(userId)
            .build());
    return new SignalActionResult(signal.id(), "dismiss", true, null);
  }
}
