package works.momens.server.signal.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;
import works.momens.server.signal.SignalActionResult;
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
  private final SignalTaskDraftGenerator taskDraftGenerator = mock(SignalTaskDraftGenerator.class);

  private SignalActionServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SignalActionServiceImpl(
            signalReader,
            workspaceAccess,
            signalActionRepository,
            executor,
            taskReader,
            taskDraftGenerator);
  }

  private static SignalReader.Snapshot snapshot() {
    return new SignalReader.Snapshot(
        SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "decision", "제목", "설명", "전체 영향");
  }

  @Test
  @DisplayName("Signal이 없으면 SIGNAL_NOT_FOUND를 던진다")
  void throwsSignalNotFoundWhenSignalMissing() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.convertToTask(SIGNAL_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(SignalErrorCode.SIGNAL_NOT_FOUND);
  }

  @Test
  @DisplayName("workspace 멤버가 아니면 AUTH_FORBIDDEN을 던진다(404 우선)")
  void throwsForbiddenWhenNotMember() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.dismiss(SIGNAL_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  @DisplayName("신규 convert는 generator를 한 번 호출하고 생성된 draft로 executor를 호출한다")
  void convertUsesGeneratedDraft() {
    SignalReader.Snapshot signal = snapshot();
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(signalReader.findDraftEvidence(SIGNAL_ID)).thenReturn(List.of());
    when(taskDraftGenerator.generate(any()))
        .thenReturn(new TaskDraft("결제 정책 확정하기", Role.BACKEND, Priority.HIGH));
    when(executor.convert(signal, USER_ID, "결제 정책 확정하기", "backend", "high"))
        .thenReturn(
            new SignalActionResult(
                SIGNAL_ID,
                "convert_to_task",
                true,
                new SignalActionResult.TaskResult(UUID.randomUUID(), "결제 정책 확정하기", "todo")));

    SignalActionResult result = service.convertToTask(SIGNAL_ID, USER_ID);

    assertThat(result.created()).isTrue();
    verify(taskDraftGenerator, times(1)).generate(any());
    verify(executor).convert(signal, USER_ID, "결제 정책 확정하기", "backend", "high");
  }

  @Test
  @DisplayName("고정 fallback draft도 같은 executor 경로로 전달한다")
  void convertPassesFallbackDraftToExecutor() {
    SignalReader.Snapshot signal = snapshot();
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(signalReader.findDraftEvidence(SIGNAL_ID)).thenReturn(List.of());
    when(taskDraftGenerator.generate(any()))
        .thenReturn(new TaskDraft("제목", Role.PM, Priority.MEDIUM));
    when(executor.convert(signal, USER_ID, "제목", "pm", "medium"))
        .thenReturn(
            new SignalActionResult(
                SIGNAL_ID,
                "convert_to_task",
                true,
                new SignalActionResult.TaskResult(UUID.randomUUID(), "제목", "todo")));

    SignalActionResult result = service.convertToTask(SIGNAL_ID, USER_ID);

    assertThat(result.created()).isTrue();
    verify(executor).convert(signal, USER_ID, "제목", "pm", "medium");
  }

  @Test
  @DisplayName("generator 입력은 Signal title/type/description/impact와 조회 순서를 유지한 evidence만 포함한다")
  void convertPassesSignalAndEvidenceToGenerator() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(signalReader.findDraftEvidence(SIGNAL_ID))
        .thenReturn(
            List.of(
                new SignalReader.DraftEvidence("결제 정책", "논의 중단", "출시 지연"),
                new SignalReader.DraftEvidence("환불 정책", "합의 없음", "CS 부담")));
    when(taskDraftGenerator.generate(any()))
        .thenReturn(new TaskDraft("제목", Role.PM, Priority.MEDIUM));
    when(executor.convert(any(), any(), any(), any(), any()))
        .thenReturn(new SignalActionResult(SIGNAL_ID, "convert_to_task", true, null));

    service.convertToTask(SIGNAL_ID, USER_ID);

    ArgumentCaptor<SignalTaskDraftInput> captor =
        ArgumentCaptor.forClass(SignalTaskDraftInput.class);
    verify(taskDraftGenerator).generate(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(
            new SignalTaskDraftInput(
                "제목",
                "decision",
                "설명",
                "전체 영향",
                List.of(
                    new SignalTaskDraftInput.Evidence("결제 정책", "논의 중단", "출시 지연"),
                    new SignalTaskDraftInput.Evidence("환불 정책", "합의 없음", "CS 부담"))));
  }

  @Test
  @DisplayName("근거가 없으면 빈 evidence 목록으로 generator를 호출한다")
  void convertPassesEmptyEvidenceWhenSignalHasNone() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(signalReader.findDraftEvidence(SIGNAL_ID)).thenReturn(List.of());
    when(taskDraftGenerator.generate(any()))
        .thenReturn(new TaskDraft("제목", Role.PM, Priority.MEDIUM));
    when(executor.convert(any(), any(), any(), any(), any()))
        .thenReturn(new SignalActionResult(SIGNAL_ID, "convert_to_task", true, null));

    service.convertToTask(SIGNAL_ID, USER_ID);

    ArgumentCaptor<SignalTaskDraftInput> captor =
        ArgumentCaptor.forClass(SignalTaskDraftInput.class);
    verify(taskDraftGenerator).generate(captor.capture());
    assertThat(captor.getValue().evidence()).isEmpty();
  }

  @Test
  @DisplayName("dismiss는 evidence를 조회하지 않고 generator도 호출하지 않는다")
  void dismissDoesNotTouchDraftGeneration() {
    SignalReader.Snapshot signal = snapshot();
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.empty());
    when(executor.dismiss(signal, USER_ID))
        .thenReturn(new SignalActionResult(SIGNAL_ID, "dismiss", true, null));

    service.dismiss(SIGNAL_ID, USER_ID);

    verify(taskDraftGenerator, never()).generate(any());
    verify(signalReader, never()).findDraftEvidence(any());
  }

  @Test
  @DisplayName("같은 action 재요청은 executor를 호출하지 않고 기존 결과를 replay한다")
  void replaysExistingResultForSameAction() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
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
                    "pm",
                    null,
                    null,
                    java.util.List.of(),
                    java.util.List.of(),
                    null)));

    SignalActionResult result = service.convertToTask(SIGNAL_ID, USER_ID);

    assertThat(result.created()).isFalse();
    assertThat(result.task().id()).isEqualTo(taskId);
    verify(executor, never()).convert(any(), any(), any(), any(), any());
    verify(taskDraftGenerator, never()).generate(any());
    verify(signalReader, never()).findDraftEvidence(any());
  }

  @Test
  @DisplayName("replay 대상 task가 존재하지 않으면 진단 메시지를 담은 IllegalStateException을 던진다")
  void throwsIllegalStateWhenReplayTaskMissing() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
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

    assertThatThrownBy(() -> service.convertToTask(SIGNAL_ID, USER_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SIGNAL_ID.toString())
        .hasMessageContaining(missingTaskId.toString());
  }

  @Test
  @DisplayName("이미 다른 action으로 처리됐으면 SIGNAL_INVALID_STATE를 던진다")
  void throwsInvalidStateWhenProcessedByDifferentAction() {
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(snapshot()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    SignalAction existing =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("dismiss")
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID)).thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.convertToTask(SIGNAL_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(SignalErrorCode.SIGNAL_INVALID_STATE);
    verify(taskDraftGenerator, never()).generate(any());
    verify(signalReader, never()).findDraftEvidence(any());
  }

  @Test
  @DisplayName("convertToTask 동시성 레이스로 제약 위반을 만나면 재조회해 기존 task로 replay한다")
  void recoversFromRaceByReplayingAfterConvertConstraintViolation() {
    SignalReader.Snapshot signal = snapshot();
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.of(signal));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    UUID taskId = UUID.randomUUID();
    SignalAction racedRow =
        SignalAction.builder()
            .workspaceId(WORKSPACE_ID)
            .signalId(SIGNAL_ID)
            .actionType("convert_to_task")
            .resultTaskId(taskId)
            .processedByUserId(USER_ID)
            .build();
    when(signalActionRepository.findBySignalId(SIGNAL_ID))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(racedRow));
    when(signalReader.findDraftEvidence(SIGNAL_ID)).thenReturn(List.of());
    when(taskDraftGenerator.generate(any()))
        .thenReturn(new TaskDraft("제목", Role.PM, Priority.MEDIUM));
    when(executor.convert(signal, USER_ID, "제목", "pm", "medium"))
        .thenThrow(new DataIntegrityViolationException("unique violation"));
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
                    "pm",
                    null,
                    null,
                    java.util.List.of(),
                    java.util.List.of(),
                    null)));

    SignalActionResult result = service.convertToTask(SIGNAL_ID, USER_ID);

    assertThat(result.created()).isFalse();
    assertThat(result.actionType()).isEqualTo("convert_to_task");
    assertThat(result.task().id()).isEqualTo(taskId);
    verify(signalActionRepository, times(2)).findBySignalId(SIGNAL_ID);
  }

  @Test
  @DisplayName("동시성 레이스로 executor가 제약 위반을 던지면 재조회해 replay한다")
  void recoversFromRaceByReplayingAfterConstraintViolation() {
    SignalReader.Snapshot signal = snapshot();
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
