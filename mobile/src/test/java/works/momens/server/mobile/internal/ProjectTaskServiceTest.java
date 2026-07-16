package works.momens.server.mobile.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import works.momens.server.context.EntityRelationReader;
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
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 태스크 보드 조회와 생성, 상세 조회 조합 규칙 검증. 도메인 모듈 public API는 mock으로 두고, 조합 규칙(권한 검사 순서, 그룹 구성, priority 매핑,
 * material_count 채우기, 생성 command 전달, 상세의 assignee 결합과 purpose 개명, 관련자료 조립)만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceTest {

  @Mock private ProjectReader projectReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @Mock private TaskReader taskReader;
  @Mock private TaskCreator taskCreator;
  @Mock private TaskEditor taskEditor;
  @Mock private UserService userService;
  @Mock private EntityRelationReader entityRelationReader;
  @Mock private SourceRefReader sourceRefReader;
  @InjectMocks private ProjectTaskService projectTaskService;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final Instant CREATED_AT = Instant.parse("2026-07-01T00:00:00Z");
  private static final Instant OCCURRED_AT = Instant.parse("2026-06-28T00:48:00Z");
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
                new BoardTask(backlogId, "백로그 태스크", "backlog", "medium", "pm", CREATED_AT),
                new BoardTask(todoId, "투두 태스크", "todo", "low", "frontend", CREATED_AT),
                new BoardTask(inProgressId, "진행중 태스크", "in_progress", "medium", "pm", CREATED_AT),
                new BoardTask(cancelledId, "취소 태스크", "cancelled", "high", "pm", CREATED_AT)));

    List<MobileTaskGroup> groups = projectTaskService.getBoard(PROJECT_ID, CALLER_ID);

    assertThat(groups)
        .extracting(MobileTaskGroup::status)
        .containsExactly(
            BoardStatus.TODO,
            BoardStatus.IN_PROGRESS,
            BoardStatus.DONE,
            BoardStatus.BACKLOG,
            BoardStatus.CANCELLED);
    assertThat(groups.get(0).tasks()).extracting(MobileTaskCard::id).containsExactly(todoId);
    assertThat(groups.get(1).tasks()).extracting(MobileTaskCard::id).containsExactly(inProgressId);
    assertThat(groups.get(2).tasks()).isEmpty();
    assertThat(groups.get(3).tasks()).extracting(MobileTaskCard::id).containsExactly(backlogId);
    assertThat(groups.get(4).tasks()).extracting(MobileTaskCard::id).containsExactly(cancelledId);
  }

  @Test
  void getBoardMapsUrgentToHigh() {
    stubMember();
    UUID taskId = UUID.randomUUID();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any()))
        .thenReturn(List.of(new BoardTask(taskId, "긴급 태스크", "todo", "urgent", "pm", CREATED_AT)));

    // 보드 그룹 순서는 todo, in_progress, ... 이므로 todo 그룹은 인덱스 0이다.
    MobileTaskCard card = projectTaskService.getBoard(PROJECT_ID, CALLER_ID).get(0).tasks().get(0);

    assertThat(card.priority()).isEqualTo("high");
    assertThat(card.role()).isEqualTo("pm");
  }

  @Test
  void getBoardFillsMaterialCountFromLinksInOneBatch() {
    stubMember();
    UUID linked = UUID.randomUUID();
    UUID unlinked = UUID.randomUUID();
    UUID refA = UUID.randomUUID();
    UUID refB = UUID.randomUUID();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any()))
        .thenReturn(
            List.of(
                new BoardTask(linked, "자료 있는 태스크", "todo", "medium", "pm", CREATED_AT),
                new BoardTask(unlinked, "자료 없는 태스크", "todo", "medium", "pm", CREATED_AT)));
    when(entityRelationReader.findLinkedSourceRefIds(WORKSPACE_ID, List.of(linked, unlinked)))
        .thenReturn(Map.of(linked, List.of(refA, refB)));
    when(sourceRefReader.findByIds(WORKSPACE_ID, List.of(refA, refB)))
        .thenReturn(List.of(sourceRef(refA), sourceRef(refB)));

    List<MobileTaskCard> cards = projectTaskService.getBoard(PROJECT_ID, CALLER_ID).get(0).tasks();

    assertThat(cards)
        .extracting(MobileTaskCard::id, MobileTaskCard::materialCount)
        .containsExactly(tuple(linked, 2), tuple(unlinked, 0));
  }

  @Test
  void getBoardCountsOnlyMaterialsThatDetailAlsoShows() {
    stubMember();
    UUID taskId = UUID.randomUUID();
    UUID live = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    when(taskReader.listTasksByStatus(eq(PROJECT_ID), any()))
        .thenReturn(List.of(new BoardTask(taskId, "태스크", "todo", "medium", "pm", CREATED_AT)));
    when(entityRelationReader.findLinkedSourceRefIds(WORKSPACE_ID, List.of(taskId)))
        .thenReturn(Map.of(taskId, List.of(live, gone)));
    // 연결은 둘 다 살아 있지만 원본 하나가 삭제된 상태다. 상세가 그 자료를 보여주지 못하므로 개수도 그것을 빼야 한다.
    when(sourceRefReader.findByIds(WORKSPACE_ID, List.of(live, gone)))
        .thenReturn(List.of(sourceRef(live)));

    MobileTaskCard card = projectTaskService.getBoard(PROJECT_ID, CALLER_ID).get(0).tasks().get(0);

    assertThat(card.materialCount()).isEqualTo(1);
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

    assertThat(result.assignee())
        .isEqualTo(
            new MobileTaskDetail.Assignee(
                assigneeId, "김규일", "https://lh3.googleusercontent.com/a/gyuil"));
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
    assertThat(result.materials()).isEmpty();
    assertThat(result.openQuestions()).isEmpty();
    assertThat(result.nextAction()).isNull();
  }

  @Test
  void getTaskDetailPassesMinsuFieldsThroughUnchanged() {
    UUID questionId = UUID.randomUUID();
    when(taskReader.findDetail(TASK_ID))
        .thenReturn(
            Optional.of(
                detail(
                    null,
                    null,
                    List.of(),
                    List.of(new TaskDetail.OpenQuestion(questionId, "권한 거부 시 대체 흐름을 둘지 검토 필요")),
                    "권한 거부 흐름을 PM과 확정하세요.")));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);

    MobileTaskDetail result = projectTaskService.getTaskDetail(TASK_ID, CALLER_ID);

    // 민수 산출물은 모바일 표기 매핑 없이 그대로 내려간다(priority의 urgent→high 매핑과 대비).
    assertThat(result.openQuestions())
        .containsExactly(new TaskDetail.OpenQuestion(questionId, "권한 거부 시 대체 흐름을 둘지 검토 필요"));
    assertThat(result.nextAction()).isEqualTo("권한 거부 흐름을 PM과 확정하세요.");
  }

  @Test
  void getTaskDetailAssemblesMaterialsInLinkOrderAndSkipsMissingSourceRef() {
    UUID figma = UUID.randomUUID();
    UUID slack = UUID.randomUUID();
    UUID gone = UUID.randomUUID();
    when(taskReader.findDetail(TASK_ID)).thenReturn(Optional.of(detail(null, null, List.of())));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(entityRelationReader.findLinkedSourceRefIds(WORKSPACE_ID, List.of(TASK_ID)))
        .thenReturn(Map.of(TASK_ID, List.of(figma, slack, gone)));
    // 원본 조회 순서는 보장되지 않으므로, 표시 순서는 링크 순서를 따라야 한다. 원본이 없는 링크(gone)는 빠진다.
    when(sourceRefReader.findByIds(WORKSPACE_ID, List.of(figma, slack, gone)))
        .thenReturn(
            List.of(
                new SourceRefView(
                    slack, "slack", "스레드", null, "본문 전체", "https://slack.example/t", OCCURRED_AT),
                new SourceRefView(
                    figma,
                    "figma",
                    "권한 요청 화면 v2",
                    "설명 문구 변경",
                    "본문",
                    "https://figma.example/p",
                    OCCURRED_AT)));

    List<MobileTaskDetail.Material> materials =
        projectTaskService.getTaskDetail(TASK_ID, CALLER_ID).materials();

    assertThat(materials).extracting(MobileTaskDetail.Material::id).containsExactly(figma, slack);
    assertThat(materials.getFirst())
        .isEqualTo(
            new MobileTaskDetail.Material(
                figma, "권한 요청 화면 v2", "설명 문구 변경", "figma", OCCURRED_AT, "https://figma.example/p"));
    // snippet이 없으면 본문(text)을 요약으로 쓴다.
    assertThat(materials.get(1).summary()).isEqualTo("본문 전체");
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
    List<ChecklistEdit> items = List.of(new ChecklistEdit(null, "A", true));

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
        .containsExactly(new UpdateTaskCommand.ChecklistItemEdit(null, "A", true));
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
    return detail(assigneeId, description, checklistItems, List.of(), null);
  }

  private static TaskDetail detail(
      UUID assigneeId,
      String description,
      List<TaskDetail.ChecklistItem> checklistItems,
      List<TaskDetail.OpenQuestion> openQuestions,
      String nextAction) {
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
        checklistItems,
        openQuestions,
        nextAction);
  }

  private static UserProfile profile(UUID id, String name) {
    return new UserProfile(
        id,
        "gyuil@momens.works",
        name,
        "backend",
        "https://lh3.googleusercontent.com/a/gyuil",
        null,
        null);
  }

  private static SourceRefView sourceRef(UUID id) {
    return new SourceRefView(id, "figma", "제목", "요약", null, "https://x", OCCURRED_AT);
  }

  private void stubMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
  }
}
