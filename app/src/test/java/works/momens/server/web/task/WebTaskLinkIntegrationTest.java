package works.momens.server.web.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * 태스크 연결 endpoint 네 개를 애플리케이션을 기동해 검증합니다.
 *
 * <p>컨트롤러만 기동하는 테스트로는 에러 코드 변환을 검증할 수 없으며, source-ref 생성과 연결이 하나의 트랜잭션에서 처리되는지도 데이터베이스에 저장된 행을 조회해야
 * 확인할 수 있습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebTaskLinkIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("메모리 연결을 해제하면 행은 남고 삭제 시각만 채워지며 다시 해제하면 404로 응답한다")
  void linksAndUnlinksMemory() throws Exception {
    UserProfile caller = userService.findOrCreate("task-link-memory@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace();
    UUID taskId = insertTask(workspaceId, insertProject(workspaceId, caller.id()));
    UUID memoryId = insertMemory(workspaceId);
    addMember(workspaceId, caller.id());

    mockMvc
        .perform(authorized(post("/api/tasks/{t}/memories/{m}", taskId, memoryId), caller.id()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("linked"));
    assertThat(relationCount(workspaceId, taskId, "MEMORY", memoryId, true)).isEqualTo(1);

    mockMvc
        .perform(authorized(delete("/api/tasks/{t}/memories/{m}", taskId, memoryId), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("unlinked"));
    assertThat(relationCount(workspaceId, taskId, "MEMORY", memoryId, true)).isZero();
    assertThat(relationCount(workspaceId, taskId, "MEMORY", memoryId, false)).isEqualTo(1);

    mockMvc
        .perform(authorized(delete("/api/tasks/{t}/memories/{m}", taskId, memoryId), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("CONTEXT_LINK_NOT_FOUND"));
  }

  @Test
  @DisplayName("링크를 첨부하면 source-ref와 연결이 함께 생성된다")
  void createsSourceRefAndItsLinkInOneRequest() throws Exception {
    UserProfile caller = userService.findOrCreate("task-link-source@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace();
    UUID taskId = insertTask(workspaceId, insertProject(workspaceId, caller.id()));
    addMember(workspaceId, caller.id());

    String body =
        mockMvc
            .perform(
                authorized(post("/api/tasks/{t}/source-refs", taskId), caller.id())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"source_url":"https://www.notion.so/momens/scrum","source_type":"notion","title":"스크럼 노트"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("linked"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID sourceRefId = UUID.fromString(body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM source_refs WHERE id = ?", sourceRefId);
    assertThat(row.get("source_type")).isEqualTo("NOTION");
    assertThat(row.get("source_object_type")).isEqualTo("link");
    assertThat(relationCount(workspaceId, taskId, "SOURCE_OBJECT", sourceRefId, true)).isEqualTo(1);

    mockMvc
        .perform(
            authorized(delete("/api/tasks/{t}/source-refs/{s}", taskId, sourceRefId), caller.id()))
        .andExpect(status().isOk());
    assertThat(relationCount(workspaceId, taskId, "SOURCE_OBJECT", sourceRefId, true)).isZero();
  }

  @Test
  @DisplayName("다른 워크스페이스의 메모리와 존재하지 않는 태스크와 비멤버 요청에 각각 400과 404와 403으로 응답한다")
  void rejectsCrossWorkspaceAndNonMemberAndUnknownTask() throws Exception {
    UserProfile caller = userService.findOrCreate("task-link-denied@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace();
    UUID taskId = insertTask(workspaceId, insertProject(workspaceId, caller.id()));
    UUID otherMemoryId = insertMemory(insertWorkspace());
    addMember(workspaceId, caller.id());

    mockMvc
        .perform(
            authorized(post("/api/tasks/{t}/memories/{m}", taskId, otherMemoryId), caller.id()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CONTEXT_CROSS_WORKSPACE_LINK_NOT_ALLOWED"));

    mockMvc
        .perform(
            authorized(
                post("/api/tasks/{t}/memories/{m}", UUID.randomUUID(), otherMemoryId), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("TASK_NOT_FOUND"));

    UserProfile stranger = userService.findOrCreate("task-link-stranger@momens.works", "김철수", null);
    mockMvc
        .perform(
            authorized(delete("/api/tasks/{t}/memories/{m}", taskId, otherMemoryId), stranger.id()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  private MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder builder, UUID userId) {
    return builder
        .header("Authorization", "Bearer " + accessTokens.issueAccessToken(userId))
        .header("API-Version", "1");
  }

  private UUID insertWorkspace() {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, '모멘스', ?)", id, "task-link-" + id);
    return id;
  }

  private UUID insertProject(UUID workspaceId, UUID ownerId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO projects (id, workspace_id, name, owner_id) VALUES (?, ?, '1차 스프린트', ?)",
        id,
        workspaceId,
        ownerId);
    return id;
  }

  private UUID insertTask(UUID workspaceId, UUID projectId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO tasks (id, workspace_id, project_id, label, title, status, priority,"
            + " origin_type) VALUES (?, ?, ?, ?, '태스크 목록 조회 API', 'todo', 'medium', 'manual')",
        id,
        workspaceId,
        projectId,
        "MOM-" + Math.abs(id.hashCode() % 10000));
    return id;
  }

  private UUID insertMemory(UUID workspaceId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO confirmed_memories (id, workspace_id, memory_type, title, status)"
            + " VALUES (?, ?, 'DECISION', '초대 링크는 7일 뒤 만료한다', 'ACTIVE')",
        id,
        workspaceId);
    return id;
  }

  private void addMember(UUID workspaceId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, 'member')",
        workspaceId,
        userId);
  }

  private int relationCount(
      UUID workspaceId, UUID taskId, String toType, UUID toId, boolean notDeleted) {
    String sql =
        "SELECT count(*) FROM entity_relations WHERE workspace_id = ? AND from_entity_type = 'TASK'"
            + " AND from_entity_id = ? AND relation_type = 'LINKED_TO' AND to_entity_type = ?"
            + " AND to_entity_id = ? AND deleted_at IS "
            + (notDeleted ? "NULL" : "NOT NULL");
    return jdbcTemplate.queryForObject(sql, Integer.class, workspaceId, taskId, toType, toId);
  }
}
