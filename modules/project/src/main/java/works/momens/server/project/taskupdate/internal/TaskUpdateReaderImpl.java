package works.momens.server.project.taskupdate.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateReader;

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
        .map(TaskUpdate::toDetail)
        .toList();
  }
}
