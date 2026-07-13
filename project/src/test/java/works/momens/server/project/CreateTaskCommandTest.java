package works.momens.server.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 출처 불변식을 생성 시점에 강제하는지(DB CHECK 이전 fail-fast) 검증한다. */
class CreateTaskCommandTest {

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID SIGNAL_ID = UUID.randomUUID();

  @Test
  void manualFactoryHasNoOriginSignalId() {
    CreateTaskCommand command =
        CreateTaskCommand.manual(PROJECT_ID, WORKSPACE_ID, "제목", "pm", "high");

    assertThat(command.origin()).isEqualTo(TaskOrigin.MANUAL);
    assertThat(command.originSignalId()).isNull();
  }

  @Test
  void fromSignalFactoryCarriesOriginSignalId() {
    CreateTaskCommand command =
        CreateTaskCommand.fromSignal(PROJECT_ID, WORKSPACE_ID, "제목", "pm", "high", SIGNAL_ID);

    assertThat(command.origin()).isEqualTo(TaskOrigin.SIGNAL);
    assertThat(command.originSignalId()).isEqualTo(SIGNAL_ID);
  }

  @Test
  void rejectsNullOrigin() {
    assertThatThrownBy(
            () -> new CreateTaskCommand(PROJECT_ID, WORKSPACE_ID, "제목", "pm", "high", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsSignalOriginWithoutSignalId() {
    assertThatThrownBy(
            () ->
                new CreateTaskCommand(
                    PROJECT_ID, WORKSPACE_ID, "제목", "pm", "high", TaskOrigin.SIGNAL, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsManualOriginWithSignalId() {
    assertThatThrownBy(
            () ->
                new CreateTaskCommand(
                    PROJECT_ID, WORKSPACE_ID, "제목", "pm", "high", TaskOrigin.MANUAL, SIGNAL_ID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
