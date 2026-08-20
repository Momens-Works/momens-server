package works.momens.server.project.task;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.BoardTask;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;
import works.momens.server.project.WebTaskDetail;

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
  public Map<String, Long> countByStatus(UUID projectId, Collection<String> statuses) {
    return taskRepository.countByStatus(projectId, statuses).stream()
        .collect(Collectors.toMap(StatusCount::status, StatusCount::count));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TaskDetail> findDetail(UUID taskId) {
    // checklistItems와 openQuestions는 LAZY 컬렉션이라 이 트랜잭션 안에서 보조 SELECT로 초기화된다.
    return taskRepository.findByIdAndDeletedAtIsNull(taskId).map(TaskDetailMapper::toDetail);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> workspaceIdOf(UUID taskId) {
    return taskRepository.findWorkspaceIdById(taskId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WebTaskDetail> findWebDetail(UUID taskId) {
    return taskRepository
        .findWebDetailById(taskId)
        .flatMap(
            task ->
                taskRepository
                    .findWebProjectWorkspaceIdByTaskId(taskId)
                    .map(workspaceId -> toWebTaskDetail(task, workspaceId)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<WebTaskDetail> listWebDetailsByProjectId(UUID projectId) {
    return taskRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).stream()
        .map(TaskReaderImpl::toWebTaskDetail)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<WebTaskDetail> listWebDetailsByWorkspaceId(UUID workspaceId) {
    return taskRepository
        .findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId)
        .stream()
        .map(TaskReaderImpl::toWebTaskDetail)
        .toList();
  }

  private static BoardTask toBoardTask(Task task) {
    return new BoardTask(
        task.getId(),
        task.getTitle(),
        task.getStatus(),
        task.getPriority(),
        task.getRole(),
        task.getCreatedAt());
  }

  private static WebTaskDetail toWebTaskDetail(Task task) {
    return toWebTaskDetail(task, task.getWorkspaceId());
  }

  private static WebTaskDetail toWebTaskDetail(Task task, UUID workspaceId) {
    return new WebTaskDetail(
        task.getId(),
        workspaceId,
        task.getProjectId(),
        task.getMilestoneId(),
        task.getLabel(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getAssigneeId(),
        task.getDueDate(),
        task.getCreatedAt(),
        task.getUpdatedAt());
  }
}
