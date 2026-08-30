package works.momens.server.web.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskSnapshot;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateReader;
import works.momens.server.project.taskupdate.TaskUpdateWriter;
import works.momens.server.workspace.WorkspaceAccess;

@ExtendWith(MockitoExtension.class)
class TaskUpdateServiceTest {
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();
  private static final UUID UPDATE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Mock private TaskReader taskReader;
  @Mock private TaskUpdateReader taskUpdateReader;
  @Mock private TaskUpdateWriter taskUpdateWriter;
  @Mock private WorkspaceAccess workspaceAccess;
  @InjectMocks private TaskUpdateService service;

  @Test
  void listChecksTaskMembershipBeforeReadingUpdates() {
    when(taskReader.findSnapshot(TASK_ID)).thenReturn(Optional.of(task()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);
    when(taskUpdateReader.listByTaskId(TASK_ID)).thenReturn(List.of(update()));

    assertThat(service.list(TASK_ID, USER_ID)).containsExactly(update());

    verify(taskUpdateReader).listByTaskId(TASK_ID);
  }

  @Test
  void createAndDeleteDelegateToTaskUpdateWriter() {
    Map<String, Object> metadata = Map.of("source", "web");
    when(taskUpdateWriter.create(TASK_ID, USER_ID, "내용", "comment", metadata)).thenReturn(update());

    assertThat(service.create(TASK_ID, USER_ID, "내용", "comment", metadata)).isEqualTo(update());
    service.delete(TASK_ID, UPDATE_ID, USER_ID);

    verify(taskUpdateWriter).create(TASK_ID, USER_ID, "내용", "comment", metadata);
    verify(taskUpdateWriter).delete(TASK_ID, UPDATE_ID, USER_ID);
  }

  private static TaskSnapshot task() {
    return new TaskSnapshot(
        TASK_ID,
        WORKSPACE_ID,
        PROJECT_ID,
        null,
        "MOM-100",
        "태스크",
        null,
        "todo",
        "medium",
        null,
        null,
        LocalDate.parse("2026-08-31"),
        Instant.parse("2026-08-21T00:00:00Z"),
        Instant.parse("2026-08-21T00:00:00Z"));
  }

  private static TaskUpdateDetail update() {
    return new TaskUpdateDetail(
        UPDATE_ID,
        WORKSPACE_ID,
        PROJECT_ID,
        TASK_ID,
        USER_ID,
        "내용",
        "comment",
        Map.of("source", "web"),
        Instant.parse("2026-08-21T00:00:00Z"),
        Instant.parse("2026-08-21T00:00:00Z"));
  }
}
