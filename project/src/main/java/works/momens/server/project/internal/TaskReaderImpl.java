package works.momens.server.project.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.BoardTask;
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

  private static BoardTask toBoardTask(Task task) {
    return new BoardTask(
        task.getId(), task.getTitle(), task.getStatus(), task.getPriority(), task.sortedRoles());
  }
}
