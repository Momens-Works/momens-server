package works.momens.server.mobile.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

  private static final String DEMO_SIGNAL_TITLE = "할인 쿠폰 적용 실패 문의가 오늘 27건 접수됐습니다";

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
  @DisplayName("멤버에게 근거의 대상·변화·영향이 담긴 Signal 상세를 반환한다")
  void returnsSignalDetailWithEvidenceDetailsForMember() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-detail@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-detail");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "Q2 Activation Readiness");
    UUID signal = insertSignal(workspace, project, "risk", "이탈 가능성 발견", "완료율에 영향", "점검 제안");
    // source_ref는 source·occurred_at·source_url을, signal_evidence는 대상·변화·영향을 채운다.
    UUID sourceRef = insertSourceRef(workspace, "figma", "권한 화면", null, "본문 요약", "https://f/1");
    insertEvidence(workspace, signal, sourceRef, 0, "권한 요청 화면", "이탈률 증가", "완료율 저하 가능");

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signal.toString()))
        .andExpect(jsonPath("$.type").value("risk"))
        .andExpect(jsonPath("$.impact").value("완료율에 영향"))
        .andExpect(jsonPath("$.evidence.length()").value(1))
        .andExpect(jsonPath("$.evidence[0].source_ref_id").value(sourceRef.toString()))
        .andExpect(jsonPath("$.evidence[0].source").value("figma"))
        .andExpect(jsonPath("$.evidence[0].details.target").value("권한 요청 화면"))
        .andExpect(jsonPath("$.evidence[0].details.change").value("이탈률 증가"))
        .andExpect(jsonPath("$.evidence[0].details.impact").value("완료율 저하 가능"))
        .andExpect(jsonPath("$.evidence[0].source_url").value("https://f/1"))
        .andExpect(jsonPath("$.minsu_suggestion").value("점검 제안"))
        // ADR-0011로 상세 응답에서 제외된 필드.
        .andExpect(jsonPath("$.project").doesNotExist())
        .andExpect(jsonPath("$.evidence[0].source_title").doesNotExist())
        .andExpect(jsonPath("$.evidence[0].summary").doesNotExist())
        .andExpect(jsonPath("$.minsu").doesNotExist())
        .andExpect(jsonPath("$.primary_action").doesNotExist());
  }

  @Test
  @DisplayName("이미 처리된 Signal의 상세를 조회하면 SIGNAL_NOT_FOUND를 반환한다")
  void returnsNotFoundOnDetailForProcessedSignal() throws Exception {
    UserProfile jinsu =
        userService.findOrCreate("signals-it-detail-processed@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-detail-processed");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-detail-processed-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목", null, null);
    insertAction(workspace, signal, jinsu.id());

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SIGNAL_NOT_FOUND"));
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

  @Test
  @DisplayName("convert-to-task는 태스크를 생성하고 재요청은 멱등 replay로 200을 반환한다")
  void convertToTaskCreatesTaskAndReplaysOnRetry() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-convert@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-convert");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-convert-project");
    UUID signal = insertSignal(workspace, project, "risk", "이탈 가능성 발견", "완료율에 영향", null);
    String token = "Bearer " + accessTokens.issueAccessToken(jinsu.id());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.task.title").value("이탈 가능성 발견"))
        .andExpect(jsonPath("$.task.status").value("todo"))
        .andExpect(jsonPath("$.signal.id").value(signal.toString()))
        .andExpect(jsonPath("$.signal.action").value("convert_to_task"));

    Integer taskCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tasks WHERE project_id = ?", Integer.class, project);
    assertThat(taskCount).isEqualTo(1);
    String taskId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tasks WHERE project_id = ?", String.class, project);

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.task.id").value(taskId));

    Integer taskCountAfterRetry =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tasks WHERE project_id = ?", Integer.class, project);
    assertThat(taskCountAfterRetry).isEqualTo(1);
  }

  @Test
  @DisplayName("test 프로필의 정확한 쿠폰 실패 제목은 상세와 관련자료가 채워진 시연용 task를 생성한다")
  void exactDemoTitleCreatesRichTaskAndReplaysWithoutDuplicates() throws Exception {
    UserProfile user =
        userService.findOrCreate("signals-it-demo-convert@momens.works", "김규일", null);
    UUID workspace = insertWorkspace("signals-demo-convert");
    addMember(workspace, user.id(), "owner");
    UUID project = insertProject(workspace, user.id(), "signals-demo-convert-project");
    UUID signal = insertSignal(workspace, project, "change", DEMO_SIGNAL_TITLE, "전환율 저하", "사전 안내");
    UUID slack =
        insertSourceRef(
            workspace, "slack", "고객 문의 채널", "쿠폰 문의 27건", "문의 본문", "https://momens.slack.com/demo");
    UUID figma =
        insertSourceRef(
            workspace,
            "figma",
            "여름 세일 이벤트 배너",
            "쿠폰 조건 안내 누락",
            "피그마 본문",
            "https://www.figma.com/file/demo");
    UUID file =
        insertSourceRef(
            workspace,
            "file",
            "쿠폰 입력 구간 분석",
            "이탈률 11.3% 증가",
            "분석 본문",
            "https://drive.google.com/file/d/demo");
    insertEvidence(workspace, signal, slack, 0, "고객 문의", "27건 접수", "반복 문의");
    insertEvidence(workspace, signal, figma, 1, "이벤트 배너", "조건 누락", "적용 불가 인지");
    insertEvidence(workspace, signal, file, 2, "쿠폰 입력", "이탈 증가", "전환 저하");
    String token = "Bearer " + accessTokens.issueAccessToken(user.id());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.task.title").value("쿠폰 실패 안내 개선"))
        .andExpect(jsonPath("$.task.status").value("todo"));

    UUID taskId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tasks WHERE origin_signal_id = ?",
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            signal);

    mockMvc
        .perform(
            get("/api/mobile/tasks/{taskId}", taskId)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("쿠폰 실패 안내 개선"))
        .andExpect(jsonPath("$.purpose").value("고객이 결제 전에 쿠폰 적용 가능 여부와 실패 이유를 이해할 수 있게 한다."))
        .andExpect(jsonPath("$.status").value("todo"))
        .andExpect(jsonPath("$.priority").value("high"))
        .andExpect(jsonPath("$.role").value("design"))
        .andExpect(jsonPath("$.assignee.id").value(user.id().toString()))
        .andExpect(jsonPath("$.checklist.total_count").value(4))
        .andExpect(jsonPath("$.checklist.items[0].title").value("쿠폰 제외 브랜드와 최소 주문 금액 확정"))
        .andExpect(jsonPath("$.checklist.items[1].title").value("결제 전에 쿠폰 적용 가능 여부 표시"))
        .andExpect(jsonPath("$.checklist.items[2].title").value("실패 원인별 안내 문구 적용"))
        .andExpect(jsonPath("$.checklist.items[3].title").value("고객센터 응대 가이드 공유"))
        .andExpect(jsonPath("$.open_questions.length()").value(2))
        .andExpect(jsonPath("$.open_questions[0].body").value("쿠폰 제외 브랜드를 상품 상세에서도 안내할까요?"))
        .andExpect(jsonPath("$.open_questions[1].body").value("쿠폰 실패 시 사용 가능한 다른 쿠폰을 추천할까요?"))
        .andExpect(
            jsonPath("$.next_action")
                .value("고객 문의 27건을 실패 원인별로 분류하고, 가장 많은 두 원인의 안내 문구와 사전 노출 위치를 먼저 확정하세요."))
        .andExpect(jsonPath("$.materials.length()").value(3))
        .andExpect(jsonPath("$.materials[0].id").value(slack.toString()))
        .andExpect(jsonPath("$.materials[0].source").value("slack"))
        .andExpect(jsonPath("$.materials[1].id").value(figma.toString()))
        .andExpect(jsonPath("$.materials[1].source").value("figma"))
        .andExpect(jsonPath("$.materials[2].id").value(file.toString()))
        .andExpect(jsonPath("$.materials[2].source").value("file"));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.task.id").value(taskId.toString()));

    assertThat(count("tasks", "origin_signal_id", signal)).isEqualTo(1);
    assertThat(count("task_checklist_items", "task_id", taskId)).isEqualTo(4);
    assertThat(count("task_open_questions", "task_id", taskId)).isEqualTo(2);
    assertThat(count("entity_relations", "from_entity_id", taskId)).isEqualTo(3);
    assertThat(count("signal_actions", "signal_id", signal)).isEqualTo(1);
    assertThat(countOutbox("task.created", taskId)).isEqualTo(1);
    assertThat(countOutbox("signal.converted_to_task", signal)).isEqualTo(1);
  }

  @Test
  @DisplayName("test 프로필에서도 마침표가 붙은 유사 제목은 기존 기본 task를 생성한다")
  void similarDemoTitleCreatesDefaultTask() throws Exception {
    UserProfile user =
        userService.findOrCreate("signals-it-demo-similar@momens.works", "김규일", null);
    UUID workspace = insertWorkspace("signals-demo-similar");
    addMember(workspace, user.id(), "owner");
    UUID project = insertProject(workspace, user.id(), "signals-demo-similar-project");
    String similarTitle = DEMO_SIGNAL_TITLE + ".";
    UUID signal = insertSignal(workspace, project, "change", similarTitle, "전환율 저하", "사전 안내");

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(user.id()))
                .header("API-Version", "1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.task.title").value(similarTitle));

    Map<String, Object> task =
        jdbcTemplate.queryForMap(
            "SELECT id, role, priority, description, assignee_id, next_action "
                + "FROM tasks WHERE origin_signal_id = ?",
            signal);
    UUID taskId = (UUID) task.get("id");
    assertThat(task)
        .containsEntry("role", "pm")
        .containsEntry("priority", "medium")
        .containsEntry("description", null)
        .containsEntry("assignee_id", null)
        .containsEntry("next_action", null);
    assertThat(count("task_checklist_items", "task_id", taskId)).isZero();
    assertThat(count("task_open_questions", "task_id", taskId)).isZero();
    assertThat(count("entity_relations", "from_entity_id", taskId)).isZero();
  }

  @Test
  @DisplayName("dismiss는 처리 기록만 남기고, 재요청은 멱등 replay로 200을 반환하며 목록에서 제외한다")
  void dismissRecordsActionAndExcludesFromList() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-dismiss@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-dismiss");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-dismiss-project");
    UUID signal = insertSignal(workspace, project, "decision", "제목", null, null);
    String token = "Bearer " + accessTokens.issueAccessToken(jinsu.id());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signal.id").value(signal.toString()))
        .andExpect(jsonPath("$.signal.action").value("dismiss"));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signal.action").value("dismiss"));

    Integer actionCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM signal_actions WHERE signal_id = ?", Integer.class, signal);
    assertThat(actionCount).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", project)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signals.length()").value(0));
  }

  @Test
  @DisplayName("이미 dismiss된 Signal에 convert-to-task를 요청하면 SIGNAL_INVALID_STATE를 반환한다")
  void convertToTaskAfterDismissReturnsInvalidState() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-conflict-1@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-conflict-1");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-conflict-1-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목", null, null);
    String token = "Bearer " + accessTokens.issueAccessToken(jinsu.id());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SIGNAL_INVALID_STATE"));
  }

  @Test
  @DisplayName("이미 전환된 Signal에 dismiss를 요청하면 SIGNAL_INVALID_STATE를 반환한다")
  void dismissAfterConvertToTaskReturnsInvalidState() throws Exception {
    UserProfile jinsu = userService.findOrCreate("signals-it-conflict-2@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-conflict-2");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "signals-conflict-2-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목", null, null);
    String token = "Bearer " + accessTokens.issueAccessToken(jinsu.id());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signal)
                .header("Authorization", token)
                .header("API-Version", "1"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("SIGNAL_INVALID_STATE"));
  }

  @Test
  @DisplayName("action 요청에서 workspace 멤버가 아니면 AUTH_FORBIDDEN을 반환한다")
  void actionsReturnForbiddenWhenNotMember() throws Exception {
    UserProfile gyuil =
        userService.findOrCreate("signals-it-action-owner@momens.works", "김규일", null);
    UserProfile jinsu =
        userService.findOrCreate("signals-it-action-stranger@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("signals-action-forbidden");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "signals-action-forbidden-project");
    UUID signal = insertSignal(workspace, project, "risk", "제목", null, null);

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signal)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  @DisplayName("없는 Signal에 action을 요청하면 SIGNAL_NOT_FOUND를 반환한다")
  void actionsReturnNotFoundForUnknownSignal() throws Exception {
    UserProfile caller =
        userService.findOrCreate("signals-it-action-404@momens.works", "신진수", null);

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(caller.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("SIGNAL_NOT_FOUND"));
  }

  @Test
  @DisplayName("토큰 없이 action을 요청하면 AUTH_UNAUTHORIZED를 반환한다")
  void actionsReturnUnauthorizedWithoutToken() throws Exception {
    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", UUID.randomUUID())
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

  private void insertEvidence(
      UUID workspaceId,
      UUID signalId,
      UUID sourceRefId,
      int sortOrder,
      String target,
      String change,
      String impact) {
    jdbcTemplate.update(
        "INSERT INTO signal_evidence (workspace_id, signal_id, source_ref_id, sort_order, target,"
            + " \"change\", impact) VALUES (?, ?, ?, ?, ?, ?, ?)",
        workspaceId,
        signalId,
        sourceRefId,
        sortOrder,
        target,
        change,
        impact);
  }

  private Integer count(String table, String column, UUID id) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id);
  }

  private Integer countOutbox(String eventType, UUID aggregateId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM outbox_events WHERE event_type = ? AND aggregate_id = ?",
        Integer.class,
        eventType,
        aggregateId.toString());
  }
}
