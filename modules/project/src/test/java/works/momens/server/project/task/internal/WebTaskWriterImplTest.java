package works.momens.server.project.task.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
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
import works.momens.server.common.api.BusinessException;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.core.ProjectReader;
import works.momens.server.project.milestone.MilestoneDirectory;
import works.momens.server.workspace.LabelAllocator;
import works.momens.server.workspace.WorkspaceAccess;

@ExtendWith(MockitoExtension.class)
class WebTaskWriterImplTest {
  private final UUID workspaceId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  @Mock private TaskRepository taskRepository;
  @Mock private ProjectReader projectReader;
  @Mock private MilestoneDirectory milestoneDirectory;
  @Mock private WorkspaceAccess workspaceAccess;
  @Mock private LabelAllocator labelAllocator;
  @Mock private OutboxAppender outboxAppender;
  @InjectMocks private WebTaskWriterImpl writer;

  @Test
  @DisplayName("웹 태스크 생성은 레이블을 발급하고 manual task.created outbox를 기록한다")
  void createsTaskAndOutboxEvent() {
    when(projectReader.workspaceIdOf(projectId)).thenReturn(Optional.of(workspaceId));
    when(workspaceAccess.isMember(workspaceId, userId)).thenReturn(true);
    when(labelAllocator.allocateMomLabel(workspaceId)).thenReturn("MOM-867");

    writer.create(projectId, userId, "제목", null, null, null, null, null, LocalDate.of(2026, 8, 31));

    ArgumentCaptor<Task> task = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(task.capture());
    assertThat(task.getValue().getStatus()).isEqualTo("backlog");
    assertThat(task.getValue().getPriority()).isEqualTo("medium");
    Map<String, Object> payload = new HashMap<>();
    payload.put("origin_type", "manual");
    payload.put("origin_signal_id", null);
    verify(outboxAppender)
        .append(
            eq(workspaceId),
            eq("task"),
            eq(task.getValue().getId().toString()),
            eq("task.created"),
            eq(payload));
  }

  @Test
  @DisplayName("PATCH는 빈 title과 status를 무시하고 null status는 거부한다")
  void preservesLegacyPatchSemantics() {
    Task task =
        Task.web(workspaceId, projectId, "MOM-867", "기존", null, "todo", "high", null, null, null);
    when(taskRepository.findByIdAndDeletedAtIsNull(task.getId())).thenReturn(Optional.of(task));
    when(workspaceAccess.isMember(workspaceId, userId)).thenReturn(true);

    writer.update(
        task.getId(),
        userId,
        "",
        true,
        null,
        false,
        "",
        true,
        "",
        true,
        null,
        false,
        null,
        false,
        null,
        false);

    assertThat(task.getTitle()).isEqualTo("기존");
    assertThat(task.getStatus()).isEqualTo("todo");
    assertThat(task.getPriority()).isEqualTo("high");
    assertThatThrownBy(
            () ->
                writer.update(
                    task.getId(),
                    userId,
                    null,
                    false,
                    null,
                    false,
                    null,
                    true,
                    null,
                    false,
                    null,
                    false,
                    null,
                    false,
                    null,
                    false))
        .isInstanceOf(BusinessException.class);
  }
}
