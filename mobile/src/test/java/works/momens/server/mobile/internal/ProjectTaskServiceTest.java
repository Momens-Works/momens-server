package works.momens.server.mobile.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import works.momens.server.project.TaskReader;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 태스크 보드 조회와 생성 조합 규칙 검증. 도메인 모듈 public API는 mock으로 두고, 조합 규칙(권한 검사 순서, 그룹 구성, priority 매핑,
 * material_count 기본값, 생성 command 전달)만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectTaskServiceTest {

  @Mock private ProjectReader projectReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @Mock private TaskReader taskReader;
  @Mock private TaskCreator taskCreator;
  @InjectMocks private ProjectTaskService projectTaskService;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();

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
  void getBoardBucketsTasksIntoThreeGroupsInOrderAndKeepsEmptyGroups() {
    stubMember();
    UUID todoId = UUID.randomUUID();
    UUID inProgressId = UUID.randomUUID();
    when(taskReader.listBoardTasks(PROJECT_ID))
        .thenReturn(
            List.of(
                new BoardTask(todoId, "투두 태스크", "todo", "low", List.of("android")),
                new BoardTask(inProgressId, "진행중 태스크", "in_progress", "medium", List.of())));

    List<MobileTaskGroup> groups = projectTaskService.getBoard(PROJECT_ID, CALLER_ID);

    assertThat(groups)
        .extracting(MobileTaskGroup::groupKey)
        .containsExactly("todo", "in_progress", "done");
    assertThat(groups.get(0).tasks()).extracting(MobileTaskCard::id).containsExactly(todoId);
    assertThat(groups.get(1).tasks()).extracting(MobileTaskCard::id).containsExactly(inProgressId);
    assertThat(groups.get(2).tasks()).isEmpty();
  }

  @Test
  void getBoardMapsUrgentToHighAndZeroMaterialCount() {
    stubMember();
    UUID taskId = UUID.randomUUID();
    when(taskReader.listBoardTasks(PROJECT_ID))
        .thenReturn(List.of(new BoardTask(taskId, "긴급 태스크", "todo", "urgent", List.of("pm"))));

    MobileTaskCard card = projectTaskService.getBoard(PROJECT_ID, CALLER_ID).get(0).tasks().get(0);

    assertThat(card.priority()).isEqualTo("high");
    assertThat(card.materialCount()).isZero();
    assertThat(card.roles()).containsExactly("pm");
  }

  @Test
  void createTaskPassesCommandThroughToCreator() {
    stubMember();
    CreatedTask created =
        new CreatedTask(UUID.randomUUID(), PROJECT_ID, "제목", List.of("pm"), "high", "todo");
    when(taskCreator.create(any())).thenReturn(created);

    CreatedTask result =
        projectTaskService.createTask(PROJECT_ID, CALLER_ID, "제목", List.of("pm"), "high");

    ArgumentCaptor<CreateTaskCommand> captor = ArgumentCaptor.forClass(CreateTaskCommand.class);
    org.mockito.Mockito.verify(taskCreator).create(captor.capture());
    CreateTaskCommand command = captor.getValue();
    assertThat(command.projectId()).isEqualTo(PROJECT_ID);
    assertThat(command.workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(command.title()).isEqualTo("제목");
    assertThat(command.roles()).containsExactly("pm");
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

  private void stubMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
  }
}
