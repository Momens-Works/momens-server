package works.momens.server.web.task;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.context.EntityRelationReader;
import works.momens.server.context.TaskContextLinks;
import works.momens.server.memory.ConfirmedMemoryDetail;
import works.momens.server.memory.ConfirmedMemoryReader;
import works.momens.server.project.core.ProjectErrorCode;
import works.momens.server.project.core.ProjectReader;
import works.momens.server.project.task.TaskErrorCode;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.WebTaskDetail;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateReader;
import works.momens.server.source.LegacySourceRefDetail;
import works.momens.server.source.SourceRefReader;
import works.momens.server.workspace.WorkspaceAccess;

@Service
@RequiredArgsConstructor
class TaskReadService {
  private final TaskReader taskReader;
  private final ProjectReader projectReader;
  private final TaskUpdateReader taskUpdateReader;
  private final EntityRelationReader entityRelationReader;
  private final ConfirmedMemoryReader confirmedMemoryReader;
  private final SourceRefReader sourceRefReader;
  private final WorkspaceAccess workspaceAccess;

  @Transactional(readOnly = true)
  public List<WebTaskDetail> list(UUID projectId, UUID userId) {
    UUID workspaceId =
        projectReader
            .workspaceIdOf(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    requireMember(workspaceId, userId);
    return taskReader.listWebDetailsByProjectId(projectId);
  }

  @Transactional(readOnly = true)
  public WebTaskDetail get(UUID taskId, UUID userId) {
    WebTaskDetail task = requireTask(taskId);
    requireMember(task.workspaceId(), userId);
    return task;
  }

  @Transactional(readOnly = true)
  public List<TaskUpdateDetail> updates(UUID taskId, UUID userId) {
    WebTaskDetail task = get(taskId, userId);
    return taskUpdateReader.listByTaskId(task.id());
  }

  @Transactional(readOnly = true)
  public TaskContext context(UUID taskId, UUID userId) {
    WebTaskDetail task = get(taskId, userId);
    TaskContextLinks links =
        entityRelationReader.findTaskContextLinks(task.workspaceId(), task.id());
    return new TaskContext(
        task.id(),
        confirmedMemoryReader.findDetailsByIds(task.workspaceId(), links.memoryIds()),
        sourceRefReader.findLegacyDetailsByIds(task.workspaceId(), links.sourceRefIds()));
  }

  private WebTaskDetail requireTask(UUID taskId) {
    return taskReader
        .findWebDetail(taskId)
        .orElseThrow(
            () ->
                new BusinessException(
                    TaskErrorCode.TASK_NOT_FOUND, Map.of("task_id", taskId.toString())));
  }

  private void requireMember(UUID workspaceId, UUID userId) {
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", workspaceId.toString()));
    }
  }

  record TaskContext(
      UUID taskId, List<ConfirmedMemoryDetail> memories, List<LegacySourceRefDetail> sourceRefs) {}
}
