package works.momens.server.project.task;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
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
    validateChecklistItemIds(task, command.checklistItems());
    task.replaceChecklist(command.checklistItems());
    return TaskDetailMapper.toDetail(task);
  }

  // 수정 화면이 보낸 완료기준 id를 검증한다. 존재하지 않는 id는 새 항목으로 만들면 원래 항목이 삭제 후 재생성되면서 완료 상태와 순서가 사라지므로 거부한다. 같은
  // id가 목록에 두 번 오면 같은 엔티티가 컬렉션에 중복으로 들어가 position 저장이 꼬이므로 잘못된 요청으로 거부한다. 토글의 없는 항목 처리와 같은 계층에서
  // 검증한다.
  private void validateChecklistItemIds(
      Task task, List<UpdateTaskCommand.ChecklistItemEdit> edits) {
    Set<UUID> existingIds =
        task.getChecklistItems().stream().map(TaskChecklistItem::getId).collect(Collectors.toSet());
    Set<UUID> seenIds = new HashSet<>();
    for (UpdateTaskCommand.ChecklistItemEdit edit : edits) {
      if (edit.id() == null) {
        continue;
      }
      if (!existingIds.contains(edit.id())) {
        throw new BusinessException(
            ProjectErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND,
            Map.of("checklist_item_id", edit.id().toString()));
      }
      if (!seenIds.add(edit.id())) {
        throw new BusinessException(
            CommonErrorCode.COMMON_VALIDATION_FAILED,
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
