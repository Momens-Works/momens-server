package works.momens.server.signal.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.PreparedTaskDraft;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.TaskDraftAsyncIntent;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.project.TaskCreator;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalReader;

/** convert/dismiss가 CO-6 이벤트 계약대로 outbox를 발행하는지 검증한다(DB 없이 협력 대상 mock). */
class SignalActionExecutorTest {

  private static final UUID SIGNAL_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  private final SignalActionRepository signalActionRepository = mock(SignalActionRepository.class);
  private final TaskCreator taskCreator = mock(TaskCreator.class);
  private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
  private final SignalTaskDraftGenerator taskDraftGenerator = mock(SignalTaskDraftGenerator.class);

  private final SignalActionExecutor executor =
      new SignalActionExecutor(
          signalActionRepository, taskCreator, outboxAppender, taskDraftGenerator);

  @Test
  @DisplayName("convert는 준비된 draft로 task를 생성하고 signal.converted_to_task를 발행한다")
  void convertCreatesTaskAndAppendsConvertedEvent() {
    // 기본값(pm/medium)과 다른 값으로, executor가 하드코딩 없이 전달받은 draft를 그대로 쓰는지 고정한다.
    String title = "쿠폰 실패 안내 개선";
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(
            SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "decision", "제목", "설명", "전체 영향");
    UUID taskId = UUID.randomUUID();
    when(taskCreator.create(any()))
        .thenReturn(new CreatedTask(taskId, PROJECT_ID, title, "design", "high", "todo"));

    SignalActionResult result =
        executor.convert(signal, USER_ID, prepared(title, Role.DESIGN, Priority.HIGH));

    verify(taskCreator)
        .create(
            CreateTaskCommand.fromSignal(
                PROJECT_ID, WORKSPACE_ID, title, "design", "high", SIGNAL_ID));
    verify(outboxAppender)
        .append(
            WORKSPACE_ID,
            "signal",
            SIGNAL_ID.toString(),
            "signal.converted_to_task",
            Map.of("task_id", taskId.toString()));
    assertThat(result.created()).isTrue();
    assertThat(result.task().id()).isEqualTo(taskId);
  }

  @Test
  @DisplayName("convert는 생성한 task로 원장 적재를 요청한다")
  void convertEnrollsGenerationLedgerWithCreatedTask() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(
            SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "decision", "제목", "설명", "전체 영향");
    UUID taskId = UUID.randomUUID();
    when(taskCreator.create(any()))
        .thenReturn(new CreatedTask(taskId, PROJECT_ID, "제목", "pm", "medium", "todo"));
    PreparedTaskDraft prepared = prepared("제목", Role.PM, Priority.MEDIUM);

    executor.convert(signal, USER_ID, prepared);

    // 적재 여부 판정은 Minsu가 한다. executor는 준비 결과를 그대로 넘길 뿐 비동기 활성 여부를 모른다.
    verify(taskDraftGenerator).enroll(prepared, taskId, WORKSPACE_ID);
  }

  @Test
  @DisplayName("dismiss는 signal.dismissed를 빈 payload로 발행한다")
  void dismissAppendsDismissedEventWithEmptyPayload() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(
            SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "decision", "제목", "설명", "전체 영향");

    SignalActionResult result = executor.dismiss(signal, USER_ID);

    verify(outboxAppender)
        .append(WORKSPACE_ID, "signal", SIGNAL_ID.toString(), "signal.dismissed", Map.of());
    verifyNoMoreInteractions(taskCreator);
    assertThat(result.created()).isTrue();
    assertThat(result.task()).isNull();
  }

  /** 비동기 활성 여부는 Minsu만 알므로 여기서는 불투명 의사를 담아 둔다. */
  private static PreparedTaskDraft prepared(String title, Role role, Priority priority) {
    return new PreparedTaskDraft(
        new TaskDraft(title, role, priority), new TaskDraftAsyncIntent() {});
  }
}
