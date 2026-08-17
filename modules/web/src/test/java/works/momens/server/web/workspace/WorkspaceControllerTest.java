package works.momens.server.web.workspace;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import works.momens.server.workspace.WorkspaceDetail;

/**
 * 컨트롤러가 Principal을 조합 서비스에 그대로 전달하고
 * 계약(docs/design/legacy-product-api-migration-workspace-read-design.md 4.3)의 snake_case 응답 shape를
 * 내는지 검증합니다. versioning은 모듈 경계상 app 설정을 못 가져오므로 슬라이스 안에서 동일하게 구성합니다. 인증 실패(401)와 에러 응답 shape는 app의
 * 통합 테스트가 봅니다.
 */
@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(WorkspaceControllerTest.ApiVersioningTestConfig.class)
class WorkspaceControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private WorkspaceService workspaceService;

  private static final UUID USER_ID = UUID.fromString("5d2f7f3a-5db1-4f2c-8b9e-13607dd1f5e8");
  private static final UUID WORKSPACE_ID = UUID.fromString("30d9e9fe-f43b-4097-a88e-dc19f0a5b025");
  private final Principal principal = USER_ID::toString;

  @Test
  @DisplayName("목록은 workspaces 래퍼와 snake_case 필드로 응답한다")
  void listReturnsWrappedWorkspacesInSnakeCase() throws Exception {
    WorkspaceDetail detail =
        new WorkspaceDetail(
            WORKSPACE_ID,
            "Momens",
            "momens",
            "제품팀 워크스페이스",
            Instant.parse("2026-06-27T09:00:00Z"),
            Instant.parse("2026-06-27T09:00:00Z"));
    when(workspaceService.list(USER_ID)).thenReturn(List.of(detail));

    mockMvc
        .perform(get("/api/workspaces").principal(principal).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces.length()").value(1))
        .andExpect(jsonPath("$.workspaces[0].id").value(WORKSPACE_ID.toString()))
        .andExpect(jsonPath("$.workspaces[0].name").value("Momens"))
        .andExpect(jsonPath("$.workspaces[0].slug").value("momens"))
        .andExpect(jsonPath("$.workspaces[0].description").value("제품팀 워크스페이스"))
        .andExpect(jsonPath("$.workspaces[0].created_at").value("2026-06-27T09:00:00Z"))
        .andExpect(jsonPath("$.workspaces[0].updated_at").value("2026-06-27T09:00:00Z"));
  }

  @Test
  @DisplayName("조회 결과가 없으면 null이 아니라 빈 배열로 응답한다")
  void listReturnsEmptyArrayWhenNoWorkspaces() throws Exception {
    when(workspaceService.list(USER_ID)).thenReturn(List.of());

    mockMvc
        .perform(get("/api/workspaces").principal(principal).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces").isArray())
        .andExpect(jsonPath("$.workspaces.length()").value(0));
  }

  @Test
  @DisplayName("description이 없으면 응답에서 필드를 생략한다")
  void listOmitsDescriptionWhenNull() throws Exception {
    WorkspaceDetail detail =
        new WorkspaceDetail(WORKSPACE_ID, "Momens", "momens", null, Instant.now(), Instant.now());
    when(workspaceService.list(USER_ID)).thenReturn(List.of(detail));

    mockMvc
        .perform(get("/api/workspaces").principal(principal).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces[0].description").doesNotExist());
  }

  @Test
  @DisplayName("단건 조회는 래퍼 없이 워크스페이스 객체를 응답한다")
  void getReturnsWorkspaceWithoutWrapper() throws Exception {
    WorkspaceDetail detail =
        new WorkspaceDetail(
            WORKSPACE_ID,
            "Momens",
            "momens",
            "제품팀 워크스페이스",
            Instant.parse("2026-06-27T09:00:00Z"),
            Instant.parse("2026-06-27T09:00:00Z"));
    when(workspaceService.get(eq(WORKSPACE_ID), eq(USER_ID))).thenReturn(detail);

    mockMvc
        .perform(
            get("/api/workspaces/{workspaceId}", WORKSPACE_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(WORKSPACE_ID.toString()))
        .andExpect(jsonPath("$.workspaces").doesNotExist());
  }

  @TestConfiguration
  static class ApiVersioningTestConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.useRequestHeader("API-Version").addSupportedVersions("1").setDefaultVersion("1");
    }
  }
}
