package works.momens.server.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.CreateTaskCommand;
import works.momens.server.project.TaskCreator;
import works.momens.server.signal.SignalActionService;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * outbox 발행이 도메인 write와 같은 트랜잭션에 묶여 있는지(이중 쓰기 방지의 핵심 보장) 실제 PostgreSQL로 검증한다.
 *
 * <p>{@link OutboxAppender}를 각 흐름의 마지막 이벤트에서 던지게 해, 앞서 insert된 task·signal_action까지 원자로 rollback되는지
 * 확인한다. 전파(propagation) 변경이나 별도 트랜잭션 도입으로 원자성이 깨지면 이 테스트가 실패한다.
 */
@SpringBootTest
class OutboxAtomicityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SignalActionService signalActionService;
  @Autowired private TaskCreator taskCreator;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private OutboxAppender outboxAppender;

  @Test
  @DisplayName("convert 중 outbox 발행이 실패하면 task와 signal_action이 모두 rollback된다")
  void convertRollsBackTaskAndActionWhenOutboxFails() {
    UserProfile jinsu = userService.findOrCreate("atomicity-convert@momens.works", "홍길동", null);
    UUID workspace = insertWorkspace("atomicity-convert");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "atomicity-convert-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목");
    doThrow(new RuntimeException("outbox append 실패"))
        .when(outboxAppender)
        .append(any(), any(), any(), eq("signal.converted_to_task"), any());

    assertThatThrownBy(() -> signalActionService.convertToTask(signal, jinsu.id()))
        .isInstanceOf(RuntimeException.class);

    assertThat(count("SELECT COUNT(*) FROM tasks WHERE project_id = ?", project)).isZero();
    assertThat(count("SELECT COUNT(*) FROM signal_actions WHERE signal_id = ?", signal)).isZero();
  }

  @Test
  @DisplayName("dismiss 중 outbox 발행이 실패하면 signal_action이 rollback된다")
  void dismissRollsBackActionWhenOutboxFails() {
    UserProfile jinsu = userService.findOrCreate("atomicity-dismiss@momens.works", "홍길동", null);
    UUID workspace = insertWorkspace("atomicity-dismiss");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "atomicity-dismiss-project");
    UUID signal = insertSignal(workspace, project, "decision", "제목");
    doThrow(new RuntimeException("outbox append 실패"))
        .when(outboxAppender)
        .append(any(), any(), any(), eq("signal.dismissed"), any());

    assertThatThrownBy(() -> signalActionService.dismiss(signal, jinsu.id()))
        .isInstanceOf(RuntimeException.class);

    assertThat(count("SELECT COUNT(*) FROM signal_actions WHERE signal_id = ?", signal)).isZero();
  }

  @Test
  @DisplayName("일반 task 생성 중 task.created 발행이 실패하면 task가 rollback된다")
  void manualCreateRollsBackTaskWhenOutboxFails() {
    UserProfile owner = userService.findOrCreate("atomicity-manual@momens.works", "홍길동", null);
    UUID workspace = insertWorkspace("atomicity-manual");
    UUID project = insertProject(workspace, owner.id(), "atomicity-manual-project");
    doThrow(new RuntimeException("outbox append 실패"))
        .when(outboxAppender)
        .append(any(), any(), any(), eq("task.created"), any());

    assertThatThrownBy(
            () ->
                taskCreator.create(
                    CreateTaskCommand.manual(project, workspace, "제목", "pm", "high")))
        .isInstanceOf(RuntimeException.class);

    assertThat(count("SELECT COUNT(*) FROM tasks WHERE project_id = ?", project)).isZero();
  }

  private Integer count(String sql, Object arg) {
    return jdbcTemplate.queryForObject(sql, Integer.class, arg);
  }

  private UUID insertWorkspace(String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, ?, ?)", id, "모멘스", slug);
    return id;
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
        workspaceId,
        userId,
        role);
  }

  private UUID insertProject(UUID workspaceId, UUID ownerId, String name) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO projects (id, workspace_id, name, owner_id) VALUES (?, ?, ?, ?)",
        id,
        workspaceId,
        name,
        ownerId);
    return id;
  }

  private UUID insertSignal(UUID workspaceId, UUID projectId, String type, String title) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO signals (id, workspace_id, project_id, type, title, description)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        projectId,
        type,
        title,
        "본문");
    return id;
  }
}
