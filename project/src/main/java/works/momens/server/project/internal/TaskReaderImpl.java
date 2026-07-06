package works.momens.server.project.internal;

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

  /** 보드가 다루는 상태. 레거시 backlog와 cancelled는 보드에 노출하지 않는다(task.md 와이어프레임). */
  private static final List<String> BOARD_STATUSES = List.of("todo", "in_progress", "done");

  private final TaskRepository taskRepository;

  @Override
  @Transactional(readOnly = true)
  public List<BoardTask> listBoardTasks(UUID projectId) {
    return taskRepository
        .findByProjectIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            projectId, BOARD_STATUSES)
        .stream()
        .map(TaskReaderImpl::toBoardTask)
        .toList();
  }

  private static BoardTask toBoardTask(Task task) {
    return new BoardTask(
        task.getId(), task.getTitle(), task.getStatus(), task.getPriority(), task.sortedRoles());
  }
}
