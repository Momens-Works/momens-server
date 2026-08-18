package works.momens.server.web.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
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
  @DisplayName("멤버인 워크스페이스만 생성 시각 내림차순으로 응답한다")
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
  @DisplayName("워크스페이스가 없으면 빈 배열로 응답한다")
  void returnsEmptyArrayWhenCallerHasNoWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-empty@momens.works", "홍길동", null);

    mockMvc
        .perform(authorized(get("/api/workspaces"), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces").isArray())
        .andExpect(jsonPath("$.workspaces.length()").value(0));
  }

  @Test
  @DisplayName("단건 조회는 래퍼 없이 워크스페이스 객체를 응답한다")
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
  @DisplayName("없는 워크스페이스는 404 WORKSPACE_NOT_FOUND로 응답한다")
  void getReturnsNotFoundForUnknownWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-404@momens.works", "홍길동", null);

    mockMvc
        .perform(authorized(get("/api/workspaces/{workspaceId}", UUID.randomUUID()), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  @DisplayName("path id가 UUID가 아니면 400 COMMON_BAD_REQUEST로 응답한다")
  void getReturnsBadRequestForNonUuidPathId() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-400@momens.works", "홍길동", null);

    mockMvc
        .perform(authorized(get("/api/workspaces/{workspaceId}", "not-a-uuid"), caller.id()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_BAD_REQUEST"));
  }

  @Test
  @DisplayName("멤버가 아니면 403 AUTH_FORBIDDEN으로 응답한다")
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
  @DisplayName("토큰이 없으면 401 AUTH_UNAUTHORIZED로 응답한다")
  void returnsUnauthorizedWithoutAnyToken() throws Exception {
    mockMvc
        .perform(get("/api/workspaces").header("API-Version", "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }

  @Test
  @DisplayName("access_token 쿠키만으로 인증을 통과한다")
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
  @DisplayName("레거시 session_token 쿠키만으로 인증을 통과한다")
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

  @Test
  @DisplayName("이미 사용 중인 slug는 사유와 대체 slug를 함께 응답한다")
  void slugAvailableReportsTakenSlugWithSuggestion() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-slug-taken@momens.works", "홍길동", null);
    insertWorkspace("web-it-taken", null);

    mockMvc
        .perform(
            authorized(get("/api/workspaces/slug-available"), caller.id())
                .param("slug", "web-it-taken"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("web-it-taken"))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.reason").value("taken"))
        .andExpect(jsonPath("$.suggestion").value("web-it-taken-2"));
  }

  @Test
  @DisplayName("사용할 수 있는 slug는 사유 없이 available로 응답한다")
  void slugAvailableReportsFreeSlugWithoutReason() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-slug-free@momens.works", "홍길동", null);

    mockMvc
        .perform(
            authorized(get("/api/workspaces/slug-available"), caller.id())
                .param("slug", "web-it-free"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.reason").doesNotExist())
        .andExpect(jsonPath("$.suggestion").doesNotExist());
  }

  @Test
  @DisplayName("예약어로 지정된 slug는 reserved 사유로 응답한다")
  void slugAvailableRejectsReservedSlug() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-slug-reserved@momens.works", "홍길동", null);

    mockMvc
        .perform(
            authorized(get("/api/workspaces/slug-available"), caller.id())
                .param("slug", "settings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.reason").value("reserved"));
  }

  @Test
  @DisplayName("admin은 이름과 slug를 수정할 수 있다")
  void updateAppliesNameAndSlugForAdmin() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-update-admin@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace("web-it-update", "기존 설명");
    addMember(workspaceId, caller.id(), "admin");

    mockMvc
        .perform(
            authorized(patch("/api/workspaces/{workspaceId}", workspaceId), caller.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새 이름\",\"slug\":\"web-it-updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("새 이름"))
        .andExpect(jsonPath("$.slug").value("web-it-updated"))
        .andExpect(jsonPath("$.description").value("기존 설명"));
  }

  @Test
  @DisplayName("admin 미만인 멤버의 수정 요청은 403 AUTH_FORBIDDEN으로 응답한다")
  void updateRejectsMemberWithoutAdminRole() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-update-member@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace("web-it-update-member", null);
    addMember(workspaceId, caller.id(), "member");

    mockMvc
        .perform(
            authorized(patch("/api/workspaces/{workspaceId}", workspaceId), caller.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새 이름\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("존재하지 않는 워크스페이스의 수정 요청은 404 WORKSPACE_NOT_FOUND로 응답한다")
  void updateReturnsNotFoundForUnknownWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-update-404@momens.works", "홍길동", null);

    mockMvc
        .perform(
            authorized(patch("/api/workspaces/{workspaceId}", UUID.randomUUID()), caller.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"새 이름\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  @DisplayName("다른 워크스페이스에서 사용 중인 slug로 수정하면 409 WORKSPACE_SLUG_ALREADY_EXISTS로 응답한다")
  void updateRejectsSlugTakenByAnotherWorkspace() throws Exception {
    UserProfile caller = userService.findOrCreate("web-it-update-dup@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace("web-it-update-mine", null);
    insertWorkspace("web-it-update-theirs", null);
    addMember(workspaceId, caller.id(), "owner");

    mockMvc
        .perform(
            authorized(patch("/api/workspaces/{workspaceId}", workspaceId), caller.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"web-it-update-theirs\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_SLUG_ALREADY_EXISTS"));
  }

  @Test
  @DisplayName("예약어로 지정된 slug로 수정하면 400 WORKSPACE_RESERVED_SLUG로 응답한다")
  void updateRejectsReservedSlug() throws Exception {
    UserProfile caller =
        userService.findOrCreate("web-it-update-reserved@momens.works", "홍길동", null);
    UUID workspaceId = insertWorkspace("web-it-update-reserved", null);
    addMember(workspaceId, caller.id(), "owner");

    mockMvc
        .perform(
            authorized(patch("/api/workspaces/{workspaceId}", workspaceId), caller.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"settings\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_RESERVED_SLUG"));
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
