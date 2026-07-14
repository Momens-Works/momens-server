package works.momens.server.mobile.presentation;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.internal.ChecklistEdit;
import works.momens.server.mobile.internal.MobileTaskDetail;
import works.momens.server.mobile.internal.ProjectTaskService;
import works.momens.server.mobile.presentation.dto.request.ToggleChecklistItemRequest;
import works.momens.server.mobile.presentation.dto.request.UpdateTaskRequest;
import works.momens.server.mobile.presentation.dto.response.ChecklistToggleResponse;
import works.momens.server.mobile.presentation.dto.response.TaskDetailResponse;

/**
 * 모바일 태스크 단건 엔드포인트. task 식별자로 접근하는 {@code /api/mobile/tasks/*} 표면을 담당하고, project 경로의 보드/생성은 {@link
 * ProjectTaskController}가 담당합니다. 수정 계열(MOM-75)이 이 표면에 추가될 예정입니다.
 *
 * <p>{@code /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link
 * CurrentUser#id(Principal)}로 읽습니다(docs/rules/code-conventions.md 보호 API).
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
class TaskController implements TaskControllerDocs {

  private final ProjectTaskService projectTaskService;

  @Override
  @GetMapping(path = "/tasks/{taskId}", version = "1")
  public TaskDetailResponse getTaskDetail(@PathVariable UUID taskId, Principal principal) {
    return TaskDetailResponse.from(
        projectTaskService.getTaskDetail(taskId, CurrentUser.id(principal)));
  }

  @Override
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PatchMapping(path = "/tasks/{taskId}", version = "1")
  public void updateTask(
      @PathVariable UUID taskId,
      @Valid @RequestBody UpdateTaskRequest request,
      Principal principal) {
    List<ChecklistEdit> checklistItems =
        request.checklistItems().stream()
            .map(item -> new ChecklistEdit(item.id(), item.title(), item.completed()))
            .toList();
    projectTaskService.updateTask(
        taskId,
        CurrentUser.id(principal),
        request.title(),
        request.role(),
        request.assigneeId(),
        request.priority(),
        request.status(),
        request.purpose(),
        checklistItems);
  }

  @Override
  @PatchMapping(path = "/tasks/{taskId}/checklist-items/{itemId}", version = "1")
  public ChecklistToggleResponse toggleChecklistItem(
      @PathVariable UUID taskId,
      @PathVariable UUID itemId,
      @Valid @RequestBody ToggleChecklistItemRequest request,
      Principal principal) {
    MobileTaskDetail updated =
        projectTaskService.toggleChecklistItem(
            taskId, CurrentUser.id(principal), itemId, request.completed());
    return ChecklistToggleResponse.from(updated, itemId);
  }
}
