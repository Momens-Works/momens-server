package works.momens.server.signal.action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.context.DevEntityRelationWriter;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.DevTaskDetailWriter;
import works.momens.server.project.TaskCreator;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalReader;

/**
 * Signal action의 원자 쓰기 트랜잭션.
 *
 * <p>convert-to-task는 {@code tasks insert + signal_actions insert + outbox_events insert(2건: task
 * 생성이 남기는 task.created, 이 메서드가 남기는 signal.converted_to_task)}, dismiss는 {@code signal_actions
 * insert + outbox_events insert(signal.dismissed)}를 한 트랜잭션에 커밋한다(CO-6). facade({@link
 * SignalActionServiceImpl})와 빈을 분리해 self-invocation 없이 {@code @Transactional} 프록시가 걸리게 한다.
 */
@Component
@RequiredArgsConstructor
class SignalActionExecutor {

  private static final String DEMO_SIGNAL_TITLE = "할인 쿠폰 적용 실패 문의가 오늘 27건 접수됐습니다";

  private static final String DEMO_TASK_TITLE = "쿠폰 실패 안내 개선";

  private static final String DEMO_TASK_DESCRIPTION = "고객이 결제 전에 쿠폰 적용 가능 여부와 실패 이유를 이해할 수 있게 한다.";

  private static final String DEMO_NEXT_ACTION =
      "고객 문의 27건을 실패 원인별로 분류하고, 가장 많은 두 원인의 안내 문구와 사전 노출 위치를 먼저 확정하세요.";

  private static final List<String> DEMO_CHECKLIST_ITEMS =
      List.of(
          "쿠폰 제외 브랜드와 최소 주문 금액 확정", "결제 전에 쿠폰 적용 가능 여부 표시", "실패 원인별 안내 문구 적용", "고객센터 응대 가이드 공유");

  private static final List<String> DEMO_OPEN_QUESTIONS =
      List.of("쿠폰 제외 브랜드를 상품 상세에서도 안내할까요?", "쿠폰 실패 시 사용 가능한 다른 쿠폰을 추천할까요?");

  private static final String AGGREGATE_SIGNAL = "signal";

  private static final String EVENT_SIGNAL_CONVERTED_TO_TASK = "signal.converted_to_task";

  private static final String EVENT_SIGNAL_DISMISSED = "signal.dismissed";

  private final SignalActionRepository signalActionRepository;
  private final TaskCreator taskCreator;
  private final OutboxAppender outboxAppender;
  private final SignalReader signalReader;
  private final Optional<DevTaskDetailWriter> devTaskDetailWriter;
  private final Optional<DevEntityRelationWriter> devEntityRelationWriter;

  @Transactional
  SignalActionResult convert(
      SignalReader.Snapshot signal, UUID userId, String title, String role, String priority) {
    DevTaskDetailWriter taskDetailWriter = devTaskDetailWriter.orElse(null);
    DevEntityRelationWriter relationWriter = devEntityRelationWriter.orElse(null);
    boolean isDemo =
        DEMO_SIGNAL_TITLE.equals(signal.title())
            && taskDetailWriter != null
            && relationWriter != null;
    String taskTitle = isDemo ? DEMO_TASK_TITLE : title;
    String taskRole = isDemo ? "pm" : role;
    String taskPriority = isDemo ? "high" : priority;
    CreatedTask created =
        taskCreator.create(
            CreateTaskCommand.fromSignal(
                signal.projectId(),
                signal.workspaceId(),
                taskTitle,
                taskRole,
                taskPriority,
                signal.id()));
    if (isDemo) {
      taskDetailWriter.enrich(
          created.id(),
          userId,
          DEMO_TASK_DESCRIPTION,
          DEMO_NEXT_ACTION,
          DEMO_CHECKLIST_ITEMS,
          DEMO_OPEN_QUESTIONS);
      relationWriter.linkTaskMaterials(
          signal.workspaceId(), created.id(), signalReader.findEvidenceSourceRefIds(signal.id()));
    }
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
        EVENT_SIGNAL_CONVERTED_TO_TASK,
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
        signal.workspaceId(),
        AGGREGATE_SIGNAL,
        signal.id().toString(),
        EVENT_SIGNAL_DISMISSED,
        Map.of());
    return new SignalActionResult(signal.id(), SignalActionType.DISMISS.value(), true, null);
  }
}
