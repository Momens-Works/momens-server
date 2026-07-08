package works.momens.server.signal.presentation;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalActionService;
import works.momens.server.signal.SignalDetail;
import works.momens.server.signal.SignalDetailService;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.SignalSummary;

/**
 * 컨트롤러가 경로 변수와 Principal을 서비스에 그대로 전달하고 명세(docs/spec/mobile-api.md)의 고정 envelope·snake_case 응답
 * shape를 내는지 검증합니다. versioning은 모듈 경계상 app 설정을 못 가져오므로 슬라이스 안에서 동일하게 구성합니다. 인증 실패(401)와 에러 응답
 * shape는 app의 통합 테스트가 봅니다.
 */
@WebMvcTest(SignalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SignalControllerTest.ApiVersioningTestConfig.class)
class SignalControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SignalListService signalListService;
  @MockitoBean private SignalDetailService signalDetailService;
  @MockitoBean private SignalActionService signalActionService;

  private static final UUID USER_ID = UUID.fromString("5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8");
  private static final UUID PROJECT_ID = UUID.fromString("30d9e9fe-f43b-4097-a88e-dc19f0a5b025");
  private final Principal principal = USER_ID::toString;

  @Test
  @DisplayName("Signal 목록 고정 envelope와 snake_case 항목을 반환한다")
  void listSignalsReturnsFixedEnvelopeWithSnakeCaseSignals() throws Exception {
    UUID signalId = UUID.randomUUID();
    when(signalListService.listUnprocessed(eq(PROJECT_ID), eq(USER_ID)))
        .thenReturn(
            List.of(
                new SignalSummary(
                    signalId, PROJECT_ID, "risk", "이탈 가능성 발견", "완료율에 영향을 줄 수 있습니다.", "점검 제안")));

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", PROJECT_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("오늘 확인해야 할 시그널"))
        .andExpect(jsonPath("$.description").value("프로젝트의 의사결정에 영향을 줄 수 있는 변화입니다."))
        .andExpect(jsonPath("$.signals.length()").value(1))
        .andExpect(jsonPath("$.signals[0].id").value(signalId.toString()))
        .andExpect(jsonPath("$.signals[0].project_id").value(PROJECT_ID.toString()))
        .andExpect(jsonPath("$.signals[0].type").value("risk"))
        .andExpect(jsonPath("$.signals[0].title").value("이탈 가능성 발견"))
        .andExpect(jsonPath("$.signals[0].impact").value("완료율에 영향을 줄 수 있습니다."))
        .andExpect(jsonPath("$.signals[0].minsu_suggestion").value("점검 제안"));
  }

  @Test
  @DisplayName("impact와 minsu_suggestion이 null이어도 응답에 포함한다")
  void listSignalsIncludesNullImpactAndMinsuSuggestion() throws Exception {
    UUID signalId = UUID.randomUUID();
    when(signalListService.listUnprocessed(eq(PROJECT_ID), eq(USER_ID)))
        .thenReturn(List.of(new SignalSummary(signalId, PROJECT_ID, "decision", "제목", null, null)));

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", PROJECT_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signals[0].impact", nullValue()))
        .andExpect(jsonPath("$.signals[0].minsu_suggestion", nullValue()));
  }

  @Test
  @DisplayName("미처리 Signal이 없으면 빈 배열을 반환한다")
  void listSignalsReturnsEmptyArrayWhenNoneUnprocessed() throws Exception {
    when(signalListService.listUnprocessed(eq(PROJECT_ID), eq(USER_ID))).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/signals", PROJECT_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signals").isArray())
        .andExpect(jsonPath("$.signals.length()").value(0));
  }

  @Test
  @DisplayName("Signal 상세 중첩 응답을 반환한다")
  void getSignalReturnsNestedDetail() throws Exception {
    UUID signalId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID();
    when(signalDetailService.getDetail(eq(signalId), eq(USER_ID)))
        .thenReturn(
            new SignalDetail(
                signalId,
                PROJECT_ID,
                "Q2 Activation Readiness",
                "risk",
                "이탈 가능성",
                "본문",
                "완료율에 영향",
                "점검 제안",
                List.of(
                    new SignalDetail.Evidence(
                        evidenceId,
                        "figma",
                        "권한 화면",
                        Instant.parse("2026-07-06T00:00:00Z"),
                        "요약",
                        "https://f/1"))));

    mockMvc
        .perform(
            get("/api/mobile/signals/{signalId}", signalId)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(signalId.toString()))
        .andExpect(jsonPath("$.project.id").value(PROJECT_ID.toString()))
        .andExpect(jsonPath("$.project.name").value("Q2 Activation Readiness"))
        .andExpect(jsonPath("$.description").value("본문"))
        .andExpect(jsonPath("$.evidence[0].id").value(evidenceId.toString()))
        .andExpect(jsonPath("$.evidence[0].source").value("figma"))
        .andExpect(jsonPath("$.evidence[0].source_title").value("권한 화면"))
        .andExpect(jsonPath("$.evidence[0].relative_time_label").doesNotExist())
        .andExpect(jsonPath("$.evidence[0].summary").value("요약"))
        // fields는 MVP에서 항상 빈 배열이다(D3).
        .andExpect(jsonPath("$.evidence[0].fields.length()").value(0))
        .andExpect(jsonPath("$.evidence[0].source_url").value("https://f/1"))
        .andExpect(jsonPath("$.minsu.suggestion").value("점검 제안"))
        // task_draft는 Signal 제목 기반 최소 초안(roles 빈 배열, priority medium).
        .andExpect(jsonPath("$.minsu.task_draft.title").value("이탈 가능성"))
        .andExpect(jsonPath("$.minsu.task_draft.roles.length()").value(0))
        .andExpect(jsonPath("$.minsu.task_draft.priority").value("medium"))
        .andExpect(jsonPath("$.actions").value(contains("convert-to-task", "dismiss")))
        .andExpect(jsonPath("$.primary_action").value("convert-to-task"));
  }

  @Test
  @DisplayName("convert-to-task 신규 처리는 201과 task를 반환한다")
  void convertToTaskReturnsCreatedWhenNewlyProcessed() throws Exception {
    UUID signalId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    when(signalActionService.convertToTask(eq(signalId), eq(USER_ID), any()))
        .thenReturn(
            new SignalActionResult(
                signalId,
                "convert_to_task",
                true,
                new SignalActionResult.TaskResult(taskId, "제목", "todo")));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signalId)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"backend\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.task.id").value(taskId.toString()))
        .andExpect(jsonPath("$.task.title").value("제목"))
        .andExpect(jsonPath("$.task.status").value("todo"))
        .andExpect(jsonPath("$.signal.id").value(signalId.toString()))
        .andExpect(jsonPath("$.signal.action").value("convert_to_task"));
  }

  @Test
  @DisplayName("convert-to-task 재요청(멱등 replay)은 200을 반환한다")
  void convertToTaskReturnsOkWhenReplayed() throws Exception {
    UUID signalId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    when(signalActionService.convertToTask(eq(signalId), eq(USER_ID), any()))
        .thenReturn(
            new SignalActionResult(
                signalId,
                "convert_to_task",
                false,
                new SignalActionResult.TaskResult(taskId, "제목", "todo")));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signalId)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.task.id").value(taskId.toString()));
  }

  @Test
  @DisplayName("convert-to-task body 생략은 command에 전부 null을 전달한다")
  void convertToTaskWithoutBodyPassesAllNullCommand() throws Exception {
    UUID signalId = UUID.randomUUID();
    when(signalActionService.convertToTask(
            eq(signalId),
            eq(USER_ID),
            eq(new SignalActionService.ConvertToTaskCommand(null, null, null))))
        .thenReturn(
            new SignalActionResult(
                signalId,
                "convert_to_task",
                true,
                new SignalActionResult.TaskResult(UUID.randomUUID(), "제목", "todo")));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signalId)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("잘못된 role 패턴은 COMMON_VALIDATION_FAILED로 400을 반환한다")
  void convertToTaskWithInvalidRolePatternReturns400() throws Exception {
    UUID signalId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signalId)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"android\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("공백 title override는 400을 반환한다")
  void convertToTaskWithBlankTitleReturns400() throws Exception {
    UUID signalId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/convert-to-task", signalId)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"role\":\"backend\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("dismiss는 신규·재요청 모두 200과 signal envelope를 반환한다")
  void dismissReturnsOkWithSignalEnvelope() throws Exception {
    UUID signalId = UUID.randomUUID();
    when(signalActionService.dismiss(eq(signalId), eq(USER_ID)))
        .thenReturn(new SignalActionResult(signalId, "dismiss", true, null));

    mockMvc
        .perform(
            post("/api/mobile/signals/{signalId}/actions/dismiss", signalId)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signal.id").value(signalId.toString()))
        .andExpect(jsonPath("$.signal.action").value("dismiss"));
  }

  @TestConfiguration
  static class ApiVersioningTestConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.useRequestHeader("API-Version").addSupportedVersions("1").setDefaultVersion("1");
    }
  }
}
