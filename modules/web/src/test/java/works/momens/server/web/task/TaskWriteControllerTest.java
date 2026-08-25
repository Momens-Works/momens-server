package works.momens.server.web.task;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.util.Map;
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
import works.momens.server.project.task.WebTaskDetail;
import works.momens.server.project.taskupdate.TaskUpdateDetail;

@WebMvcTest(TaskWriteController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TaskWriteControllerTest.ApiVersioningTestConfig.class)
class TaskWriteControllerTest {
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID TASK_ID = UUID.randomUUID();
  private static final UUID UPDATE_ID = UUID.randomUUID();
  private final Principal principal = USER_ID::toString;

  @Autowired private MockMvc mockMvc;
  @MockitoBean private TaskWriteService taskWriteService;

  @Test
  @DisplayName("태스크 생성은 레거시 응답 형식으로 반환한다")
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
  @DisplayName("태스크 수정과 삭제는 레거시 경로 및 null 필드 의미를 전달한다")
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
    verify(taskWriteService)
        .update(
            eq(TASK_ID),
            eq(USER_ID),
            isNull(),
            eq(false),
            isNull(),
            eq(false),
            isNull(),
            eq(false),
            isNull(),
            eq(false),
            isNull(),
            eq(false),
            isNull(),
            eq(true),
            isNull(),
            eq(false));
    mockMvc
        .perform(
            delete("/api/tasks/{taskId}", TASK_ID).principal(principal).header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("deleted"));
    verify(taskWriteService).delete(TASK_ID, USER_ID);
  }

  @Test
  @DisplayName("태스크 업데이트 생성과 삭제는 레거시 경로로 처리한다")
  void createsAndDeletesTaskUpdates() throws Exception {
    when(taskWriteService.createUpdate(
            eq(TASK_ID), eq(USER_ID), eq("첫 댓글"), eq("comment"), eq(Map.of("source", "web"))))
        .thenReturn(update());

    mockMvc
        .perform(
            post("/api/tasks/{taskId}/updates", TASK_ID)
                .principal(principal)
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"body\":\"첫 댓글\",\"kind\":\"comment\",\"metadata\":{\"source\":\"web\"}}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(UPDATE_ID.toString()))
        .andExpect(jsonPath("$.body").value("첫 댓글"));
    verify(taskWriteService)
        .createUpdate(TASK_ID, USER_ID, "첫 댓글", "comment", Map.of("source", "web"));

    mockMvc
        .perform(
            delete("/api/tasks/{taskId}/updates/{updateId}", TASK_ID, UPDATE_ID)
                .principal(principal)
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("deleted"));
    verify(taskWriteService).deleteUpdate(TASK_ID, UPDATE_ID, USER_ID);
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

  private static TaskUpdateDetail update() {
    return new TaskUpdateDetail(
        UPDATE_ID,
        UUID.randomUUID(),
        PROJECT_ID,
        TASK_ID,
        USER_ID,
        "첫 댓글",
        "comment",
        Map.of("source", "web"),
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
