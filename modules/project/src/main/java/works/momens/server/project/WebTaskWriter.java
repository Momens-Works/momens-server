package works.momens.server.project;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** 웹 Product API의 레거시 task write public API입니다. */
public interface WebTaskWriter {

  WebTaskDetail create(
      UUID projectId,
      UUID userId,
      String title,
      String description,
      String status,
      UUID milestoneId,
      String priority,
      UUID assigneeId,
      LocalDate dueDate);

  WebTaskDetail update(
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
      boolean dueDateSet);

  void delete(UUID taskId, UUID userId);

  TaskUpdateDetail createUpdate(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata);

  void deleteUpdate(UUID taskId, UUID updateId, UUID userId);
}
