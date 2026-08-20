package works.momens.server.project.task;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.TaskUpdateDetail;
import works.momens.server.project.TaskUpdateReader;

@Service
@RequiredArgsConstructor
class TaskUpdateReaderImpl implements TaskUpdateReader {

  private final TaskUpdateRepository taskUpdateRepository;

  @Override
  @Transactional(readOnly = true)
  public List<TaskUpdateDetail> listByTaskId(UUID taskId) {
    return taskUpdateRepository
        .findByTaskIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(taskId)
        .stream()
        .map(
            update ->
                new TaskUpdateDetail(
                    update.getId(),
                    update.getWorkspaceId(),
                    update.getProjectId(),
                    update.getTaskId(),
                    update.getAuthorId(),
                    update.getBody(),
                    update.getKind(),
                    update.getMetadata(),
                    update.getCreatedAt(),
                    update.getUpdatedAt()))
        .toList();
  }
}
