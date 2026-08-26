package works.momens.server.project.task.internal;

import works.momens.server.project.task.TaskSnapshot;

final class TaskSnapshotMapper {

  private TaskSnapshotMapper() {}

  static TaskSnapshot toSnapshot(Task task) {
    return toSnapshot(task, task.getWorkspaceId());
  }

  static TaskSnapshot toSnapshot(Task task, java.util.UUID workspaceId) {
    return new TaskSnapshot(
        task.getId(),
        workspaceId,
        task.getProjectId(),
        task.getMilestoneId(),
        task.getLabel(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getRole(),
        task.getAssigneeId(),
        task.getDueDate(),
        task.getCreatedAt(),
        task.getUpdatedAt());
  }
}
