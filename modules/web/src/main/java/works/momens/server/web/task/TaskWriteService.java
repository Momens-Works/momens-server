package works.momens.server.web.task;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import works.momens.server.project.task.WebTaskDetail;
import works.momens.server.project.task.WebTaskWriter;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateWriter;

@Service
@RequiredArgsConstructor
class TaskWriteService {
  private final WebTaskWriter webTaskWriter;
  private final TaskUpdateWriter taskUpdateWriter;

  WebTaskDetail create(
      UUID projectId,
      UUID userId,
      String title,
      String description,
      String status,
      UUID milestoneId,
      String priority,
      UUID assigneeId,
      LocalDate dueDate) {
    return webTaskWriter.create(
        projectId, userId, title, description, status, milestoneId, priority, assigneeId, dueDate);
  }

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
      boolean dueDateSet) {
    return webTaskWriter.update(
        taskId,
        userId,
        title,
        titleSet,
        description,
        descriptionSet,
        status,
        statusSet,
        priority,
        prioritySet,
        milestoneId,
        milestoneSet,
        assigneeId,
        assigneeSet,
        dueDate,
        dueDateSet);
  }

  void delete(UUID taskId, UUID userId) {
    webTaskWriter.delete(taskId, userId);
  }

  TaskUpdateDetail createUpdate(
      UUID taskId, UUID userId, String body, String kind, Map<String, Object> metadata) {
    return taskUpdateWriter.create(taskId, userId, body, kind, metadata);
  }

  void deleteUpdate(UUID taskId, UUID updateId, UUID userId) {
    taskUpdateWriter.delete(taskId, updateId, userId);
  }
}
