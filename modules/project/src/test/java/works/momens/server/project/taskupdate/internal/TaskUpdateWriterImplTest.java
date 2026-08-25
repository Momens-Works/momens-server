package works.momens.server.project.taskupdate.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskScope;
import works.momens.server.workspace.WorkspaceAccess;

@ExtendWith(MockitoExtension.class)
class TaskUpdateWriterImplTest {
  private final UUID workspaceId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  @Mock private TaskUpdateRepository taskUpdateRepository;
  @Mock private TaskReader taskReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @InjectMocks private TaskUpdateWriterImpl writer;

  @Test
  @DisplayName("태스크 업데이트는 task 공개 계약으로 소속을 얻고 kind를 정규화한다")
  void createsFromTaskScopeAndNormalizesKind() {
    when(taskReader.findScope(taskId))
        .thenReturn(Optional.of(new TaskScope(workspaceId, projectId)));
    when(workspaceAccess.isMember(workspaceId, userId)).thenReturn(true);

    writer.create(taskId, userId, " 내용 ", " Comment ", Map.of());

    ArgumentCaptor<TaskUpdate> update = ArgumentCaptor.forClass(TaskUpdate.class);
    verify(taskUpdateRepository).save(update.capture());
    assertThat(update.getValue().getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(update.getValue().getProjectId()).isEqualTo(projectId);
    assertThat(update.getValue().getBody()).isEqualTo("내용");
    assertThat(update.getValue().getKind()).isEqualTo("comment");
  }
}
