package works.momens.server.project.task;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskCommandTest {

  @Test
  void createRequiresStatus() {
    assertThatIllegalArgumentException().isThrownBy(() -> createCommand(null));
    assertThatIllegalArgumentException().isThrownBy(() -> createCommand(" "));
  }

  @Test
  void patchRequiresValueWhenNonNullableFieldIsSet() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> patchCommand(null, true, "todo", false, "medium", false));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> patchCommand("제목", false, null, true, "medium", false));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> patchCommand("제목", false, "todo", false, null, true));
  }

  private static CreateTaskCommand createCommand(String status) {
    return new CreateTaskCommand(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "제목",
        null,
        status,
        null,
        "medium",
        null,
        null,
        null,
        TaskOrigin.MANUAL,
        null);
  }

  private static PatchTaskCommand patchCommand(
      String title,
      boolean titleSet,
      String status,
      boolean statusSet,
      String priority,
      boolean prioritySet) {
    return new PatchTaskCommand(
        UUID.randomUUID(),
        title,
        titleSet,
        null,
        false,
        status,
        statusSet,
        priority,
        prioritySet,
        null,
        false,
        null,
        false,
        null,
        false);
  }
}
