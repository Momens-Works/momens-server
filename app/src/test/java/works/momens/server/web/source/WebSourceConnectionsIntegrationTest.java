package works.momens.server.web.source;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
@AutoConfigureMockMvc
/**
 * source 연동 endpoint 네 개를 애플리케이션 실행 환경에서 검증하는 통합 테스트입니다(MOM-0870).
 *
 * <p>실제 토큰과 PostgreSQL을 사용해 보안 필터부터 응답 형식, 권한별 응답, 에러 코드까지 전체 흐름을 검증합니다. 사용자는 user 모듈의 public API로
 * 생성하고, 워크스페이스와 멤버십, source 데이터는 생성 endpoint가 없으므로 SQL로 직접 저장합니다.
 *
 * <p>provider가 호출하는 콜백 경로는 토큰 없이 요청해 공개 경로 설정이 실제로 적용되었는지도 함께 확인합니다.
 */
class WebSourceConnectionsIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("source 연결 목록을 생성 시각 내림차순으로 응답한다")
  void listReturnsConnectionsNewestFirst() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-list");
    UserProfile caller = user("src-list-caller@momens.works", "가장 먼저 오는 이름");
    addMember(workspaceId, caller.id(), "member");
    insertConnection(workspaceId, "GITHUB", "ext-old", "2026-08-01T00:00:00Z");
    insertConnection(workspaceId, "SLACK", "ext-new", "2026-08-03T00:00:00Z");

    mockMvc
        .perform(
            authorized(get("/api/workspaces/{id}/source-connections", workspaceId), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source_connections.length()").value(2))
        .andExpect(jsonPath("$.source_connections[0].source_type").value("SLACK"))
        .andExpect(jsonPath("$.source_connections[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.source_connections[0].captures_read_count").value(0))
        .andExpect(jsonPath("$.source_connections[1].source_type").value("GITHUB"));
  }

  @Test
  @DisplayName("멤버가 아닌 사용자의 source 연결 목록 조회를 거부한다")
  void listRejectsUserWhoIsNotAMember() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-forbidden");
    UserProfile stranger = user("src-forbidden@momens.works", "가장 먼저 오는 이름");

    mockMvc
        .perform(
            authorized(get("/api/workspaces/{id}/source-connections", workspaceId), stranger.id()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("존재하지 않는 워크스페이스의 source 연결 목록 조회를 거부한다")
  void listRejectsWorkspaceThatDoesNotExist() throws Exception {
    UserProfile caller = user("src-missing-ws@momens.works", "가장 먼저 오는 이름");

    mockMvc
        .perform(
            authorized(
                get("/api/workspaces/{id}/source-connections", UUID.randomUUID()), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  @DisplayName("토큰이 없는 source 연결 목록 조회를 거부한다")
  void listRejectsRequestWithoutToken() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-noauth");

    mockMvc
        .perform(
            get("/api/workspaces/{id}/source-connections", workspaceId).header("API-Version", "1"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("member 권한으로는 source 연결을 시작할 수 없다")
  void installRejectsMemberWhoIsNotAdminOrOwner() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-install-member");
    UserProfile member = user("src-install-member@momens.works", "가장 먼저 오는 이름");
    addMember(workspaceId, member.id(), "member");

    mockMvc
        .perform(
            authorized(
                get("/api/workspaces/{id}/source-connections/install", workspaceId)
                    .param("provider", "github"),
                member.id()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("지원하지 않는 provider로는 source 연결을 시작할 수 없다")
  void installRejectsProviderThatIsNotSupported() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-install-unknown");
    UserProfile admin = user("src-install-unknown@momens.works", "가장 먼저 오는 이름");
    addMember(workspaceId, admin.id(), "admin");

    mockMvc
        .perform(
            authorized(
                get("/api/workspaces/{id}/source-connections/install", workspaceId)
                    .param("provider", "linear"),
                admin.id()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SOURCE_UNSUPPORTED_PROVIDER"));
  }

  @Test
  @DisplayName("provider 설정이 없으면 500으로 응답한다")
  void installReportsMissingProviderConfigurationAsServerError() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-install-unconfigured");
    UserProfile admin = user("src-install-unconfigured@momens.works", "가장 먼저 오는 이름");
    addMember(workspaceId, admin.id(), "admin");

    mockMvc
        .perform(
            authorized(
                get("/api/workspaces/{id}/source-connections/install", workspaceId)
                    .param("provider", "github"),
                admin.id()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error.code").value("SOURCE_PROVIDER_UNCONFIGURED"));
  }

  @Test
  @DisplayName("source-ref를 검증 완료로 표시하고 레거시와 동일한 필드로 응답한다")
  void verifyMarksSourceRefAndReturnsLegacyShape() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-verify");
    UserProfile caller = user("src-verify@momens.works", "가장 먼저 오는 이름");
    addMember(workspaceId, caller.id(), "member");
    UUID sourceRefId = insertSourceRef(workspaceId);

    mockMvc
        .perform(authorized(post("/api/source-refs/{id}/verify", sourceRefId), caller.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sourceRefId.toString()))
        .andExpect(jsonPath("$.workspace_id").value(workspaceId.toString()))
        .andExpect(jsonPath("$.verified_by_user_id").value(caller.id().toString()))
        .andExpect(jsonPath("$.verified_at").exists())
        .andExpect(jsonPath("$.text").value("수집한 원문 전체"));
  }

  @Test
  @DisplayName("source-ref가 속한 워크스페이스의 멤버가 아니면 검증을 거부한다")
  void verifyRejectsUserWhoIsNotAMemberOfTheSourceRefWorkspace() throws Exception {
    UUID workspaceId = insertWorkspace("web-src-verify-forbidden");
    UserProfile stranger = user("src-verify-forbidden@momens.works", "가장 먼저 오는 이름");
    UUID sourceRefId = insertSourceRef(workspaceId);

    mockMvc
        .perform(authorized(post("/api/source-refs/{id}/verify", sourceRefId), stranger.id()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("존재하지 않는 source-ref의 검증을 거부한다")
  void verifyRejectsSourceRefThatDoesNotExist() throws Exception {
    UserProfile caller = user("src-verify-missing@momens.works", "가장 먼저 오는 이름");

    mockMvc
        .perform(authorized(post("/api/source-refs/{id}/verify", UUID.randomUUID()), caller.id()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SOURCE_REF_NOT_FOUND"));
  }

  @Test
  @DisplayName("provider 콜백은 토큰 없이 호출할 수 있으며 승인 코드가 없으면 거부한다")
  void providerCallbackIsReachableWithoutTokenAndRejectsRequestWithoutCode() throws Exception {
    mockMvc
        .perform(get("/api/source-connections/oauth/callback").param("state", "some-state"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SOURCE_OAUTH_INVALID_REQUEST"));
  }

  @Test
  @DisplayName("provider 콜백은 검증할 수 없는 state를 거부한다")
  void providerCallbackRejectsStateThatDoesNotVerify() throws Exception {
    mockMvc
        .perform(
            get("/api/source-connections/oauth/callback")
                .param("code", "code-1")
                .param("state", "not-a-state"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SOURCE_OAUTH_INVALID_STATE"));
  }

  private MockHttpServletRequestBuilder authorized(
      MockHttpServletRequestBuilder builder, UUID userId) {
    return builder
        .header("Authorization", "Bearer " + accessTokens.issueAccessToken(userId))
        .header("API-Version", "1");
  }

  private UserProfile user(String email, String name) {
    return userService.findOrCreate(email, name, null);
  }

  private UUID insertWorkspace(String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO workspaces (id, name, slug) VALUES (?, ?, ?)", id, "모먼스", slug);
    return id;
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    jdbcTemplate.update(
        "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?, ?, ?)",
        workspaceId,
        userId,
        role);
  }

  private void insertConnection(
      UUID workspaceId, String sourceType, String externalWorkspaceId, String createdAt) {
    jdbcTemplate.update(
        "INSERT INTO source_connections (id, workspace_id, source_type, status,"
            + " external_workspace_id, created_at, updated_at)"
            + " VALUES (?, ?, ?, 'ACTIVE', ?, ?::timestamptz, ?::timestamptz)",
        UUID.randomUUID(),
        workspaceId,
        sourceType,
        externalWorkspaceId,
        createdAt,
        createdAt);
  }

  private UUID insertSourceRef(UUID workspaceId) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO source_refs (id, workspace_id, source_type, source_object_type,"
            + " source_object_id, title, snippet, text, visibility)"
            + " VALUES (?, ?, 'figma', 'FILE_COMMENT', 'obj-1', ?, ?, ?, 'WORKSPACE')",
        id,
        workspaceId,
        "권한 요청 화면 v2",
        "설명 문구 변경",
        "수집한 원문 전체");
    return id;
  }
}
