package works.momens.server.project.task;

import java.util.HashMap;
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

  private static final String EVENT_TASK_CREATED = "task.created";

  private final TaskRepository taskRepository;
  private final LabelAllocator labelAllocator;
  private final OutboxAppender outboxAppender;

  @Override
  @Transactional
  public CreatedTask create(CreateTaskCommand command) {
    // 라벨 발급(MANDATORY 트랜잭션)과 저장을 한 트랜잭션으로 묶어, 실패 시 발급 번호까지 함께 되돌린다.
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
            .origin(command.origin())
            .originSignalId(command.originSignalId())
            .build();
    taskRepository.save(task);
    // task.created는 manual/signal 모든 생성 경로에서 발행한다(CO-6). 같은 트랜잭션에 합류시켜
    // task insert와 outbox insert가 원자로 커밋되게 한다(SD-5).
    Map<String, Object> payload = new HashMap<>();
    payload.put("origin_type", task.getOriginType());
    payload.put(
        "origin_signal_id",
        task.getOriginSignalId() == null ? null : task.getOriginSignalId().toString());
    outboxAppender.append(
        task.getWorkspaceId(), "task", task.getId().toString(), EVENT_TASK_CREATED, payload);
    return new CreatedTask(
        task.getId(),
        task.getProjectId(),
        task.getTitle(),
        task.getRole(),
        task.getPriority(),
        task.getStatus());
  }
}
