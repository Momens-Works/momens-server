package works.momens.server.project.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.outbox.OutboxAppender;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.CreatedTask;
import works.momens.server.workspace.LabelAllocator;

/**
 * 태스크 생성 규칙 검증.
 *
 * <p>DB 없이 생성 로직(라벨 발급, todo 시작, 기본값)만 확인합니다. 실제 영속성과 시퀀스 증가는 app 통합테스트(MOM-62 단위 2)에서 전체 컨텍스트로
 * 검증합니다. workspace의 라벨 발급 구현이 package-private라 project 슬라이스에서 조립하기 어렵기 때문입니다.
 */
@ExtendWith(MockitoExtension.class)
class TaskCreatorImplTest {

  @Mock private TaskRepository taskRepository;
  @Mock private LabelAllocator labelAllocator;
  @Mock private OutboxAppender outboxAppender;
  @InjectMocks private TaskCreatorImpl taskCreator;

  @Test
  void createAllocatesMomLabelAndStartsAsTodo() {
    UUID projectId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    when(labelAllocator.allocateMomLabel(workspaceId)).thenReturn("MOM-0001");

    CreatedTask created =
        taskCreator.create(
            CreateTaskCommand.manual(projectId, workspaceId, "권한 요청 점검", "backend", "high"));

    ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(captor.capture());
    Task saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo("todo");
    assertThat(saved.getLabel()).isEqualTo("MOM-0001");
    assertThat(saved.getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(saved.getProjectId()).isEqualTo(projectId);
    assertThat(saved.getTitle()).isEqualTo("권한 요청 점검");
    assertThat(saved.getPriority()).isEqualTo("high");
    assertThat(saved.getRole()).isEqualTo("backend");

    assertThat(created.id()).isEqualTo(saved.getId());
    assertThat(created.projectId()).isEqualTo(projectId);
    assertThat(created.status()).isEqualTo("todo");
    assertThat(created.priority()).isEqualTo("high");
    assertThat(created.role()).isEqualTo("backend");
  }

  @Test
  void createDefaultsPriorityToMediumWhenAbsent() {
    UUID workspaceId = UUID.randomUUID();
    when(labelAllocator.allocateMomLabel(any())).thenReturn("MOM-0002");

    CreatedTask created =
        taskCreator.create(
            CreateTaskCommand.manual(UUID.randomUUID(), workspaceId, "제목", "pm", null));

    assertThat(created.priority()).isEqualTo("medium");
    assertThat(created.role()).isEqualTo("pm");
    assertThat(created.status()).isEqualTo("todo");
  }

  @Test
  void appendsTaskCreatedOutboxEventWithManualOrigin() {
    UUID projectId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    when(labelAllocator.allocateMomLabel(workspaceId)).thenReturn("MOM-0003");

    CreatedTask created =
        taskCreator.create(CreateTaskCommand.manual(projectId, workspaceId, "제목", "pm", "medium"));

    Map<String, Object> expectedPayload = new HashMap<>();
    expectedPayload.put("origin_type", "manual");
    expectedPayload.put("origin_signal_id", null);
    verify(outboxAppender)
        .append(
            eq(workspaceId),
            eq("task"),
            eq(created.id().toString()),
            eq("task.created"),
            eq(expectedPayload));
  }

  @Test
  void appendsTaskCreatedOutboxEventWithSignalOrigin() {
    UUID projectId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    UUID signalId = UUID.randomUUID();
    when(labelAllocator.allocateMomLabel(workspaceId)).thenReturn("MOM-0004");

    CreatedTask created =
        taskCreator.create(
            CreateTaskCommand.fromSignal(projectId, workspaceId, "제목", "pm", "medium", signalId));

    verify(outboxAppender)
        .append(
            eq(workspaceId),
            eq("task"),
            eq(created.id().toString()),
            eq("task.created"),
            eq(Map.of("origin_type", "signal", "origin_signal_id", signalId.toString())));
  }
}
