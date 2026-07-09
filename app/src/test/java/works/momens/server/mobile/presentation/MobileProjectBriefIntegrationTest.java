package works.momens.server.mobile.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * {@code GET /api/mobile/projects/{projectId}/brief} 실배선 통합 테스트.
 *
 * <p>실토큰(auth public testFixtures)과 실제 PostgreSQL로 보안 체인부터 권한 검사, 프로젝트 스냅샷 응답 shape까지 끝까지 확인합니다.
 * 사용자는 user public API로 만들고, workspace/멤버십/project는 아직 생성 public API가 없어 소유 스키마에 SQL로 시드합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MobileProjectBriefIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void returnsProjectSnapshotForMember() throws Exception {
    UserProfile jinsu = userService.findOrCreate("brief-it-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("brief-snapshot");
    addMember(workspace, jinsu.id(), "owner");
    UUID project =
        insertProject(
            workspace,
            jinsu.id(),
            "Q2 Activation Readiness",
            LocalDate.of(2026, 6, 30),
            64,
            "목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다.");

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.id").value(project.toString()))
        .andExpect(jsonPath("$.project.name").value("Q2 Activation Readiness"))
        .andExpect(jsonPath("$.project.target_date").value("2026-06-30"))
        .andExpect(jsonPath("$.project.progress").value(64))
        .andExpect(
            jsonPath("$.project.summary")
                .value("목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다."));
  }

  @Test
  void returnsNullableSnapshotFieldsAsNull() throws Exception {
    // target_date와 summary는 스키마상 nullable이라 값이 없으면 null로 항상 포함된다(명세 예시와 동일한 키 구성).
    UserProfile gyuil = userService.findOrCreate("brief-it-gyuil@momens.works", "김규일", null);
    UUID workspace = insertWorkspace("brief-nullable");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "빈 스냅샷 프로젝트", null, 0, null);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(gyuil.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.name").value("빈 스냅샷 프로젝트"))
        .andExpect(jsonPath("$.project.target_date", nullValue()))
        .andExpect(jsonPath("$.project.progress").value(0))
        .andExpect(jsonPath("$.project.summary", nullValue()));
  }

  @Test
  void returnsForbiddenWhenCallerIsNotWorkspaceMember() throws Exception {
    UserProfile gyuil = userService.findOrCreate("brief-it-owner-gyuil@momens.works", "김규일", null);
    UserProfile jinsu =
        userService.findOrCreate("brief-it-stranger-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("brief-forbidden");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "brief-forbidden-project", null, 0, null);

    // 규일만 멤버인 workspace의 project를 진수 토큰으로 조회한다.
    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void returnsNotFoundForUnknownProject() throws Exception {
    UserProfile caller = userService.findOrCreate("brief-it-404@momens.works", "신진수", null);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(caller.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
  }

  @Test
  void returnsStandardUnauthorizedWithoutToken() throws Exception {
    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", UUID.randomUUID())
                .header("API-Version", "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
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

  private UUID insertProject(
      UUID workspaceId,
      UUID ownerId,
      String name,
      LocalDate targetDate,
      int progress,
      String summary) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO projects (id, workspace_id, name, owner_id, target_date, progress, summary)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        name,
        ownerId,
        targetDate,
        progress,
        summary);
    return id;
  }
}
