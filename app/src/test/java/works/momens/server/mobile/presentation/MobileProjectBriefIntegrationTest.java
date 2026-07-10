package works.momens.server.mobile.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
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
                .value("목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다."))
        // 시그널이 없으면 개수는 0, 목록은 빈 배열, 다음 커서와 요약 문단은 null로 항상 포함된다.
        .andExpect(jsonPath("$.signal_summary.summary", nullValue()))
        .andExpect(jsonPath("$.signal_summary.filters[0].key").value("all"))
        .andExpect(jsonPath("$.signal_summary.filters[0].count").value(0))
        .andExpect(jsonPath("$.signal_summary.items.length()").value(0))
        .andExpect(jsonPath("$.signal_summary.next_cursor", nullValue()));
  }

  @Test
  void returnsSignalSummaryCountsAndFirstPageExcludingChangeAndProcessed() throws Exception {
    UserProfile jinsu = userService.findOrCreate("brief-it-signal-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("brief-signal");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "brief-signal-project", null, 0, null);
    insertSignal(workspace, project, "decision", "소셜 로그인은 MVP 범위에서 제외", "2026-07-01T00:00:00Z");
    insertSignal(workspace, project, "decision", "회원가입 MVP 범위 1차 확정", "2026-07-02T00:00:00Z");
    insertSignal(
        workspace, project, "risk", "Android 13+ 권한 요청 플로우 이탈 가능성", "2026-07-03T00:00:00Z");
    insertSignal(workspace, project, "question", "온보딩 단계 수 확정 필요", "2026-07-04T00:00:00Z");
    UUID newest =
        insertSignal(workspace, project, "question", "권한 요청 문구 결정 필요", "2026-07-05T00:00:00Z");
    // change(VOC)와 처리된 signal은 개수와 목록 모두에서 빠져야 한다.
    insertSignal(workspace, project, "change", "VOC 유형", "2026-07-06T00:00:00Z");
    UUID processed = insertSignal(workspace, project, "risk", "이미 처리됨", "2026-07-07T00:00:00Z");
    insertAction(workspace, processed, jinsu.id());

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signal_summary.filters[0].key").value("all"))
        .andExpect(jsonPath("$.signal_summary.filters[0].count").value(5))
        .andExpect(jsonPath("$.signal_summary.filters[1].key").value("decisions"))
        .andExpect(jsonPath("$.signal_summary.filters[1].count").value(2))
        .andExpect(jsonPath("$.signal_summary.filters[2].key").value("risks"))
        .andExpect(jsonPath("$.signal_summary.filters[2].count").value(1))
        .andExpect(jsonPath("$.signal_summary.filters[3].key").value("questions"))
        .andExpect(jsonPath("$.signal_summary.filters[3].count").value(2))
        .andExpect(jsonPath("$.signal_summary.items.length()").value(3))
        .andExpect(jsonPath("$.signal_summary.items[0].id").value(newest.toString()))
        .andExpect(jsonPath("$.signal_summary.items[0].type").value("question"))
        .andExpect(jsonPath("$.signal_summary.items[0].title").value("권한 요청 문구 결정 필요"))
        .andExpect(jsonPath("$.signal_summary.items[1].title").value("온보딩 단계 수 확정 필요"))
        .andExpect(
            jsonPath("$.signal_summary.items[2].title").value("Android 13+ 권한 요청 플로우 이탈 가능성"))
        .andExpect(jsonPath("$.signal_summary.next_cursor").isNotEmpty());
  }

  @Test
  void paginatesSignalSummaryWithCursorAndFilter() throws Exception {
    UserProfile jinsu = userService.findOrCreate("brief-it-page-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("brief-page");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "brief-page-project", null, 0, null);
    UUID oldest = insertSignal(workspace, project, "decision", "가장 오래된 결정", "2026-07-01T00:00:00Z");
    insertSignal(workspace, project, "risk", "리스크", "2026-07-02T00:00:00Z");
    insertSignal(workspace, project, "question", "질문", "2026-07-03T00:00:00Z");
    insertSignal(workspace, project, "decision", "최신 결정", "2026-07-04T00:00:00Z");

    // 첫 페이지(기본 3개)의 next_cursor로 나머지 1개를 이어서 조회한다.
    String firstPageBody =
        mockMvc
            .perform(
                get("/api/mobile/projects/{projectId}/brief/signal-summary", project)
                    .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                    .header("API-Version", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String nextCursor = JsonPath.read(firstPageBody, "$.next_cursor");

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief/signal-summary", project)
                .param("cursor", nextCursor)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(oldest.toString()))
        .andExpect(jsonPath("$.next_cursor", nullValue()));

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief/signal-summary", project)
                .param("filter", "decisions")
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].title").value("최신 결정"))
        .andExpect(jsonPath("$.items[1].title").value("가장 오래된 결정"));
  }

  @Test
  void returnsValidationFailedForMalformedCursor() throws Exception {
    UserProfile jinsu = userService.findOrCreate("brief-it-cursor-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("brief-cursor");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "brief-cursor-project", null, 0, null);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief/signal-summary", project)
                .param("cursor", "not-a-cursor")
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
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

  private UUID insertSignal(
      UUID workspaceId, UUID projectId, String type, String title, String createdAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO signals (id, workspace_id, project_id, type, title, description, created_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        projectId,
        type,
        title,
        "본문",
        Timestamp.from(Instant.parse(createdAt)));
    return id;
  }

  private void insertAction(UUID workspaceId, UUID signalId, UUID userId) {
    jdbcTemplate.update(
        "INSERT INTO signal_actions (id, workspace_id, signal_id, action_type,"
            + " processed_by_user_id) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        workspaceId,
        signalId,
        "dismiss",
        userId);
  }
}
