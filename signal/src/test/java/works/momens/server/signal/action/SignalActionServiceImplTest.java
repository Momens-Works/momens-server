package works.momens.server.signal.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalActionService;
import works.momens.server.signal.SignalErrorCode;
import works.momens.server.signal.SignalReader;
import works.momens.server.workspace.WorkspaceAccess;

/** SignalActionServiceImpl의 가드·멱등·충돌·동시성 레이스 정책 검증(원자 쓰기는 SignalActionExecutor를 mock으로 분리). */
class SignalActionServiceImplTest {

  private static final UUID SIGNAL_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private final SignalReader signalReader = mock(SignalReader.class);
  private final WorkspaceAccess workspaceAccess = mock(WorkspaceAccess.class);
  private final SignalActionRepository signalActionRepository = mock(SignalActionRepository.class);
  private final SignalActionExecutor executor = mock(SignalActionExecutor.class);
  private final TaskReader taskReader = mock(TaskReader.class);

  private SignalActionServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SignalActionServiceImpl(
            signalReader, workspaceAccess, signalActionRepository, executor, taskReader);
  }

  @Test
  @DisplayName("Signal이 없으면 SIGNAL_NOT_FOUND를 던진다")
  void throwsSignalNotFoundWhenSignalMissing() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.convertToTask(
                    SIGNAL_ID,
                    USER_ID,
                    new SignalActionService.ConvertToTaskCommand(null, "backend", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(SignalErrorCode.SIGNAL_NOT_FOUND);
  }

  @Test
  @DisplayName("workspace 멤버가 아니면 AUTH_FORBIDDEN을 던진다(404 우선)")
  void throwsForbiddenWhenNotMember() {
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목")));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.dismiss(SIGNAL_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  @DisplayName("role이 body에도 draft에도 없으면 COMMON_VALIDATION_FAILED를 던진다")
  void throwsValidationFailedWhenRoleMissing() {
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목")));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.convertToTask(
                    SIGNAL_ID,
                    USER_ID,
                    new SignalActionService.ConvertToTaskCommand(null, null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
    verify(executor, never()).convert(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("title·priority는 폴백하고 role은 body 값을 그대로 executor에 넘긴다")
  void fallsBackTitleAndPriorityButPassesRoleThrough() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "시그널 제목");
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(executor.convert(signal, USER_ID, "시그널 제목", "backend", "medium"))
        .thenReturn(
            new SignalActionResult(
                SIGNAL_ID,
                "convert_to_task",
                true,
                new SignalActionResult.TaskResult(UUID.randomUUID(), "시그널 제목", "todo")));

    SignalActionResult result =
        service.convertToTask(
            SIGNAL_ID,
            USER_ID,
            new SignalActionService.ConvertToTaskCommand(null, "backend", null));

    assertThat(result.created()).isTrue();
    verify(executor).convert(signal, USER_ID, "시그널 제목", "backend", "medium");
  }

  @Test
  @DisplayName("같은 action 재요청은 executor를 호출하지 않고 기존 결과를 replay한다")
  void replaysExistingResultForSameAction() {
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목")));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    UUID taskId = UUID.randomUUID();
    SignalAction existing =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("convert_to_task")
            .resultTaskId(taskId)
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.of(existing));
    when(taskReader.findDetail(taskId))
        .thenReturn(
            Optional.of(
                new TaskDetail(
                    taskId,
                    PROJECT_ID,
                    WORKSPACE_ID,
                    "제목",
                    "todo",
                    "medium",
                    "backend",
                    null,
                    null,
                    java.util.List.of())));

    SignalActionResult result =
        service.convertToTask(
            SIGNAL_ID,
            USER_ID,
            new SignalActionService.ConvertToTaskCommand(null, "backend", null));

    assertThat(result.created()).isFalse();
    assertThat(result.task().id()).isEqualTo(taskId);
    verify(executor, never()).convert(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("replay 대상 task가 존재하지 않으면 진단 메시지를 담은 IllegalStateException을 던진다")
  void throwsIllegalStateWhenReplayTaskMissing() {
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목")));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    UUID missingTaskId = UUID.randomUUID();
    SignalAction existing =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("convert_to_task")
            .resultTaskId(missingTaskId)
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.of(existing));
    when(taskReader.findDetail(missingTaskId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.convertToTask(
                    SIGNAL_ID,
                    USER_ID,
                    new SignalActionService.ConvertToTaskCommand(null, "backend", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SIGNAL_ID.toString())
        .hasMessageContaining(missingTaskId.toString());
  }

  @Test
  @DisplayName("이미 다른 action으로 처리됐으면 SIGNAL_INVALID_STATE를 던진다")
  void throwsInvalidStateWhenProcessedByDifferentAction() {
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목")));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    SignalAction existing =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("dismiss")
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.convertToTask(
                    SIGNAL_ID,
                    USER_ID,
                    new SignalActionService.ConvertToTaskCommand(null, "backend", null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(SignalErrorCode.SIGNAL_INVALID_STATE);
  }

  @Test
  @DisplayName("동시성 레이스로 executor가 제약 위반을 던지면 재조회해 replay한다")
  void recoversFromRaceByReplayingAfterConstraintViolation() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목");
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    SignalAction racedRow =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("dismiss")
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(racedRow));
    when(executor.dismiss(signal, USER_ID))
        .thenThrow(new DataIntegrityViolationException("unique violation"));

    SignalActionResult result = service.dismiss(SIGNAL_ID, USER_ID);

    assertThat(result.created()).isFalse();
    assertThat(result.actionType()).isEqualTo("dismiss");
    verify(signalActionRepository, times(2)).findBySignalId(SIGNAL_ID);
  }
}
