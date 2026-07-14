package works.momens.server.project.task;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    rejectUnknownChecklistItems(task, command.checklistItems());
    task.replaceChecklist(command.checklistItems());
    return TaskDetailMapper.toDetail(task);
  }

  // 수정 화면이 보낸 완료기준에 존재하지 않는 id가 있으면 거부한다. 잘못된 id를 새 항목으로 만들면 원래 항목이 삭제 후 재생성되면서 완료 상태와 순서가
  // 사라지기 때문이다. 토글의 없는 항목 처리와 같은 계층에서 검증한다.
  private void rejectUnknownChecklistItems(
      Task task, List<UpdateTaskCommand.ChecklistItemEdit> edits) {
    Set<UUID> existingIds =
        task.getChecklistItems().stream().map(TaskChecklistItem::getId).collect(Collectors.toSet());
    for (UpdateTaskCommand.ChecklistItemEdit edit : edits) {
      if (edit.id() != null && !existingIds.contains(edit.id())) {
        throw new BusinessException(
            ProjectErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND,
            Map.of("checklist_item_id", edit.id().toString()));
      }
    }
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
