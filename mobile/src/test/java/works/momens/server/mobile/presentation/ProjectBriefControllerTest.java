package works.momens.server.mobile.presentation;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import works.momens.server.mobile.internal.ProjectBriefService;
import works.momens.server.project.ProjectSnapshot;

/**
 * 컨트롤러가 경로 변수와 Principal을 조합 서비스에 그대로 전달하고 명세(docs/spec/mobile-api.md)의 snake_case 응답 shape를 내는지
 * 검증합니다. versioning은 모듈 경계상 app 설정을 못 가져오므로 슬라이스 안에서 동일하게 구성합니다. 인증 실패(401)와 에러 응답 shape는 app의 통합
 * 테스트가 봅니다.
 */
@WebMvcTest(ProjectBriefController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ProjectBriefControllerTest.ApiVersioningTestConfig.class)
class ProjectBriefControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private ProjectBriefService projectBriefService;

  private static final UUID USER_ID = UUID.fromString("5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8");
  private static final UUID PROJECT_ID = UUID.fromString("30d9e9fe-f43b-4097-a88e-dc19f0a5b025");
  private static final UUID WORKSPACE_ID = UUID.fromString("9be5a41c-2c63-4f7e-9d15-3b7f8c2a1d40");
  private final Principal principal = USER_ID::toString;

  @Test
  void getBriefReturnsSnakeCaseProjectBlock() throws Exception {
    when(projectBriefService.getBrief(eq(PROJECT_ID), eq(USER_ID)))
        .thenReturn(
            new ProjectSnapshot(
                PROJECT_ID,
                WORKSPACE_ID,
                "Q2 Activation Readiness",
                LocalDate.of(2026, 6, 30),
                64,
                "목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다."));

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/brief", PROJECT_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.project.id").value(PROJECT_ID.toString()))
        .andExpect(jsonPath("$.project.name").value("Q2 Activation Readiness"))
        .andExpect(jsonPath("$.project.target_date").value("2026-06-30"))
        .andExpect(jsonPath("$.project.progress").value(64))
        .andExpect(
            jsonPath("$.project.summary")
                .value("목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다."));
  }

  @TestConfiguration
  static class ApiVersioningTestConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.useRequestHeader("API-Version").addSupportedVersions("1").setDefaultVersion("1");
    }
  }
}
