package works.momens.server.project.task;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.BoardTask;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;

@Service
@RequiredArgsConstructor
class TaskReaderImpl implements TaskReader {

  private final TaskRepository taskRepository;

  @Override
  @Transactional(readOnly = true)
  public List<BoardTask> listTasksByStatus(UUID projectId, Collection<String> statuses) {
    return taskRepository
        .findByProjectIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(projectId, statuses)
        .stream()
        .map(TaskReaderImpl::toBoardTask)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TaskDetail> findDetail(UUID taskId) {
    // checklistItems는 LAZY 컬렉션이라 이 트랜잭션 안에서 보조 SELECT로 초기화된다.
    return taskRepository.findByIdAndDeletedAtIsNull(taskId).map(TaskDetailMapper::toDetail);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> workspaceIdOf(UUID taskId) {
    return taskRepository.findWorkspaceIdById(taskId);
  }

  private static BoardTask toBoardTask(Task task) {
    return new BoardTask(
        task.getId(), task.getTitle(), task.getStatus(), task.getPriority(), task.getRole());
  }
}
