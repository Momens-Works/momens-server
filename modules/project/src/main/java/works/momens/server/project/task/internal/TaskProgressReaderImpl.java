package works.momens.server.project.task.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.core.ProjectReader;
import works.momens.server.project.task.TaskProgressReader;
import works.momens.server.project.task.TaskStatus;

@Service
@RequiredArgsConstructor
class TaskProgressReaderImpl implements TaskProgressReader {

  private static final List<String> PROGRESS_STATUSES =
      Arrays.stream(TaskStatus.values())
          .filter(status -> status != TaskStatus.CANCELLED)
          .map(TaskStatus::value)
          .toList();

  private final ProjectReader projectReader;
  private final TaskRepository taskRepository;

  @Override
  @Transactional(readOnly = true)
  public OptionalInt progressOf(UUID projectId) {
    if (projectReader.workspaceIdOf(projectId).isEmpty()) {
      return OptionalInt.empty();
    }
    Map<String, Long> countsByStatus =
        taskRepository.countByStatus(projectId, PROGRESS_STATUSES).stream()
            .collect(Collectors.toMap(StatusCount::status, StatusCount::count));
    long total = countsByStatus.values().stream().mapToLong(Long::longValue).sum();
    if (total == 0) {
      return OptionalInt.of(0);
    }
    long done = countsByStatus.getOrDefault(TaskStatus.DONE.value(), 0L);
    return OptionalInt.of((int) (done * 100 / total));
  }
}
