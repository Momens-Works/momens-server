package works.momens.server.mcp.tools.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.api.FieldValidationException;
import works.momens.server.mcp.grant.McpGrantDetail;
import works.momens.server.mcp.grant.McpGrantReader;
import works.momens.server.mcp.grant.McpScope;
import works.momens.server.mcp.transport.McpAuthenticationContext;
import works.momens.server.mcp.transport.McpToolDefinition;
import works.momens.server.project.core.ProjectDetail;
import works.momens.server.project.core.ProjectDetailReader;
import works.momens.server.project.milestone.CreateMilestoneCommand;
import works.momens.server.project.milestone.MilestoneDetail;
import works.momens.server.project.milestone.MilestoneReader;
import works.momens.server.project.milestone.MilestoneWriter;
import works.momens.server.project.milestone.UpdateMilestoneCommand;
import works.momens.server.project.task.CreateTaskCommand;
import works.momens.server.project.task.PatchTaskCommand;
import works.momens.server.project.task.TaskPriority;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskSnapshot;
import works.momens.server.project.task.TaskStatus;
import works.momens.server.project.task.TaskWriter;
import works.momens.server.project.taskupdate.CreateTaskUpdateCommand;
import works.momens.server.project.taskupdate.TaskUpdateKind;
import works.momens.server.project.taskupdate.TaskUpdateReader;
import works.momens.server.project.taskupdate.TaskUpdateWriter;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.membership.WorkspaceMembershipDetail;
import works.momens.server.workspace.membership.WorkspaceMembershipReader;
import works.momens.server.workspace.membership.WorkspaceRole;

class McpWriteToolServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final McpGrantReader grants = mock(McpGrantReader.class);
  private final WorkspaceMembershipReader memberships = mock(WorkspaceMembershipReader.class);
  private final UserService users = mock(UserService.class);
  private final ProjectDetailReader projects = mock(ProjectDetailReader.class);
  private final MilestoneReader milestones = mock(MilestoneReader.class);
  private final TaskReader tasks = mock(TaskReader.class);
  private final TaskWriter taskWriter = mock(TaskWriter.class);
  private final MilestoneWriter milestoneWriter = mock(MilestoneWriter.class);
  private final TaskUpdateWriter updates = mock(TaskUpdateWriter.class);
  private final UUID workspaceId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID projectId = UUID.randomUUID();
  private final UUID taskId = UUID.randomUUID();
  private final UUID milestoneId = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-09-28T03:04:00Z");
  private final McpAuthenticationContext context =
      new McpAuthenticationContext(
          UUID.randomUUID(),
          userId,
          "client",
          workspaceId,
          Arrays.stream(McpScope.values()).map(McpScope::value).collect(Collectors.toSet()));
  private McpWriteToolService writes;
  private McpToolService service;

  @BeforeEach
  void setup() throws Exception {
    writes =
        new McpWriteToolService(
            mapper,
            grants,
            memberships,
            users,
            projects,
            milestones,
            tasks,
            taskWriter,
            milestoneWriter,
            updates);
    service =
        new McpToolService(
            new McpReadToolService(
                mapper,
                grants,
                memberships,
                users,
                projects,
                milestones,
                tasks,
                mock(TaskUpdateReader.class)),
            writes,
            mapper);
    when(grants.findActive(context.grantId())).thenReturn(Optional.of(grant(context.scopes())));
    when(memberships.roleOf(workspaceId, userId)).thenReturn(Optional.of(WorkspaceRole.MEMBER));
  }

  @Test
  void catalogMatchesGoldenAndCombinedCatalogIsSortedAndScopeFiltered() throws Exception {
    ArrayNode actual = mapper.createArrayNode();
    for (McpToolDefinition tool : writes.list(context)) {
      actual
          .addObject()
          .put("name", tool.name())
          .put("description", tool.description())
          .set("inputSchema", tool.inputSchema());
    }
    try (InputStream input =
        new ClassPathResource("mcp/write-tools-golden.json").getInputStream()) {
      assertThat(actual).isEqualTo(mapper.readTree(input));
    }
    assertThat(service.list(context))
        .extracting(McpToolDefinition::name)
        .containsExactly(
            "create_comment",
            "create_milestone",
            "create_task",
            "delete_milestone",
            "get_task",
            "list_members",
            "list_milestones",
            "list_projects",
            "list_tasks",
            "update_milestone",
            "update_task");
    assertThat(service.list(context)).isEqualTo(service.list(context));
    ((ObjectNode) writes.list(context).getFirst().inputSchema()).put("type", "array");
    assertThat(writes.list(context).getFirst().inputSchema().path("type").asText())
        .isEqualTo("object");
    assertThat(service.list(withScopes(Set.of(McpScope.TASKS_WRITE.value()))))
        .extracting(McpToolDefinition::name)
        .containsExactly("create_comment", "create_task", "update_task");
    assertThat(service.list(withScopes(Set.of(McpScope.MILESTONES_WRITE.value()))))
        .extracting(McpToolDefinition::name)
        .containsExactly("create_milestone", "delete_milestone", "update_milestone");
    assertThat(service.call("unknown", mapper.createObjectNode(), context)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "create_task",
        "update_task",
        "create_comment",
        "create_milestone",
        "update_milestone",
        "delete_milestone"
      })
  void revokedGrantBlocksAllWritesBeforeDomainAccess(String name) {
    when(grants.findActive(context.grantId())).thenReturn(Optional.empty());
    assertThat(call(name, "{}").path("isError").asBoolean()).isTrue();
    verifyNoInteractions(projects, tasks, milestones, users, taskWriter, milestoneWriter, updates);
  }

  @ParameterizedTest
  @ValueSource(strings = {"membership", "scope", "user", "client", "workspace", "token_scope"})
  void revalidatesEveryAuthorizationDimension(String dimension) {
    McpGrantDetail grant = grant(context.scopes());
    switch (dimension) {
      case "membership" ->
          when(memberships.roleOf(workspaceId, userId)).thenReturn(Optional.empty());
      case "scope" -> grant = grant(Set.of(McpScope.TASKS_READ.value()));
      case "user" ->
          grant =
              new McpGrantDetail(
                  context.grantId(),
                  UUID.randomUUID(),
                  "client",
                  workspaceId,
                  grant.scopes(),
                  now,
                  null,
                  now);
      case "client" ->
          grant =
              new McpGrantDetail(
                  context.grantId(), userId, "other", workspaceId, grant.scopes(), now, null, now);
      case "workspace" ->
          grant =
              new McpGrantDetail(
                  context.grantId(),
                  userId,
                  "client",
                  UUID.randomUUID(),
                  grant.scopes(),
                  now,
                  null,
                  now);
      default -> {}
    }
    when(grants.findActive(context.grantId())).thenReturn(Optional.of(grant));
    McpAuthenticationContext caller =
        dimension.equals("token_scope") ? withScopes(Set.of(McpScope.TASKS_READ.value())) : context;
    assertThat(
            service
                .call("create_task", mapper.readTree("{}"), caller)
                .orElseThrow()
                .path("isError")
                .asBoolean())
        .isTrue();
    verifyNoInteractions(projects, tasks, milestones, taskWriter, milestoneWriter, updates);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"project\":\"p\",\"title\":\" \"}",
        "{\"project\":\"p\",\"title\":null}",
        "{\"project\":\"p\",\"title\":42}",
        "{\"project\":\"p\",\"title\":\"t\",\"extra\":true}",
        "[]"
      })
  void invalidShapeCannotReachWriter(String args) {
    assertThat(call("create_task", args).path("isError").asBoolean()).isTrue();
    verifyNoInteractions(projects, taskWriter);
  }

  @Test
  void createsTaskWithLegacyDefaultsAndReferencesUsingWriteScopeAlone() {
    seed();
    when(taskWriter.create(any())).thenReturn(task());
    JsonNode result =
        service
            .call(
                "create_task",
                mapper.readTree(
                    """
        {"project":"prj-0003","title":" Write tools ","assignee":"me","milestone":"Release"}
        """),
                withScopes(Set.of(McpScope.TASKS_WRITE.value())))
            .orElseThrow();
    assertThat(text(result))
        .isEqualTo(
            "Created MOM-0993 · Write tools\nproject: PRJ-0003 Sprint · status: todo · priority: medium · milestone: Release · due: 2026-10-01");
    ArgumentCaptor<CreateTaskCommand> command = ArgumentCaptor.forClass(CreateTaskCommand.class);
    verify(taskWriter).create(command.capture());
    assertThat(command.getValue().workspaceId()).isEqualTo(workspaceId);
    assertThat(command.getValue().projectId()).isEqualTo(projectId);
    assertThat(command.getValue().title()).isEqualTo("Write tools");
    assertThat(command.getValue().status()).isEqualTo(TaskStatus.BACKLOG);
    assertThat(command.getValue().priority()).isEqualTo(TaskPriority.MEDIUM);
    assertThat(command.getValue().assigneeId()).isEqualTo(userId);
    assertThat(command.getValue().milestoneId()).isEqualTo(milestoneId);
  }

  @ParameterizedTest
  @CsvSource({"progress,med", "in-progress,MED", "IN_PROGRESS,medium"})
  void preservesTaskStatusAndPriorityAliases(String status, String priority) {
    seed();
    when(taskWriter.patch(any())).thenReturn(task());
    assertThat(
            call(
                    "update_task",
                    mapper.writeValueAsString(
                        Map.of("task", "mom-0993", "status", status, "priority", priority)))
                .path("isError")
                .asBoolean())
        .isFalse();
    ArgumentCaptor<PatchTaskCommand> command = ArgumentCaptor.forClass(PatchTaskCommand.class);
    verify(taskWriter).patch(command.capture());
    assertThat(command.getValue().status()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(command.getValue().priority()).isEqualTo(TaskPriority.MEDIUM);
  }

  @Test
  void separatesOmittedFieldsFromExplicitClears() {
    seed();
    when(taskWriter.patch(any())).thenReturn(task());
    call(
        "update_task",
        "{\"task\":\"mom-0993\",\"description\":\"\",\"due_date\":\"\",\"assignee\":\"none\",\"milestone\":\"remove\"}");
    ArgumentCaptor<PatchTaskCommand> command = ArgumentCaptor.forClass(PatchTaskCommand.class);
    verify(taskWriter).patch(command.capture());
    PatchTaskCommand patch = command.getValue();
    assertThat(patch.descriptionSet()).isTrue();
    assertThat(patch.description()).isNull();
    assertThat(patch.dueDateSet()).isTrue();
    assertThat(patch.dueDate()).isNull();
    assertThat(patch.assigneeSet()).isTrue();
    assertThat(patch.assigneeId()).isNull();
    assertThat(patch.milestoneSet()).isTrue();
    assertThat(patch.milestoneId()).isNull();
    assertThat(patch.titleSet()).isFalse();
    assertThat(patch.statusSet()).isFalse();
    assertThat(patch.prioritySet()).isFalse();
  }

  @Test
  void omittedOptionalFieldsAreNotCleared() {
    seed();
    when(taskWriter.patch(any())).thenReturn(task());
    call("update_task", "{\"task\":\"mom-0993\"}");
    ArgumentCaptor<PatchTaskCommand> command = ArgumentCaptor.forClass(PatchTaskCommand.class);
    verify(taskWriter).patch(command.capture());
    assertThat(command.getValue().descriptionSet()).isFalse();
    assertThat(command.getValue().dueDateSet()).isFalse();
    assertThat(command.getValue().assigneeSet()).isFalse();
    assertThat(command.getValue().milestoneSet()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"status\":\"blocked\"}",
        "{\"priority\":\"p1\"}",
        "{\"due_date\":\"2026-02-30\"}",
        "{\"due_date\":\"2026-1-01\"}",
        "{\"description\":null}",
        "{\"milestone\":\"absent\"}",
        "{\"assignee\":\"absent\"}"
      })
  void invalidTaskValuesCannotReachWriter(String fields) {
    seed();
    ObjectNode args = (ObjectNode) mapper.readTree(fields);
    args.put("task", "MOM-0993");
    assertThat(call("update_task", args.toString()).path("isError").asBoolean()).isTrue();
    verifyNoInteractions(taskWriter);
  }

  @ParameterizedTest
  @ValueSource(strings = {"update_task", "create_comment"})
  void missingOrDeletedProjectBlocksTaskWritesByUuidAndLabel(String name) {
    seed();
    when(projects.listDetailsByWorkspaceId(workspaceId)).thenReturn(List.of());
    for (String reference : List.of("MOM-0993", taskId.toString(), UUID.randomUUID().toString())) {
      ObjectNode args = mapper.createObjectNode().put("task", reference);
      if (name.equals("create_comment")) {
        args.put("body", "Comment");
      }
      assertThat(call(name, args.toString()).path("isError").asBoolean()).isTrue();
    }
    verifyNoInteractions(taskWriter, updates);
  }

  @Test
  void rejectsMilestoneFromAnotherProjectAndAmbiguousNames() {
    seed();
    when(milestones.listDetailsByWorkspaceId(workspaceId))
        .thenReturn(List.of(milestone(milestoneId, UUID.randomUUID(), "Release")));
    assertThat(
            call(
                    "create_task",
                    "{\"project\":\"Sprint\",\"title\":\"Task\",\"milestone\":\"Release\"}")
                .path("isError")
                .asBoolean())
        .isTrue();
    when(milestones.listDetailsByWorkspaceId(workspaceId))
        .thenReturn(
            List.of(
                milestone(milestoneId, projectId, "Release"),
                milestone(UUID.randomUUID(), projectId, "Release")));
    assertThat(call("delete_milestone", "{\"milestone\":\"Release\"}").path("isError").asBoolean())
        .isTrue();
    verifyNoInteractions(taskWriter, milestoneWriter);
  }

  @Test
  void createsCommentUsingAuthenticatedAuthorAndTaskOwnership() {
    seed();
    assertThat(text(call("create_comment", "{\"task\":\"mom-0993\",\"body\":\" Comment \"}")))
        .isEqualTo("Added a comment to MOM-0993.");
    verify(updates)
        .create(
            new CreateTaskUpdateCommand(
                taskId, workspaceId, projectId, userId, "Comment", TaskUpdateKind.COMMENT, null));
  }

  @Test
  void createsUpdatesAndDeletesMilestonesWithLegacyText() {
    seed();
    when(milestoneWriter.create(any())).thenReturn(milestone(milestoneId, projectId, "Release"));
    when(milestoneWriter.update(any())).thenReturn(milestone(milestoneId, projectId, "Release"));
    assertThat(
            text(
                call(
                    "create_milestone",
                    "{\"project\":\"Sprint\",\"name\":\" Release \",\"progress\":0}")))
        .isEqualTo(
            "Created milestone Release ("
                + milestoneId
                + ").\nproject: PRJ-0003 Sprint · status: active · target: 2026-10-01");
    ArgumentCaptor<CreateMilestoneCommand> create =
        ArgumentCaptor.forClass(CreateMilestoneCommand.class);
    verify(milestoneWriter).create(create.capture());
    assertThat(create.getValue().requesterId()).isEqualTo(userId);
    assertThat(create.getValue().ownerUserIds()).isEmpty();
    assertThat(create.getValue().progress()).isZero();
    assertThat(text(call("update_milestone", "{\"milestone\":\"Release\",\"progress\":40}")))
        .isEqualTo(
            "Updated milestone Release ("
                + milestoneId
                + ").\nstatus: active · health: on_track · progress: 40% · target: 2026-10-01");
    verify(milestoneWriter)
        .update(new UpdateMilestoneCommand(milestoneId, "", "", "", null, "", 40, ""));
    assertThat(text(call("delete_milestone", "{\"milestone\":\"" + milestoneId + "\"}")))
        .isEqualTo("Removed milestone Release (" + milestoneId + ").");
    verify(milestoneWriter).delete(milestoneId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "101", "1.5", "2147483648", "null", "\"10\""})
  void invalidProgressIsRejected(String value) {
    seed();
    assertThat(
            call("update_milestone", "{\"milestone\":\"Release\",\"progress\":" + value + "}")
                .path("isError")
                .asBoolean())
        .isTrue();
    verifyNoInteractions(milestoneWriter);
  }

  @Test
  void mapsExpectedWriterErrorsWithoutExposingDetailsAndPropagatesInternalErrors() {
    seed();
    when(taskWriter.patch(any()))
        .thenThrow(FieldValidationException.forField("assignee_id", "private detail"));
    assertThat(text(call("update_task", "{\"task\":\"MOM-0993\"}")))
        .isEqualTo("Invalid value or reference for this tool.");
    doThrow(new BusinessException(CommonErrorCode.COMMON_NOT_FOUND, "private detail"))
        .when(taskWriter)
        .patch(any());
    assertThat(text(call("update_task", "{\"task\":\"MOM-0993\"}")))
        .isEqualTo("Resource not found in this workspace.");
    doThrow(new IllegalStateException("private SQL")).when(taskWriter).patch(any());
    assertThatThrownBy(() -> call("update_task", "{\"task\":\"MOM-0993\"}"))
        .isInstanceOf(IllegalStateException.class);
  }

  private void seed() {
    when(projects.listDetailsByWorkspaceId(workspaceId))
        .thenReturn(
            List.of(
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
                    now)));
    when(tasks.findSnapshotByLabel(workspaceId, "MOM-0993")).thenReturn(Optional.of(task()));
    when(tasks.findSnapshotInWorkspace(workspaceId, taskId)).thenReturn(Optional.of(task()));
    when(milestones.listDetailsByWorkspaceId(workspaceId))
        .thenReturn(List.of(milestone(milestoneId, projectId, "Release")));
    when(memberships.listMembershipDetails(workspaceId))
        .thenReturn(List.of(new WorkspaceMembershipDetail(userId, "member", now, now)));
    when(users.getProfiles(List.of(userId)))
        .thenReturn(
            List.of(new UserProfile(userId, "alice@example.com", "Alice", null, null, now, now)));
  }

  private TaskSnapshot task() {
    return new TaskSnapshot(
        taskId,
        workspaceId,
        projectId,
        milestoneId,
        "MOM-0993",
        "Write tools",
        "Description",
        "todo",
        "medium",
        null,
        userId,
        LocalDate.parse("2026-10-01"),
        now,
        now);
  }

  private MilestoneDetail milestone(UUID id, UUID project, String name) {
    return new MilestoneDetail(
        id,
        project,
        name,
        null,
        LocalDate.parse("2026-10-01"),
        "active",
        List.of(),
        "on_track",
        40,
        null,
        null,
        now,
        now);
  }

  private McpGrantDetail grant(Set<String> scopes) {
    return new McpGrantDetail(
        context.grantId(), userId, "client", workspaceId, List.copyOf(scopes), now, null, now);
  }

  private McpAuthenticationContext withScopes(Set<String> scopes) {
    return new McpAuthenticationContext(context.grantId(), userId, "client", workspaceId, scopes);
  }

  private JsonNode call(String name, String args) {
    return service.call(name, mapper.readTree(args), context).orElseThrow();
  }

  private static String text(JsonNode result) {
    assertThat(result.path("resultType").asText()).isEqualTo("complete");
    return result.path("content").get(0).path("text").asText();
  }
}
