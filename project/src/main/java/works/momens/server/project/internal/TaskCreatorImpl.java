package works.momens.server.project.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.TaskCreator;
import works.momens.server.workspace.LabelAllocator;

@Service
@RequiredArgsConstructor
class TaskCreatorImpl implements TaskCreator {

  /** 모바일에서 새로 만든 태스크는 backlog가 아니라 todo 그룹에서 시작한다(명세 R-ACTION-002). */
  private static final String INITIAL_STATUS = "todo";

  private final TaskRepository taskRepository;
  private final LabelAllocator labelAllocator;

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
            .roles(command.roles())
            .build();
    taskRepository.save(task);
    return new CreatedTask(
        task.getId(),
        task.getProjectId(),
        task.getTitle(),
        task.sortedRoles(),
        task.getPriority(),
        task.getStatus());
  }
}
