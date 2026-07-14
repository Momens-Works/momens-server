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

  private final SignalActionExecutor executor =
      new SignalActionExecutor(signalActionRepository, taskCreator, outboxAppender);

  @Test
  @DisplayName("convert는 task를 signal 출처로 생성하고 signal.converted_to_task를 발행한다")
  void convertCreatesSignalOriginTaskAndAppendsConvertedEvent() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목");
    UUID taskId = UUID.randomUUID();
    when(taskCreator.create(any()))
        .thenReturn(new CreatedTask(taskId, PROJECT_ID, "제목", "pm", "medium", "todo"));

    SignalActionResult result = executor.convert(signal, USER_ID, "제목", "pm", "medium");

    verify(taskCreator)
        .create(
            CreateTaskCommand.fromSignal(
                PROJECT_ID, WORKSPACE_ID, "제목", "pm", "medium", SIGNAL_ID));
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
  @DisplayName("dismiss는 signal.dismissed를 빈 payload로 발행한다")
  void dismissAppendsDismissedEventWithEmptyPayload() {
    SignalReader.Snapshot signal =
        new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "제목");

    SignalActionResult result = executor.dismiss(signal, USER_ID);

    verify(outboxAppender)
        .append(WORKSPACE_ID, "signal", SIGNAL_ID.toString(), "signal.dismissed", Map.of());
    verifyNoMoreInteractions(taskCreator);
    assertThat(result.created()).isTrue();
    assertThat(result.task()).isNull();
  }
}
