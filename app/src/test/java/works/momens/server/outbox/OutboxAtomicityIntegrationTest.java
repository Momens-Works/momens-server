package works.momens.server.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * outbox append 실패가 도메인 write를 함께 rollback시키는지 검증한다(SD-5 원자성 완료 조건). {@link OutboxAppender}를 실패하는
 * mock으로 바꿔치기해, convert-to-task 트랜잭션의 {@code task insert + signal_actions insert + outbox insert}가
 * 정말 하나의 트랜잭션인지(부분 커밋이 없는지) 실배선으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OutboxAtomicityIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private OutboxAppender outboxAppender;

  @Test
  @DisplayName("convert-to-task 중 outbox append가 실패하면 task와 signal_actions도 함께 rollback된다")
  void rollsBackTaskAndSignalActionWhenOutboxAppendFails() throws Exception {
    doThrow(new RuntimeException("outbox append 실패(테스트 유도)"))
        .when(outboxAppender)
        .append(any(), any(), any(), any(), any());

    UserProfile user = userService.findOrCreate("outbox-atomicity-it@momens.works", "김규일", null);
    UUID workspace = insertWorkspace("outbox-atomicity");
    addMember(workspace, user.id(), "owner");
    UUID project = insertProject(workspace, user.id(), "outbox-atomicity-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목");

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(user.id()))
                .header("API-Version", "1"))
        .andExpect(status().is5xxServerError());

    Integer taskCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tasks WHERE project_id = ?", Integer.class, project);
    Integer signalActionCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM signal_actions WHERE signal_id = ?", Integer.class, signal);

    assertThat(taskCount).isZero();
    assertThat(signalActionCount).isZero();
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
        "INSERT INTO signals (id, workspace_id, project_id, type, title, description) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        projectId,
        type,
        title,
        "본문");
    return id;
  }
}
