package works.momens.server.web.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
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

@WebMvcTest(TaskLinkController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TaskLinkControllerTest.ApiVersioningTestConfig.class)
class TaskLinkControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();
  private static final UUID MEMORY_ID = UUID.randomUUID();
  private static final UUID SOURCE_REF_ID = UUID.randomUUID();
  private final Principal principal = USER_ID::toString;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TaskLinkService taskLinkService;

  @Test
  @DisplayName("메모리를 연결하면 201과 레거시 메시지를 반환한다")
  void linksMemoryWithLegacyResponse() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/{taskId}/memories/{memoryId}", TASK_ID, MEMORY_ID)
                .principal(principal))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.message").value("linked"));

    verify(taskLinkService).linkMemory(TASK_ID, MEMORY_ID, USER_ID);
  }

  @Test
  @DisplayName("메모리 연결을 해제하면 200과 레거시 메시지를 반환한다")
  void unlinksMemoryWithLegacyResponse() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/{taskId}/memories/{memoryId}", TASK_ID, MEMORY_ID)
                .principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("unlinked"));

    verify(taskLinkService).unlinkMemory(TASK_ID, MEMORY_ID, USER_ID);
  }

  @Test
  @DisplayName("링크를 첨부하면 201과 생성된 식별자를 함께 반환한다")
  void createsSourceRefWithLegacyResponse() throws Exception {
    when(taskLinkService.createSourceRef(eq(TASK_ID), eq(USER_ID), any(), any(), any()))
        .thenReturn(SOURCE_REF_ID);

    mockMvc
        .perform(
            post("/api/tasks/{taskId}/source-refs", TASK_ID)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source_url":"https://example.com/doc","source_type":"notion","title":"노트"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(SOURCE_REF_ID.toString()))
        .andExpect(jsonPath("$.message").value("linked"));

    verify(taskLinkService)
        .createSourceRef(TASK_ID, USER_ID, "https://example.com/doc", "notion", "노트");
  }

  @Test
  @DisplayName("주소가 공백으로만 이루어져 있으면 400으로 응답한다")
  void rejectsBlankSourceUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/tasks/{taskId}/source-refs", TASK_ID)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"source_url":"   "}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("source-ref 연결을 해제하면 200과 레거시 메시지를 반환한다")
  void unlinksSourceRefWithLegacyResponse() throws Exception {
    mockMvc
        .perform(
            delete("/api/tasks/{taskId}/source-refs/{sourceRefId}", TASK_ID, SOURCE_REF_ID)
                .principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("unlinked"));

    verify(taskLinkService).unlinkSourceRef(TASK_ID, SOURCE_REF_ID, USER_ID);
  }

  @TestConfiguration
  static class ApiVersioningTestConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.useRequestHeader("API-Version").addSupportedVersions("1").setDefaultVersion("1");
    }
  }
}
