package works.momens.server.web.task;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskSnapshot;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateReader;
import works.momens.server.project.taskupdate.TaskUpdateWriter;
import works.momens.server.workspace.WorkspaceAccess;

@Service
@RequiredArgsConstructor
class TaskUpdateService {
  private final TaskReader taskReader;
  private final TaskUpdateReader taskUpdateReader;
  private final TaskUpdateWriter taskUpdateWriter;
  private final WorkspaceAccess workspaceAccess;

  @Transactional(readOnly = true)
  List<TaskUpdateDetail> list(UUID taskId, UUID userId) {
    TaskSnapshot task =
        taskReader
            .findSnapshot(taskId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        TaskErrorCode.TASK_NOT_FOUND, Map.of("task_id", taskId.toString())));
    if (!workspaceAccess.isMember(task.workspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", task.workspaceId().toString()));
    }
    return taskUpdateReader.listByTaskId(task.id());
  }

  TaskUpdateDetail create(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata) {
    return taskUpdateWriter.create(taskId, userId, body, kind, metadata);
  }

  void delete(UUID taskId, UUID updateId, UUID userId) {
    taskUpdateWriter.delete(taskId, updateId, userId);
  }
}
