package works.momens.server.mobile.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * {@code GET /api/mobile/projects/{projectId}/signals}(목록)와 {@code GET
 * /api/mobile/signals/{signalId}} (상세) 실배선 통합 테스트.
 *
 * <p>실토큰(auth public testFixtures)과 실제 PostgreSQL로 보안 체인부터 권한 검사, 미처리 필터, evidence hydrate, 응답
 * shape까지 끝까지 확인합니다. 사용자는 user public API로 만들고, workspace/멤버십/project/signals/signal_actions/
 * signal_evidence/source_refs는 아직 생성 public API가 없어 소유 스키마에 SQL로 시드합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MobileSignalsIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("workspace 멤버에게 미처리 Signal 목록을 반환한다")
  void returnsUnprocessedSignalsForWorkspaceMember() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-list");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-list-project");
    UUID unprocessed = insertSignal(workspace, project, "risk", "이탈 가능성 발견", "완료율에 영향", "점검 제안");
    UUID processed = insertSignal(workspace, project, "decision", "이미 처리됨", null, null);
    insertAction(workspace, processed, jinsu.id());

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("오늘 확인해야 할 시그널"))
        .andExpect(jsonPath("$.signals.length()").value(1))
        .andExpect(jsonPath("$.signals[0].id").value(unprocessed.toString()))
        .andExpect(jsonPath("$.signals[0].type").value("risk"))
        .andExpect(jsonPath("$.signals[0].impact").value("완료율에 영향"))
        .andExpect(jsonPath("$.signals[0].minsu_suggestion").value("점검 제안"));
  }

  @Test
  @DisplayName("workspace 멤버가 아니면 AUTH_FORBIDDEN을 반환한다")
  void returnsForbiddenWhenCallerIsNotWorkspaceMember() throws Exception {
    UserProfile gyuil =
        userService.findOrCreate("signals-it-owner-gyuil@momens.works", "김규일", null);
    UserProfile jinsu =
        userService.findOrCreate("signals-it-stranger-jinsu@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-forbidden");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "signals-forbidden-project");

    // 규일만 멤버인 workspace의 project를 진수 토큰으로 조회한다.
    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("없는 project를 조회하면 PROJECT_NOT_FOUND를 반환한다")
  void returnsNotFoundForUnknownProject() throws Exception {
    UserProfile caller = userService.findOrCreate("signals-it-404@momens.works", "신진수", null);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(caller.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
  }

  @Test
  @DisplayName("토큰 없이 조회하면 AUTH_UNAUTHORIZED를 반환한다")
  void returnsStandardUnauthorizedWithoutToken() throws Exception {
    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", UUID.randomUUID())
                .header("API-Version", "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
  }

  @Test
  @DisplayName("멤버에게 hydrate된 근거가 포함된 Signal 상세를 반환한다")
  void returnsSignalDetailWithHydratedEvidenceForMember() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-detail@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-detail");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "Q2 Activation Readiness");
    UUID signal = insertSignal(workspace, project, "risk", "이탈 가능성 발견", "완료율에 영향", "점검 제안");
    // snippet 없이 text만 있어 summary는 text로 폴백된다.
    UUID sourceRef = insertSourceRef(workspace, "figma", "권한 화면", null, "본문 요약", "https://f/1");
    insertEvidence(workspace, signal, sourceRef, 0);

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signal.toString()))
        .andExpect(jsonPath("$.project.name").value("Q2 Activation Readiness"))
        .andExpect(jsonPath("$.evidence.length()").value(1))
        .andExpect(jsonPath("$.evidence[0].id").value(sourceRef.toString()))
        .andExpect(jsonPath("$.evidence[0].source").value("figma"))
        .andExpect(jsonPath("$.evidence[0].source_title").value("권한 화면"))
        .andExpect(jsonPath("$.evidence[0].summary").value("본문 요약"))
        .andExpect(jsonPath("$.evidence[0].fields.length()").value(0))
        .andExpect(jsonPath("$.evidence[0].source_url").value("https://f/1"))
        .andExpect(jsonPath("$.minsu.suggestion").value("점검 제안"))
        .andExpect(jsonPath("$.minsu.task_draft.title").value("이탈 가능성 발견"))
        .andExpect(jsonPath("$.minsu.task_draft.priority").value("medium"))
        .andExpect(jsonPath("$.primary_action").value("convert-to-task"));
  }

  @Test
  @DisplayName("상세 조회에서 workspace 멤버가 아니면 AUTH_FORBIDDEN을 반환한다")
  void returnsForbiddenOnDetailWhenNotMember() throws Exception {
    UserProfile gyuil =
        userService.findOrCreate("signals-it-detail-owner@momens.works", "김규일", null);
    UserProfile jinsu =
        userService.findOrCreate("signals-it-detail-stranger@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-detail-forbidden");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "detail-forbidden-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목", null, null);

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("없는 Signal을 조회하면 SIGNAL_NOT_FOUND를 반환한다")
  void returnsNotFoundForUnknownSignal() throws Exception {
    UserProfile caller =
        userService.findOrCreate("signals-it-detail-404@momens.works", "신진수", null);

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(caller.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SIGNAL_NOT_FOUND"));
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

  private UUID insertSignal(
      UUID workspaceId,
      UUID projectId,
      String type,
      String title,
      String impact,
      String minsuSuggestion) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO signals (id, workspace_id, project_id, type, title, description, impact,"
            + " minsu_suggestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        projectId,
        type,
        title,
        "본문",
        impact,
        minsuSuggestion);
    return id;
  }

  private void insertAction(UUID workspaceId, UUID signalId, UUID processedByUserId) {
    jdbcTemplate.update(
        "INSERT INTO signal_actions (id, workspace_id, signal_id, action_type,"
            + " processed_by_user_id) VALUES (?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        workspaceId,
        signalId,
        "dismiss",
        processedByUserId);
  }

  private UUID insertSourceRef(
      UUID workspaceId, String sourceType, String title, String snippet, String text, String url) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO source_refs (id, workspace_id, source_type, title, snippet, text, source_url)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        sourceType,
        title,
        snippet,
        text,
        url);
    return id;
  }

  private void insertEvidence(UUID workspaceId, UUID signalId, UUID sourceRefId, int sortOrder) {
    jdbcTemplate.update(
        "INSERT INTO signal_evidence (workspace_id, signal_id, source_ref_id, sort_order)"
            + " VALUES (?, ?, ?, ?)",
        workspaceId,
        signalId,
        sourceRefId,
        sortOrder);
  }
}
