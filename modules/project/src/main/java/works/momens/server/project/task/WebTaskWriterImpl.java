package works.momens.server.project.task;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.TaskUpdateDetail;
import works.momens.server.project.WebTaskDetail;
import works.momens.server.project.WebTaskWriter;
import works.momens.server.workspace.LabelAllocator;
import works.momens.server.workspace.WorkspaceAccess;

@Service
@RequiredArgsConstructor
class WebTaskWriterImpl implements WebTaskWriter {

  private static final String EVENT_TASK_CREATED = "task.created";

  private final TaskRepository taskRepository;
  private final TaskUpdateRepository taskUpdateRepository;
  private final WorkspaceAccess workspaceAccess;
  private final LabelAllocator labelAllocator;
  private final OutboxAppender outboxAppender;

  @Override
  @Transactional
  public WebTaskDetail create(
      UUID projectId,
      UUID userId,
      String title,
      String description,
      String status,
      UUID milestoneId,
      String priority,
      UUID assigneeId,
      LocalDate dueDate) {
    UUID workspaceId =
        taskRepository.findWorkspaceIdByProjectId(projectId).orElseThrow(this::projectNotFound);
    requireMember(workspaceId, userId);
    if (title == null || title.isBlank()) {
      throw validation("title");
    }
    validateReferences(workspaceId, projectId, milestoneId, assigneeId);
    Task task =
        Task.web(
            workspaceId,
            projectId,
            labelAllocator.allocateMomLabel(workspaceId),
            title,
            description,
            normalizeStatus(status, true),
            normalizePriority(priority, true),
            milestoneId,
            assigneeId,
            dueDate);
    taskRepository.save(task);
    Map<String, Object> payload = new HashMap<>();
    payload.put("origin_type", task.getOriginType());
    payload.put("origin_signal_id", null);
    outboxAppender.append(
        workspaceId, "task", task.getId().toString(), EVENT_TASK_CREATED, payload);
    return toDetail(task);
  }

  @Override
  @Transactional
  public WebTaskDetail update(
      UUID taskId,
      UUID userId,
      String title,
      boolean titleSet,
      String description,
      boolean descriptionSet,
      String status,
      boolean statusSet,
      String priority,
      boolean prioritySet,
      UUID milestoneId,
      boolean milestoneSet,
      UUID assigneeId,
      boolean assigneeSet,
      LocalDate dueDate,
      boolean dueDateSet) {
    Task task = requireTask(taskId);
    requireMember(task.getWorkspaceId(), userId);
    if (titleSet && (title == null || title.isBlank())) {
      throw validation("title");
    }
    if (milestoneSet || assigneeSet) {
      validateReferences(
          task.getWorkspaceId(),
          task.getProjectId(),
          milestoneSet ? milestoneId : null,
          assigneeSet ? assigneeId : null);
    }
    boolean effectiveStatusSet = statusSet && status != null && !status.isBlank();
    boolean effectivePrioritySet = prioritySet && priority != null && !priority.isBlank();
    task.patch(
        title,
        titleSet,
        description,
        descriptionSet,
        effectiveStatusSet ? normalizeStatus(status, false) : status,
        effectiveStatusSet,
        effectivePrioritySet ? normalizePriority(priority, false) : priority,
        effectivePrioritySet,
        milestoneId,
        milestoneSet,
        assigneeId,
        assigneeSet,
        dueDate,
        dueDateSet);
    return toDetail(task);
  }

  @Override
  @Transactional
  public void delete(UUID taskId, UUID userId) {
    Task task = requireTask(taskId);
    requireMember(task.getWorkspaceId(), userId);
    task.delete();
  }

  @Override
  @Transactional
  public TaskUpdateDetail createUpdate(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata) {
    Task task = requireTask(taskId);
    requireMember(task.getWorkspaceId(), userId);
    if (body == null || body.trim().isEmpty()) {
      throw validation("body");
    }
    if (kind != null && !kind.isBlank() && !kind.equals("comment") && !kind.equals("update")) {
      throw validation("kind");
    }
    TaskUpdate update =
        TaskUpdate.create(
            task.getWorkspaceId(),
            task.getProjectId(),
            taskId,
            userId,
            body.trim(),
            kind,
            metadata);
    taskUpdateRepository.save(update);
    return update.toDetail();
  }

  @Override
  @Transactional
  public void deleteUpdate(UUID taskId, UUID updateId, UUID userId) {
    Task task = requireTask(taskId);
    requireMember(task.getWorkspaceId(), userId);
    TaskUpdate update =
        taskUpdateRepository
            .findByIdAndTaskIdAndDeletedAtIsNull(updateId, taskId)
            .orElseThrow(this::taskNotFound);
    if (!userId.equals(update.getAuthorId())) {
      throw new BusinessException(CommonErrorCode.AUTH_FORBIDDEN);
    }
    update.delete();
  }

  private void validateReferences(
      UUID workspaceId, UUID projectId, UUID milestoneId, UUID assigneeId) {
    if (milestoneId != null
        && !taskRepository.existsLiveMilestoneInProject(milestoneId, projectId)) {
      throw validation("milestone_id");
    }
    if (assigneeId != null && !workspaceAccess.isMember(workspaceId, assigneeId)) {
      throw validation("assignee_id");
    }
  }

  private Task requireTask(UUID taskId) {
    return taskRepository.findByIdAndDeletedAtIsNull(taskId).orElseThrow(this::taskNotFound);
  }

  private void requireMember(UUID workspaceId, UUID userId) {
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", workspaceId.toString()));
    }
  }

  private BusinessException projectNotFound() {
    return new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
  }

  private BusinessException taskNotFound() {
    return new BusinessException(ProjectErrorCode.TASK_NOT_FOUND);
  }

  private static BusinessException validation(String field) {
    return new BusinessException(CommonErrorCode.COMMON_VALIDATION_FAILED, Map.of("field", field));
  }

  private static String normalizeStatus(String value, boolean allowDefault) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() && allowDefault) {
      return "backlog";
    }
    return switch (normalized) {
      case "backlog", "todo", "done", "cancelled" -> normalized;
      case "in_progress", "progress", "in-progress" -> "in_progress";
      default -> throw validation("status");
    };
  }

  private static String normalizePriority(String value, boolean allowDefault) {
    String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty() && allowDefault) {
      return "medium";
    }
    return switch (normalized) {
      case "low", "high", "urgent" -> normalized;
      case "medium", "med" -> "medium";
      default -> throw validation("priority");
    };
  }

  private static WebTaskDetail toDetail(Task task) {
    return new WebTaskDetail(
        task.getId(),
        task.getWorkspaceId(),
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
