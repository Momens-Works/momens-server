package works.momens.server.project.task;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskEditor;
import works.momens.server.project.UpdateTaskCommand;

@Service
@RequiredArgsConstructor
class TaskEditorImpl implements TaskEditor {

  private final TaskRepository taskRepository;

  @Override
  @Transactional
  public TaskDetail update(UpdateTaskCommand command) {
    Task task = findTask(command.taskId());
    task.update(
        command.title(),
        command.role(),
        command.priority(),
        command.status(),
        command.purpose(),
        command.assigneeId());
    task.replaceChecklist(command.checklistItems());
    return TaskDetailMapper.toDetail(task);
  }

  @Override
  @Transactional
  public TaskDetail toggleChecklistItem(UUID taskId, UUID itemId, boolean completed) {
    Task task = findTask(taskId);
    TaskChecklistItem item =
        task.getChecklistItems().stream()
            .filter(candidate -> candidate.getId().equals(itemId))
            .findFirst()
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND,
                        Map.of("checklist_item_id", itemId.toString())));
    item.changeCompleted(completed);
    return TaskDetailMapper.toDetail(task);
  }

  private Task findTask(UUID taskId) {
    return taskRepository
        .findByIdAndDeletedAtIsNull(taskId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ProjectErrorCode.TASK_NOT_FOUND, Map.of("task_id", taskId.toString())));
  }
}
