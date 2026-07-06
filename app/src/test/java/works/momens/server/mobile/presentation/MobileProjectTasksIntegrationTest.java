package works.momens.server.mobile.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import works.momens.server.auth.AccessTokenTestFactory;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;

/**
 * {@code /api/mobile/projects/{projectId}/tasks} 보드 조회와 생성 실배선 통합 테스트.
 *
 * <p>실토큰(auth public testFixtures)과 실제 PostgreSQL로 보안 체인부터 권한 검사, 보드 그룹 구성, 생성과 라벨 발급, 응답 shape까지
 * 끝까지 확인합니다. 사용자는 user public API로 만들고, workspace/멤버십/project/task는 아직 생성 public API가 없거나(workspace
 * 계열) 시드 편의를 위해 소유 스키마에 SQL로 넣습니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MobileProjectTasksIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private AccessTokenTestFactory accessTokens;
  @Autowired private UserService userService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void boardGroupsBoardStatusesAndExcludesBacklogAndCancelled() throws Exception {
    UserProfile jinsu = userService.findOrCreate("tasks-it-board@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("tasks-board");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "tasks-board-project");

    insertTask(workspace, project, "백로그", "backlog", "medium");
    UUID urgentTodo = insertTask(workspace, project, "긴급 투두", "todo", "urgent");
    addTaskRole(urgentTodo, "android");
    insertTask(workspace, project, "진행중", "in_progress", "medium");
    insertTask(workspace, project, "완료", "done", "low");
    insertTask(workspace, project, "취소", "cancelled", "high");

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/tasks", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("프로젝트 태스크"))
        .andExpect(jsonPath("$.groups.length()").value(3))
        .andExpect(jsonPath("$.groups[0].group_key").value("todo"))
        .andExpect(jsonPath("$.groups[0].count").value(1))
        .andExpect(jsonPath("$.groups[0].tasks[0].title").value("긴급 투두"))
        // 레거시에만 있는 urgent는 모바일 표기에서 high로 내린다.
        .andExpect(jsonPath("$.groups[0].tasks[0].priority").value("high"))
        .andExpect(jsonPath("$.groups[0].tasks[0].roles[0]").value("android"))
        .andExpect(jsonPath("$.groups[0].tasks[0].material_count").value(0))
        .andExpect(jsonPath("$.groups[1].group_key").value("in_progress"))
        .andExpect(jsonPath("$.groups[1].count").value(1))
        .andExpect(jsonPath("$.groups[2].group_key").value("done"))
        .andExpect(jsonPath("$.groups[2].count").value(1));
  }

  @Test
  void createTaskPersistsTodoTaskWithMomLabel() throws Exception {
    UserProfile jinsu = userService.findOrCreate("tasks-it-create@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("tasks-create");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "tasks-create-project");

    mockMvc
        .perform(
            post("/api/mobile/projects/{projectId}/tasks", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"권한 요청 점검\",\"roles\":[\"android\"],\"priority\":\"high\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.task.project_id").value(project.toString()))
        .andExpect(jsonPath("$.task.title").value("권한 요청 점검"))
        .andExpect(jsonPath("$.task.status").value("todo"))
        .andExpect(jsonPath("$.task.priority").value("high"))
        .andExpect(jsonPath("$.task.roles[0]").value("android"));

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "SELECT status, priority, label, workspace_id FROM tasks WHERE project_id = ?",
            project);
    org.assertj.core.api.Assertions.assertThat(row.get("status")).isEqualTo("todo");
    org.assertj.core.api.Assertions.assertThat(row.get("priority")).isEqualTo("high");
    org.assertj.core.api.Assertions.assertThat((String) row.get("label")).startsWith("MOM-");
    org.assertj.core.api.Assertions.assertThat(row.get("workspace_id")).isEqualTo(workspace);

    UUID taskId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM tasks WHERE project_id = ?", UUID.class, project);
    List<String> roles =
        jdbcTemplate.queryForList(
            "SELECT role FROM task_roles WHERE task_id = ?", String.class, taskId);
    org.assertj.core.api.Assertions.assertThat(roles).containsExactly("android");
  }

  @Test
  void createTaskRejectsBlankTitleWithStandardError() throws Exception {
    UserProfile jinsu = userService.findOrCreate("tasks-it-validation@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("tasks-validation");
    addMember(workspace, jinsu.id(), "owner");
    UUID project = insertProject(workspace, jinsu.id(), "tasks-validation-project");

    mockMvc
        .perform(
            post("/api/mobile/projects/{projectId}/tasks", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"  \",\"roles\":[\"pm\"],\"priority\":\"medium\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_FAILED"));
  }

  @Test
  void returnsForbiddenWhenCallerIsNotWorkspaceMember() throws Exception {
    UserProfile gyuil = userService.findOrCreate("tasks-it-owner@momens.works", "김규일", null);
    UserProfile jinsu = userService.findOrCreate("tasks-it-stranger@momens.works", "신진수", null);
    UUID workspace = insertWorkspace("tasks-forbidden");
    addMember(workspace, gyuil.id(), "owner");
    UUID project = insertProject(workspace, gyuil.id(), "tasks-forbidden-project");

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/tasks", project)
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(jinsu.id()))
                .header("API-Version", "1"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_FORBIDDEN"));
  }

  @Test
  void returnsNotFoundForUnknownProject() throws Exception {
    UserProfile caller = userService.findOrCreate("tasks-it-404@momens.works", "신진수", null);

    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/tasks", UUID.randomUUID())
                .header("Authorization", "Bearer " + accessTokens.issueAccessToken(caller.id()))
                .header("API-Version", "1"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("PROJECT_NOT_FOUND"));
  }

  @Test
  void returnsStandardUnauthorizedWithoutToken() throws Exception {
    mockMvc
        .perform(
            get("/api/mobile/projects/{projectId}/tasks", UUID.randomUUID())
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

  private UUID insertTask(
      UUID workspaceId, UUID projectId, String title, String status, String priority) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO tasks (id, workspace_id, project_id, title, status, priority)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id,
        workspaceId,
        projectId,
        title,
        status,
        priority);
    return id;
  }

  private void addTaskRole(UUID taskId, String role) {
    jdbcTemplate.update("INSERT INTO task_roles (task_id, role) VALUES (?, ?)", taskId, role);
  }
}
