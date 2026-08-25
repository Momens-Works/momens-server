package works.momens.server.project.task;

import java.time.LocalDate;
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
}
