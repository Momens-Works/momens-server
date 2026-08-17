package works.momens.server.web.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * {@code GET /api/workspaces}, {@code GET /api/workspaces/{workspaceId}} 실배선 통합 테스트(MOM-0851).
 *
 * <p>실토큰(auth public testFixtures)과 실제 PostgreSQL로 보안 체인부터 응답 shape·정렬·에러 매핑까지 끝까지 확인합니다. 사용자는 user
 * public API로 만들고, workspace/멤버십은 아직 생성 public API가 없어 소유 스키마에 SQL로 시드합니다. 레거시 {@code
 * session_token} 쿠키만 가진 요청도 인증을 통과하는지 확인합니다(ADR-0017).
 */
@SpringBootTest
@AutoConfigureMockMvc
class WebWorkspacesIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void returnsMemberWorkspacesSortedByCreatedAtDesc() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-sorted@momens.works", "홍길동", null);
    UUID older = insertWorkspace("web-it-older", null);
    UUID newer = insertWorkspace("web-it-newer", "제품팀 워크스페이스");
    UUID othersOnly = insertWorkspace("web-it-others-only", null);
    Instant now = Instant.now();
    updateCreatedAt(older, now.minusSeconds(60));
    updateCreatedAt(newer, now);
    addMember(older, caller.id(), "owner");
    addMember(newer, caller.id(), "member");
    addMember(
        othersOnly,
        userService.findOrCreate("web-it-other@momens.works", "박외부", null).id(),
        "owner");

    mockMvc
        .perform(authorized(get("/api/workspaces"), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces.length()").value(2))
        .andExpect(jsonPath("$.workspaces[0].id").value(newer.toString()))
        .andExpect(jsonPath("$.workspaces[0].description").value("제품팀 워크스페이스"))
        .andExpect(jsonPath("$.workspaces[1].id").value(older.toString()))
        .andExpect(jsonPath("$.workspaces[1].description").doesNotExist());
  }

  @Test
  void returnsEmptyArrayWhenCallerHasNoWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-empty@momens.works", "홍길동", null);

    mockMvc
        .perform(authorized(get("/api/workspaces"), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces").isArray())
        .andExpect(jsonPath("$.workspaces.length()").value(0));
  }

  @Test
  void getReturnsWorkspaceWithoutWrapper() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-get@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace("web-it-get", null);
    addMember(workspaceId, caller.id(), "owner");

    mockMvc
        .perform(authorized(get("/api/workspaces/{workspaceId}", workspaceId), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(workspaceId.toString()))
        .andExpect(jsonPath("$.slug").value("web-it-get"))
        .andExpect(jsonPath("$.workspaces").doesNotExist());
  }

  @Test
  void getReturnsNotFoundForUnknownWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-404@momens.works", "홍길동", null);

    mockMvc
        .perform(authorized(get("/api/workspaces/{workspaceId}", UUID.randomUUID()), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void getReturnsForbiddenWhenCallerIsNotMember() throws Exception {
    UserProfile owner = userService.findOrCreate("web-it-owner@momens.works", "홍길동", null);
    UserProfile stranger = userService.findOrCreate("web-it-stranger@momens.works", "김철수", null);
    UUID workspaceId = insertWorkspace("web-it-forbidden", null);
    addMember(workspaceId, owner.id(), "owner");

    mockMvc
        .perform(authorized(get("/api/workspaces/{workspaceId}", workspaceId), stranger.id()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void returnsUnauthorizedWithoutAnyToken() throws Exception {
    mockMvc
        .perform(get("/api/workspaces").header("API-Version", "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }

  @Test
  void authenticatesViaAccessTokenCookie() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-access-cookie@momens.works", "홍길동", null);

    mockMvc
        .perform(
            get("/api/workspaces")
                .cookie(new Cookie("access_token", accessTokens.issueAccessToken(caller.id())))
                .header("API-Version", "1"))
        .andExpect(status().isOk());
  }

  @Test
  void authenticatesViaLegacySessionTokenCookieWhenNoHeaderOrAccessCookie() throws Exception {
    UserProfile caller =
        userService.findOrCreate("web-it-session-cookie@momens.works", "홍길동", null);

    mockMvc
        .perform(
            get("/api/workspaces")
                .cookie(new Cookie("session_token", accessTokens.issueAccessToken(caller.id())))
                .header("API-Version", "1"))
        .andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder builder, UUID userId) {
    return builder
        .header("Authorization", "Bearer " + accessTokens.issueAccessToken(userId))
        .header("API-Version", "1");
  }

  private UUID insertWorkspace(String slug, String description) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workspaces (id, name, slug, description) VALUES (?, ?, ?, ?)",
        id,
        "모멘스",
        slug,
        description);
    return id;
  }

  private void updateCreatedAt(UUID workspaceId, Instant createdAt) {
    jdbcTemplate.update(
        "UPDATE workspaces SET created_at = ? WHERE id = ?",
        Timestamp.from(createdAt),
        workspaceId);
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
        workspaceId,
        userId,
        role);
  }
}
