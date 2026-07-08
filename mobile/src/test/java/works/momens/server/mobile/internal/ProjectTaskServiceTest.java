package works.momens.server.mobile.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.BoardTask;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.TaskCreator;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskEditor;
import works.momens.server.project.TaskReader;
import works.momens.server.project.UpdateTaskCommand;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 태스크 보드 조회와 생성, 상세 조회 조합 규칙 검증. 도메인 모듈 public API는 mock으로 두고, 조합 규칙(권한 검사 순서, 그룹 구성, priority 매핑,
 * material_count 기본값, 생성 command 전달, 상세의 assignee 결합과 purpose 개명)만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceTest {

  @Mock private ProjectReader projectReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @Mock private TaskReader taskReader;
  @Mock private TaskCreator taskCreator;
  @Mock private TaskEditor taskEditor;
  @Mock private UserService userService;
  @InjectMocks private ProjectTaskService projectTaskService;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();

  @Test
  void getBoardThrowsProjectNotFoundWhenProjectMissing() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectTaskService.getBoard(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
  }

  @Test
  void getBoardThrowsForbiddenWhenCallerIsNotMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> projectTaskService.getBoard(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void getBoardBucketsTasksIntoFiveGroupsInOrderAndKeepsEmptyGroups() {
    stubMember();
    UUID backlogId = UUID.randomUUID();
    UUID todoId = UUID.randomUUID();
    UUID inProgressId = UUID.randomUUID();
    UUID cancelledId = UUID.randomUUID();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any()))
        .thenReturn(
            List.of(
                new BoardTask(backlogId, "백로그 태스크", "backlog", "medium", "pm"),
                new BoardTask(todoId, "투두 태스크", "todo", "low", "frontend"),
                new BoardTask(inProgressId, "진행중 태스크", "in_progress", "medium", "pm"),
                new BoardTask(cancelledId, "취소 태스크", "cancelled", "high", "pm")));

    List<MobileTaskGroup> groups = projectTaskService.getBoard(PROJECT_ID, CALLER_ID);

    assertThat(groups)
        .extracting(MobileTaskGroup::status)
        .containsExactly(
            BoardStatus.BACKLOG,
            BoardStatus.TODO,
            BoardStatus.IN_PROGRESS,
            BoardStatus.DONE,
            BoardStatus.CANCELLED);
    assertThat(groups.get(0).tasks()).extracting(MobileTaskCard::id).containsExactly(backlogId);
    assertThat(groups.get(1).tasks()).extracting(MobileTaskCard::id).containsExactly(todoId);
    assertThat(groups.get(2).tasks()).extracting(MobileTaskCard::id).containsExactly(inProgressId);
    assertThat(groups.get(3).tasks()).isEmpty();
    assertThat(groups.get(4).tasks()).extracting(MobileTaskCard::id).containsExactly(cancelledId);
  }

  @Test
  void getBoardMapsUrgentToHighAndZeroMaterialCount() {
    stubMember();
    UUID taskId = UUID.randomUUID();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any()))
        .thenReturn(List.of(new BoardTask(taskId, "긴급 태스크", "todo", "urgent", "pm")));

    // 보드 그룹 순서는 backlog, todo, ... 이므로 todo 그룹은 인덱스 1이다.
    MobileTaskCard card = projectTaskService.getBoard(PROJECT_ID, CALLER_ID).get(1).tasks().get(0);

    assertThat(card.priority()).isEqualTo("high");
    assertThat(card.materialCount()).isZero();
    assertThat(card.role()).isEqualTo("pm");
  }

  @Test
  void createTaskPassesCommandThroughToCreator() {
    stubMember();
    CreatedTask created =
        new CreatedTask(UUID.randomUUID(), PROJECT_ID, "제목", "pm", "high", "todo");
    when(taskCreator.create(any())).thenReturn(created);

    CreatedTask result = projectTaskService.createTask(PROJECT_ID, CALLER_ID, "제목", "pm", "high");

    ArgumentCaptor<CreateTaskCommand> captor = ArgumentCaptor.forClass(CreateTaskCommand.class);
    org.mockito.Mockito.verify(taskCreator).create(captor.capture());
    CreateTaskCommand command = captor.getValue();
    assertThat(command.projectId()).isEqualTo(PROJECT_ID);
    assertThat(command.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(command.title()).isEqualTo("제목");
    assertThat(command.role()).isEqualTo("pm");
    assertThat(command.priority()).isEqualTo("high");
    assertThat(result).isSameAs(created);
  }

  @Test
  void createTaskThrowsForbiddenWhenCallerIsNotMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> projectTaskService.createTask(PROJECT_ID, CALLER_ID, "제목", null, null))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void getTaskDetailThrowsTaskNotFoundWhenTaskMissing() {
    when(taskReader.findDetail(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectTaskService.getTaskDetail(TASK_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_NOT_FOUND);
  }

  @Test
  void getTaskDetailThrowsForbiddenForNonMemberOfTaskWorkspace() {
    when(taskReader.findDetail(TASK_ID)).thenReturn(Optional.of(detail(null, null, List.of())));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> projectTaskService.getTaskDetail(TASK_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void getTaskDetailJoinsAssigneeProfileAndMapsUrgentToHigh() {
    UUID assigneeId = UUID.randomUUID();
    when(taskReader.findDetail(TASK_ID))
        .thenReturn(
            Optional.of(
                detail(
                    assigneeId,
                    "화면 흐름 정리",
                    List.of(new TaskDetail.ChecklistItem(UUID.randomUUID(), "완료기준", true)))));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(userService.getProfile(assigneeId)).thenReturn(profile(assigneeId, "김규일"));

    MobileTaskDetail result = projectTaskService.getTaskDetail(TASK_ID, CALLER_ID);

    assertThat(result.assignee()).isEqualTo(new MobileTaskDetail.Assignee(assigneeId, "김규일"));
    assertThat(result.priority()).isEqualTo("high");
    assertThat(result.purpose()).isEqualTo("화면 흐름 정리");
    assertThat(result.checklistItems())
        .extracting(TaskDetail.ChecklistItem::title)
        .containsExactly("완료기준");
  }

  @Test
  void getTaskDetailReturnsNullAssigneeAndPurposeWhenUnset() {
    when(taskReader.findDetail(TASK_ID)).thenReturn(Optional.of(detail(null, null, List.of())));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);

    MobileTaskDetail result = projectTaskService.getTaskDetail(TASK_ID, CALLER_ID);

    assertThat(result.assignee()).isNull();
    assertThat(result.purpose()).isNull();
    assertThat(result.checklistItems()).isEmpty();
  }

  @Test
  void updateTaskThrowsTaskNotFoundWhenTaskMissing() {
    when(taskReader.workspaceIdOf(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                projectTaskService.updateTask(
                    TASK_ID, CALLER_ID, "제목", "pm", null, "medium", "todo", null, List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_NOT_FOUND);
  }

  @Test
  void updateTaskThrowsForbiddenWhenCallerIsNotMember() {
    when(taskReader.workspaceIdOf(TASK_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(
            () ->
                projectTaskService.updateTask(
                    TASK_ID, CALLER_ID, "제목", "pm", null, "medium", "todo", null, List.of()))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void updateTaskSendsFullEditableStateToEditor() {
    when(taskReader.workspaceIdOf(TASK_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(taskEditor.update(any())).thenReturn(detail(null, "수정한 목적", List.of()));
    List<ChecklistEdit> items = List.of(new ChecklistEdit(null, "A"));

    MobileTaskDetail result =
        projectTaskService.updateTask(
            TASK_ID, CALLER_ID, "제목", "backend", null, "high", "in_progress", "수정한 목적", items);

    ArgumentCaptor<UpdateTaskCommand> captor = ArgumentCaptor.forClass(UpdateTaskCommand.class);
    org.mockito.Mockito.verify(taskEditor).update(captor.capture());
    UpdateTaskCommand command = captor.getValue();
    assertThat(command.taskId()).isEqualTo(TASK_ID);
    assertThat(command.title()).isEqualTo("제목");
    assertThat(command.role()).isEqualTo("backend");
    assertThat(command.assigneeId()).isNull();
    assertThat(command.priority()).isEqualTo("high");
    assertThat(command.status()).isEqualTo("in_progress");
    assertThat(command.purpose()).isEqualTo("수정한 목적");
    assertThat(command.checklistItems())
        .containsExactly(new UpdateTaskCommand.ChecklistItemEdit(null, "A"));
    assertThat(result.assignee()).isNull();
  }

  @Test
  void toggleChecklistItemThrowsTaskNotFoundWhenTaskMissing() {
    when(taskReader.workspaceIdOf(TASK_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                projectTaskService.toggleChecklistItem(TASK_ID, CALLER_ID, UUID.randomUUID(), true))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_NOT_FOUND);
  }

  @Test
  void toggleChecklistItemPassesThroughToEditor() {
    UUID itemId = UUID.randomUUID();
    when(taskReader.workspaceIdOf(TASK_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(taskEditor.toggleChecklistItem(TASK_ID, itemId, true))
        .thenReturn(
            detail(null, null, List.of(new TaskDetail.ChecklistItem(itemId, "완료기준", true))));

    MobileTaskDetail result =
        projectTaskService.toggleChecklistItem(TASK_ID, CALLER_ID, itemId, true);

    assertThat(result.checklistItems())
        .extracting(TaskDetail.ChecklistItem::completed)
        .containsExactly(true);
  }

  private static TaskDetail detail(
      UUID assigneeId, String description, List<TaskDetail.ChecklistItem> checklistItems) {
    return new TaskDetail(
        TASK_ID,
        PROJECT_ID,
        WORKSPACE_ID,
        "1차 와이어프레임",
        "todo",
        "urgent",
        "pm",
        assigneeId,
        description,
        checklistItems);
  }

  private static UserProfile profile(UUID id, String name) {
    return new UserProfile(id, "gyuil@momens.works", name, "backend", null, null, null);
  }

  private void stubMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
  }
}
