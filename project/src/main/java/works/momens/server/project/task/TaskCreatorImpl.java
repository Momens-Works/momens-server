package works.momens.server.project.task;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.TaskCreator;
import works.momens.server.workspace.LabelAllocator;

@Service
@RequiredArgsConstructor
class TaskCreatorImpl implements TaskCreator {

  /** 모바일에서 새로 만든 태스크는 backlog가 아니라 todo 그룹에서 시작한다(명세 R-ACTION-002). */
  private static final String INITIAL_STATUS = "todo";

  private static final String AGGREGATE_TASK = "task";
  private static final String EVENT_TASK_CREATED = "task.created";

  private final TaskRepository taskRepository;
  private final LabelAllocator labelAllocator;
  private final OutboxAppender outboxAppender;

  @Override
  @Transactional
  public CreatedTask create(CreateTaskCommand command) {
    // 라벨 발급(MANDATORY 트랜잭션)과 저장, task.created 발행을 한 트랜잭션으로 묶어, 실패 시 함께 되돌린다(SD-5).
    String label = labelAllocator.allocateMomLabel(command.workspaceId());
    Task task =
        Task.builder()
            .workspaceId(command.workspaceId())
            .projectId(command.projectId())
            .label(label)
            .title(command.title())
            .status(INITIAL_STATUS)
            .priority(command.priority())
            .role(command.role())
            .originType(command.origin().value())
            .originSignalId(command.originSignalId())
            .build();
    taskRepository.save(task);
    // 일반 보드 생성(manual)과 convert-to-task(signal) 양쪽이 이 경로를 지나므로 task.created를 여기서 발행한다.
    outboxAppender.append(
        command.workspaceId(),
        AGGREGATE_TASK,
        task.getId().toString(),
        EVENT_TASK_CREATED,
        taskCreatedPayload(command));
    return new CreatedTask(
        task.getId(),
        task.getProjectId(),
        task.getTitle(),
        task.getRole(),
        task.getPriority(),
        task.getStatus());
  }

  private Map<String, Object> taskCreatedPayload(CreateTaskCommand command) {
    // origin_signal_id는 manual일 때 null이라 null 값을 허용하는 LinkedHashMap을 쓴다(Map.of 불가).
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("origin_type", command.origin().value());
    payload.put(
        "origin_signal_id",
        command.originSignalId() == null ? null : command.originSignalId().toString());
    return payload;
  }
}
