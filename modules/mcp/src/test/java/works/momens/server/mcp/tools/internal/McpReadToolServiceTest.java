package works.momens.server.mcp.tools.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import works.momens.server.mcp.grant.McpGrantDetail;
import works.momens.server.mcp.grant.McpGrantReader;
import works.momens.server.mcp.grant.McpScope;
import works.momens.server.mcp.transport.McpAuthenticationContext;
import works.momens.server.mcp.transport.McpToolDefinition;
import works.momens.server.project.core.ProjectDetail;
import works.momens.server.project.core.ProjectDetailReader;
import works.momens.server.project.milestone.MilestoneDetail;
import works.momens.server.project.milestone.MilestoneReader;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskSnapshot;
import works.momens.server.project.taskupdate.TaskUpdateDetail;
import works.momens.server.project.taskupdate.TaskUpdateReader;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.membership.WorkspaceMembershipDetail;
import works.momens.server.workspace.membership.WorkspaceMembershipReader;
import works.momens.server.workspace.membership.WorkspaceRole;

class McpReadToolServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final McpGrantReader grants = mock(McpGrantReader.class);
  private final WorkspaceMembershipReader memberships = mock(WorkspaceMembershipReader.class);
  private final UserService users = mock(UserService.class);
  private final ProjectDetailReader projects = mock(ProjectDetailReader.class);
  private final MilestoneReader milestones = mock(MilestoneReader.class);
  private final TaskReader tasks = mock(TaskReader.class);
  private final TaskUpdateReader updates = mock(TaskUpdateReader.class);
  private final UUID workspaceId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID milestoneId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-09-27T03:04:00Z");
  private final McpAuthenticationContext context =
      new McpAuthenticationContext(
          UUID.randomUUID(),
          userId,
          "client",
          workspaceId,
          Arrays.stream(McpScope.values()).map(McpScope::value).collect(Collectors.toSet()));
  private McpReadToolService service;

  @BeforeEach
  void setup() throws Exception {
    service =
        new McpReadToolService(
            mapper, grants, memberships, users, projects, milestones, tasks, updates);
    when(grants.findActive(context.grantId()))
        .thenReturn(Optional.of(grant(context.scopes().stream().toList())));
    when(memberships.roleOf(workspaceId, userId)).thenReturn(Optional.of(WorkspaceRole.MEMBER));
  }

  @Test
  void catalogMatchesGoldenAndDoesNotExposeMutableSchema() throws Exception {
    ArrayNode actual = mapper.createArrayNode();
    for (McpToolDefinition tool : service.list(context)) {
      actual
          .addObject()
          .put("name", tool.name())
          .put("description", tool.description())
          .set("inputSchema", tool.inputSchema());
    }
    try (java.io.InputStream input =
        new ClassPathResource("mcp/read-tools-golden.json").getInputStream()) {
      assertThat(actual).isEqualTo(mapper.readTree(input));
    }
    assertThat(service.list(context)).isEqualTo(service.list(context));
    ((tools.jackson.databind.node.ObjectNode) service.list(context).getFirst().inputSchema())
        .put("type", "array");
    assertThat(service.list(context).getFirst().inputSchema().path("type").asText())
        .isEqualTo("object");
    McpAuthenticationContext limited =
        new McpAuthenticationContext(
            context.grantId(), userId, "client", workspaceId, Set.of(McpScope.TASKS_READ.value()));
    assertThat(service.list(limited))
        .extracting(McpToolDefinition::name)
        .containsExactly("get_task", "list_tasks");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"list_projects", "list_members", "list_milestones", "list_tasks", "get_task"})
  void revokedGrantBlocksEveryToolBeforeDomainReads(String name) {
    when(grants.findActive(context.grantId())).thenReturn(Optional.empty());
    assertThat(call(name, "{}").path("isError").asBoolean()).isTrue();
    verifyNoInteractions(projects, milestones, tasks, updates, users);
  }

  @Test
  void deniesRemovedMembershipAndReducedGrantScopes() {
    when(memberships.roleOf(workspaceId, userId)).thenReturn(Optional.empty());
    assertThat(call("list_projects", "{}").path("isError").asBoolean()).isTrue();
    when(memberships.roleOf(workspaceId, userId)).thenReturn(Optional.of(WorkspaceRole.MEMBER));
    when(grants.findActive(context.grantId()))
        .thenReturn(Optional.of(grant(List.of(McpScope.TASKS_READ.value()))));
    assertThat(call("list_projects", "{}").path("isError").asBoolean()).isTrue();
    verifyNoInteractions(projects, tasks);
  }

  @Test
  void rejectsMismatchedGrantIdentityAndMissingTokenScope() {
    McpAuthenticationContext wrongWorkspace =
        new McpAuthenticationContext(
            context.grantId(), userId, "client", UUID.randomUUID(), context.scopes());
    assertThat(
            service
                .call("list_projects", mapper.createObjectNode(), wrongWorkspace)
                .orElseThrow()
                .path("isError")
                .asBoolean())
        .isTrue();
    McpAuthenticationContext limited =
        new McpAuthenticationContext(
            context.grantId(), userId, "client", workspaceId, Set.of(McpScope.TASKS_READ.value()));
    assertThat(
            service
                .call("list_projects", mapper.createObjectNode(), limited)
                .orElseThrow()
                .path("isError")
                .asBoolean())
        .isTrue();
    verifyNoInteractions(projects);
  }

  @Test
  void preservesProjectMemberAndMilestoneText() {
    seed();
    assertThat(text(call("list_projects", "{}")))
        .isEqualTo("1 project(s):\n- PRJ-0003 Sprint [active]");
    assertThat(text(call("list_members", "{}")))
        .isEqualTo("1 member(s):\n- Alice <alice@example.com> [member]");
    assertThat(text(call("list_milestones", "{\"project\":\"prj-0003\"}")))
        .isEqualTo(
            "1 milestone(s):\n- Release ("
                + milestoneId
                + ") [active] · health: on_track · progress: 40% — PRJ-0003 Sprint · target: 2026-10-01");
  }

  @Test
  void filtersTasksAndPreservesDetailComments() {
    seed();
    assertThat(
            text(
                call(
                    "list_tasks",
                    "{\"project\":\"sprint\",\"assignee\":\"me\",\"status\":\" TODO \"}")))
        .isEqualTo("1 task(s):\n- MOM-0991 · Read tools [todo] — PRJ-0003 Sprint");
    assertThat(text(call("list_tasks", "{\"assignee\":\"ALICE@example.com\"}")))
        .contains("MOM-0991");
    assertThat(text(call("list_tasks", "{\"status\":\"unknown\"}"))).isEqualTo("No tasks match.");
    assertThat(text(call("get_task", "{\"task\":\" mom-0991 \"}")))
        .isEqualTo(
            "MOM-0991 · Read tools\nstatus: todo · priority: medium · project: PRJ-0003 Sprint · milestone: Release · assignee: Alice · due: 2026-10-01\n\nDescription\n\ncomments (1):\n- [2026-09-27 03:04] Comment");
    verify(tasks).findSnapshotByLabel(workspaceId, "MOM-0991");
  }

  @Test
  void taskLookupIsScopedBeforeReadingComments() {
    assertThat(call("get_task", "{\"task\":\"" + taskId + "\"}").path("isError").asBoolean())
        .isTrue();
    verify(tasks).findSnapshotInWorkspace(workspaceId, taskId);
    verifyNoInteractions(updates, projects, milestones, users);
  }

  @Test
  void distinguishesUnknownToolsFromInvalidArguments() {
    assertThat(service.call("create_task", mapper.createObjectNode(), context)).isEmpty();
    assertThat(call("get_task", "{}").path("isError").asBoolean()).isTrue();
    assertThat(call("list_tasks", "{\"status\":42}").path("isError").asBoolean()).isTrue();
    assertThat(call("list_projects", "{\"workspace_id\":\"other\"}").path("isError").asBoolean())
        .isTrue();
    verifyNoInteractions(projects, tasks);
  }

  @Test
  void emptyListsKeepLegacyMessages() {
    assertThat(text(call("list_projects", "{}"))).isEqualTo("No projects in this workspace yet.");
    assertThat(text(call("list_members", "{}"))).isEqualTo("No members found.");
    assertThat(text(call("list_milestones", "{}"))).isEqualTo("No milestones match.");
    assertThat(text(call("list_tasks", "{}"))).isEqualTo("No tasks match.");
  }

  @Test
  void projectResolutionDoesNotLetLongInputMatchShortNameAndRejectsAmbiguity() {
    seed();
    ProjectDetail first = projects.listDetailsByWorkspaceId(workspaceId).getFirst();
    ProjectDetail duplicate =
        new ProjectDetail(
            UUID.randomUUID(),
            workspaceId,
            "PRJ-0004",
            "Sprint",
            null,
            "active",
            userId,
            List.of(userId),
            null,
            "planned",
            null,
            0,
            0,
            null,
            null,
            now,
            now);
    when(projects.listDetailsByWorkspaceId(workspaceId)).thenReturn(List.of(first, duplicate));
    assertThat(call("list_tasks", "{\"project\":\"Sprint\"}").path("isError").asBoolean()).isTrue();
    assertThat(call("list_tasks", "{\"project\":\"Sprint extended\"}").path("isError").asBoolean())
        .isTrue();
    assertThat(text(call("list_tasks", "{\"project\":\"PRJ-0003\"}"))).contains("MOM-0991");
    assertThat(
            call("list_tasks", "{\"project\":\"" + UUID.randomUUID() + "\"}")
                .path("isError")
                .asBoolean())
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Alice", "Ali"})
  void ambiguousAssigneeRequiresEmail(String reference) {
    UUID otherUserId = UUID.randomUUID();
    when(memberships.listMembershipDetails(workspaceId))
        .thenReturn(
            List.of(
                new WorkspaceMembershipDetail(userId, "member", now, now),
                new WorkspaceMembershipDetail(otherUserId, "member", now, now)));
    when(users.getProfiles(List.of(userId, otherUserId)))
        .thenReturn(
            List.of(
                new UserProfile(userId, "alice@example.com", "Alice", null, null, now, now),
                new UserProfile(otherUserId, "other@example.com", "Alice", null, null, now, now)));
    JsonNode result = call("list_tasks", "{\"assignee\":\"" + reference + "\"}");
    assertThat(result.path("isError").asBoolean()).isTrue();
    assertThat(text(result)).isEqualTo("This reference matches several members — use their email");
    verifyNoInteractions(tasks);
    assertThat(
            call("list_tasks", "{\"assignee\":\"alice@example.com\"}").path("isError").asBoolean())
        .isFalse();
    verify(tasks).listSnapshotsByWorkspaceId(workspaceId);
  }

  @Test
  void taskWithoutOptionalFieldsKeepsMinimalText() {
    TaskSnapshot task =
        new TaskSnapshot(
            taskId,
            workspaceId,
            projectId,
            null,
            null,
            "Read tools",
            null,
            "todo",
            "medium",
            "implementation",
            null,
            null,
            now,
            now);
    when(tasks.findSnapshotInWorkspace(workspaceId, taskId)).thenReturn(Optional.of(task));
    when(tasks.listSnapshotsByWorkspaceId(workspaceId)).thenReturn(List.of(task));
    JsonNode detail = call("get_task", "{\"task\":\"" + taskId + "\"}");
    assertThat(detail.path("isError").asBoolean()).isFalse();
    assertThat(text(detail)).isEqualTo(taskId + " · Read tools\nstatus: todo · priority: medium");
    assertThat(text(call("list_tasks", "{}")))
        .isEqualTo("1 task(s):\n- " + taskId + " · Read tools [todo]");
    verifyNoInteractions(milestones, users);
  }

  private McpGrantDetail grant(List<String> scopes) {
    return new McpGrantDetail(
        context.grantId(), userId, "client", workspaceId, scopes, now, null, now);
  }

  private void seed() {
    ProjectDetail project =
        new ProjectDetail(
            projectId,
            workspaceId,
            "PRJ-0003",
            "Sprint",
            null,
            "active",
            userId,
            List.of(userId),
            null,
            "planned",
            null,
            0,
            0,
            null,
            null,
            now,
            now);
    TaskSnapshot task =
        new TaskSnapshot(
            taskId,
            workspaceId,
            projectId,
            milestoneId,
            "MOM-0991",
            "Read tools",
            "Description",
            "todo",
            "medium",
            "implementation",
            userId,
            LocalDate.parse("2026-10-01"),
            now,
            now);
    when(projects.listDetailsByWorkspaceId(workspaceId)).thenReturn(List.of(project));
    when(tasks.listSnapshotsByWorkspaceId(workspaceId)).thenReturn(List.of(task));
    when(tasks.findSnapshotByLabel(workspaceId, "MOM-0991")).thenReturn(Optional.of(task));
    when(memberships.listMembershipDetails(workspaceId))
        .thenReturn(List.of(new WorkspaceMembershipDetail(userId, "member", now, now)));
    when(users.getProfiles(List.of(userId)))
        .thenReturn(
            List.of(new UserProfile(userId, "alice@example.com", "Alice", null, null, now, now)));
    when(milestones.listDetailsByWorkspaceId(workspaceId))
        .thenReturn(
            List.of(
                new MilestoneDetail(
                    milestoneId,
                    projectId,
                    "Release",
                    null,
                    LocalDate.parse("2026-10-01"),
                    "active",
                    List.of(),
                    "on_track",
                    40,
                    null,
                    null,
                    now,
                    now)));
    when(updates.listByTaskId(taskId))
        .thenReturn(
            List.of(
                new TaskUpdateDetail(
                    UUID.randomUUID(),
                    workspaceId,
                    projectId,
                    taskId,
                    userId,
                    "Comment",
                    "comment",
                    null,
                    now,
                    now)));
  }

  private JsonNode call(String name, String args) {
    return service.call(name, mapper.readTree(args), context).orElseThrow();
  }

  private static String text(JsonNode result) {
    assertThat(result.path("resultType").asText()).isEqualTo("complete");
    return result.path("content").get(0).path("text").asText();
  }
}
