package works.momens.server.signal.action;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.TaskCreator;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalReader;

/**
 * Signal action의 원자 쓰기 트랜잭션.
 *
 * <p>convert-to-task는 {@code tasks insert + signal_actions insert + outbox insert}, dismiss는 {@code
 * signal_actions insert + outbox insert}를 한 트랜잭션에 커밋한다. facade({@link SignalActionServiceImpl})와 빈을
 * 분리해 self-invocation 없이 {@code @Transactional} 프록시가 걸리게 한다. task.created는 {@code TaskCreator}가
 * task 생성 트랜잭션에서 발행하고, 이 executor는 signal aggregate 이벤트(converted_to_task·dismissed)를 같은 트랜잭션에
 * 합류시킨다.
 */
@Component
@RequiredArgsConstructor
class SignalActionExecutor {

  private static final String AGGREGATE_SIGNAL = "signal";
  private static final String EVENT_CONVERTED_TO_TASK = "signal.converted_to_task";
  private static final String EVENT_DISMISSED = "signal.dismissed";

  private final SignalActionRepository signalActionRepository;
  private final TaskCreator taskCreator;
  private final OutboxAppender outboxAppender;

  @Transactional
  SignalActionResult convert(
      SignalReader.Snapshot signal, UUID userId, String title, String role, String priority) {
    CreatedTask created =
        taskCreator.create(
            CreateTaskCommand.fromSignal(
                signal.projectId(), signal.workspaceId(), title, role, priority, signal.id()));
    signalActionRepository.save(
        SignalAction.builder()
            .workspaceId(signal.workspaceId())
            .signalId(signal.id())
            .actionType(SignalActionType.CONVERT_TO_TASK.value())
            .resultTaskId(created.id())
            .processedByUserId(userId)
            .build());
    outboxAppender.append(
        signal.workspaceId(),
        AGGREGATE_SIGNAL,
        signal.id().toString(),
        EVENT_CONVERTED_TO_TASK,
        Map.of("task_id", created.id().toString()));
    return new SignalActionResult(
        signal.id(),
        SignalActionType.CONVERT_TO_TASK.value(),
        true,
        new SignalActionResult.TaskResult(created.id(), created.title(), created.status()));
  }

  @Transactional
  SignalActionResult dismiss(SignalReader.Snapshot signal, UUID userId) {
    signalActionRepository.save(
        SignalAction.builder()
            .workspaceId(signal.workspaceId())
            .signalId(signal.id())
            .actionType(SignalActionType.DISMISS.value())
            .processedByUserId(userId)
            .build());
    outboxAppender.append(
        signal.workspaceId(), AGGREGATE_SIGNAL, signal.id().toString(), EVENT_DISMISSED, Map.of());
    return new SignalActionResult(signal.id(), SignalActionType.DISMISS.value(), true, null);
  }
}
