package works.momens.server.web.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
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
import works.momens.server.project.WebTaskDetail;

@WebMvcTest(TaskWriteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TaskWriteControllerTest.ApiVersioningTestConfig.class)
class TaskWriteControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();
  private final Principal principal = USER_ID::toString;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TaskWriteService taskWriteService;

  @Test
  void createsLegacyTaskShape() throws Exception {
    when(taskWriteService.create(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(task());

    mockMvc
        .perform(
            post("/api/projects/{projectId}/tasks", PROJECT_ID)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"웹 태스크\",\"due_date\":\"2026-08-31\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(TASK_ID.toString()))
        .andExpect(jsonPath("$.due_date").value("2026-08-31"));
  }

  @Test
  void patchesAndDeletesLegacyPaths() throws Exception {
    when(taskWriteService.update(
            any(),
            any(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean()))
        .thenReturn(task());

    mockMvc
        .perform(
            patch("/api/tasks/{taskId}", TASK_ID)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee_id\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assignee_id").doesNotExist());
    mockMvc
        .perform(
            delete("/api/tasks/{taskId}", TASK_ID).principal(principal).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("deleted"));
    verify(taskWriteService).delete(TASK_ID, USER_ID);
  }

  private static WebTaskDetail task() {
    return new WebTaskDetail(
        TASK_ID,
        UUID.randomUUID(),
        PROJECT_ID,
        null,
        "MOM-867",
        "웹 태스크",
        null,
        "backlog",
        "medium",
        null,
        LocalDate.parse("2026-08-31"),
        Instant.parse("2026-08-21T00:00:00Z"),
        Instant.parse("2026-08-21T00:00:00Z"));
  }

  @TestConfiguration
  static class ApiVersioningTestConfig implements WebMvcConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
      configurer.useRequestHeader("API-Version").addSupportedVersions("1").setDefaultVersion("1");
    }
  }
}
