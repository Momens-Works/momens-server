package works.momens.server.project.task.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.core.ProjectReader;
import works.momens.server.project.task.BoardTask;
import works.momens.server.project.task.TaskDetail;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskScope;
import works.momens.server.project.task.TaskSnapshot;

@Service
@RequiredArgsConstructor
class TaskReaderImpl implements TaskReader {

  private final TaskRepository taskRepository;
  private final ProjectReader projectReader;

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
    // checklistItems와 openQuestions는 LAZY 컬렉션이라 이 트랜잭션 안에서 보조 SELECT로 초기화된다.
    return taskRepository.findByIdAndDeletedAtIsNull(taskId).map(TaskDetailMapper::toDetail);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<TaskScope> findScope(UUID taskId) {
    return taskRepository.findScopeById(taskId);
  }

  /**
   * 웹 상세 조회입니다. 소프트 삭제된 태스크와 소속 프로젝트를 모두 제외합니다.
   *
   * <p>응답의 workspace는 태스크가 가진 값이 아니라 소속 프로젝트의 workspace를 기준으로 합니다. 레거시 웹 상세가 프로젝트를 조인해 workspace를
   * 판정하므로 인가 기준도 같은 값을 씁니다.
   */
  @Override
  @Transactional(readOnly = true)
  public Optional<TaskSnapshot> findSnapshot(UUID taskId) {
    return taskRepository
        .findByIdAndDeletedAtIsNull(taskId)
        .flatMap(
            task ->
                projectReader
                    .workspaceIdOf(task.getProjectId())
                    .map(
                        workspaceId ->
                            TaskSnapshotMapper.toProjectWorkspaceSnapshot(task, workspaceId)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TaskSnapshot> listSnapshotsByProjectId(UUID projectId) {
    return taskRepository.findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId).stream()
        .map(TaskSnapshotMapper::toTaskWorkspaceSnapshot)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<TaskSnapshot> listSnapshotsByWorkspaceId(UUID workspaceId) {
    return taskRepository
        .findByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId)
        .stream()
        .map(TaskSnapshotMapper::toTaskWorkspaceSnapshot)
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
}
