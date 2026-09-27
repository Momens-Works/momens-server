package works.momens.server.mcp.tools.internal;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import works.momens.server.mcp.grant.McpGrantReader;
import works.momens.server.mcp.grant.McpScope;
import works.momens.server.mcp.transport.McpAuthenticationContext;
import works.momens.server.mcp.transport.McpToolDefinition;
import works.momens.server.project.core.ProjectDetail;
import works.momens.server.project.core.ProjectDetailReader;
import works.momens.server.project.milestone.CreateMilestoneCommand;
import works.momens.server.project.milestone.MilestoneDetail;
import works.momens.server.project.milestone.MilestoneHealthStatus;
import works.momens.server.project.milestone.MilestoneReader;
import works.momens.server.project.milestone.MilestoneWriter;
import works.momens.server.project.milestone.UpdateMilestoneCommand;
import works.momens.server.project.task.CreateTaskCommand;
import works.momens.server.project.task.PatchTaskCommand;
import works.momens.server.project.task.TaskOrigin;
import works.momens.server.project.task.TaskPriority;
import works.momens.server.project.task.TaskReader;
import works.momens.server.project.task.TaskSnapshot;
import works.momens.server.project.task.TaskStatus;
import works.momens.server.project.task.TaskWriter;
import works.momens.server.project.taskupdate.CreateTaskUpdateCommand;
import works.momens.server.project.taskupdate.TaskUpdateKind;
import works.momens.server.project.taskupdate.TaskUpdateWriter;
import works.momens.server.user.UserService;
import works.momens.server.workspace.membership.WorkspaceMembershipReader;

@Service
class McpWriteToolService {
  private static final Map<String, McpScope> SCOPES =
      Map.of(
          "create_task", McpScope.TASKS_WRITE,
          "update_task", McpScope.TASKS_WRITE,
          "create_comment", McpScope.TASKS_WRITE,
          "create_milestone", McpScope.MILESTONES_WRITE,
          "update_milestone", McpScope.MILESTONES_WRITE,
          "delete_milestone", McpScope.MILESTONES_WRITE);

  private final ObjectMapper mapper;
  private final McpGrantReader grants;
  private final WorkspaceMembershipReader memberships;
  private final UserService users;
  private final ProjectDetailReader projects;
  private final MilestoneReader milestones;
  private final TaskReader tasks;
  private final TaskWriter taskWriter;
  private final MilestoneWriter milestoneWriter;
  private final TaskUpdateWriter updates;
  private final List<McpToolDefinition> definitions;

  McpWriteToolService(
      ObjectMapper mapper,
      McpGrantReader grants,
      WorkspaceMembershipReader memberships,
      UserService users,
      ProjectDetailReader projects,
      MilestoneReader milestones,
      TaskReader tasks,
      TaskWriter taskWriter,
      MilestoneWriter milestoneWriter,
      TaskUpdateWriter updates)
      throws IOException {
    this.mapper = mapper;
    this.grants = grants;
    this.memberships = memberships;
    this.users = users;
    this.projects = projects;
    this.milestones = milestones;
    this.tasks = tasks;
    this.taskWriter = taskWriter;
    this.milestoneWriter = milestoneWriter;
    this.updates = updates;
    try (InputStream input = new ClassPathResource("mcp/write-tools.json").getInputStream()) {
      List<McpToolDefinition> loaded = new ArrayList<>();
      for (JsonNode tool : mapper.readTree(input)) {
        loaded.add(
            new McpToolDefinition(
                tool.get("name").asText(),
                tool.get("description").asText(),
                tool.get("inputSchema")));
      }
      definitions = List.copyOf(loaded);
    }
  }

  public List<McpToolDefinition> list(McpAuthenticationContext context) {
    return definitions.stream()
        .filter(tool -> context.scopes().contains(SCOPES.get(tool.name()).value()))
        .map(
            tool ->
                new McpToolDefinition(
                    tool.name(), tool.description(), tool.inputSchema().deepCopy()))
        .toList();
  }

  @Transactional
  public Optional<JsonNode> call(
      String name, JsonNode arguments, McpAuthenticationContext context) {
    McpScope scope = SCOPES.get(name);
    if (scope == null) {
      return Optional.empty();
    }
    requireAuthorization(context, scope);
    validateArguments(name, arguments);
    String text =
        switch (name) {
          case "create_task" -> createTask(context, arguments);
          case "update_task" -> updateTask(context, arguments);
          case "create_comment" -> createComment(context, arguments);
          case "create_milestone" -> createMilestone(context, arguments);
          case "update_milestone" -> updateMilestone(context, arguments);
          case "delete_milestone" -> deleteMilestone(context, argument(arguments, "milestone"));
          default -> throw new IllegalStateException("Unregistered write tool");
        };
    ObjectNode result = mapper.createObjectNode();
    result.put("resultType", "complete");
    result.putArray("content").addObject().put("type", "text").put("text", text);
    return Optional.of(result);
  }

  private void requireAuthorization(McpAuthenticationContext context, McpScope scope) {
    boolean allowed =
        context.permits(context.workspaceId(), scope)
            && grants
                .findActive(context.grantId())
                .filter(
                    grant ->
                        grant.userId().equals(context.userId())
                            && grant.clientId().equals(context.clientId())
                            && grant.workspaceId().equals(context.workspaceId())
                            && grant.scopes().contains(scope.value()))
                .isPresent()
            && memberships.roleOf(context.workspaceId(), context.userId()).isPresent();
    if (!allowed) {
      throw new McpToolInputException("You don't have access to this tool.");
    }
  }

  private void validateArguments(String name, JsonNode arguments) {
    JsonNode schema =
        definitions.stream()
            .filter(tool -> tool.name().equals(name))
            .findFirst()
            .orElseThrow()
            .inputSchema();
    if (!arguments.isObject()) {
      throw new McpToolInputException("arguments must be an object");
    }
    for (Map.Entry<String, JsonNode> field : arguments.properties()) {
      JsonNode property = schema.get("properties").get(field.getKey());
      boolean valid =
          property != null
              && (property.path("type").asText().equals("integer")
                  ? field.getValue().isIntegralNumber() && field.getValue().canConvertToInt()
                  : field.getValue().isTextual());
      if (!valid) {
        throw new McpToolInputException(
            "Unknown argument or invalid argument type: " + field.getKey());
      }
    }
    for (JsonNode field : schema.path("required")) {
      if (argument(arguments, field.asText()).isEmpty()) {
        throw new McpToolInputException(field.asText() + " is required");
      }
    }
  }

  private String createTask(McpAuthenticationContext context, JsonNode args) {
    ProjectDetail project =
        McpToolReferences.resolveProject(
            projects.listDetailsByWorkspaceId(context.workspaceId()), argument(args, "project"));
    UUID milestoneId = resolveMilestone(context, project.id(), argument(args, "milestone"));
    TaskSnapshot task =
        taskWriter.create(
            new CreateTaskCommand(
                project.id(),
                context.workspaceId(),
                argument(args, "title"),
                emptyToNull(argument(args, "description")),
                taskStatus(argument(args, "status")),
                null,
                taskPriority(argument(args, "priority")),
                milestoneId,
                resolveAssignee(context, argument(args, "assignee")),
                date(argument(args, "due_date"), "due_date"),
                TaskOrigin.MANUAL,
                null));
    return McpWriteToolText.createdTask(task, project, milestoneName(context, task.milestoneId()));
  }

  private String updateTask(McpAuthenticationContext context, JsonNode args) {
    TaskSnapshot current = findTask(context, argument(args, "task"));
    String status = argument(args, "status");
    String priority = argument(args, "priority");
    String milestone = argument(args, "milestone");
    String assignee = argument(args, "assignee");
    TaskSnapshot task =
        taskWriter.patch(
            new PatchTaskCommand(
                current.id(),
                argument(args, "title"),
                !argument(args, "title").isEmpty(),
                emptyToNull(argument(args, "description")),
                args.has("description"),
                status.isEmpty() ? null : taskStatus(status),
                !status.isEmpty(),
                priority.isEmpty() ? null : taskPriority(priority),
                !priority.isEmpty(),
                resolveMilestone(context, current.projectId(), milestone),
                !milestone.isEmpty(),
                resolveAssignee(context, assignee),
                !assignee.isEmpty(),
                date(argument(args, "due_date"), "due_date"),
                args.has("due_date")));
    return McpWriteToolText.updatedTask(task, milestoneName(context, task.milestoneId()));
  }

  private String createComment(McpAuthenticationContext context, JsonNode args) {
    TaskSnapshot task = findTask(context, argument(args, "task"));
    updates.create(
        new CreateTaskUpdateCommand(
            task.id(),
            context.workspaceId(),
            task.projectId(),
            context.userId(),
            argument(args, "body"),
            TaskUpdateKind.COMMENT,
            null));
    return "Added a comment to " + McpWriteToolText.label(task) + ".";
  }

  private String createMilestone(McpAuthenticationContext context, JsonNode args) {
    ProjectDetail project =
        McpToolReferences.resolveProject(
            projects.listDetailsByWorkspaceId(context.workspaceId()), argument(args, "project"));
    String health = argument(args, "health_status");
    MilestoneHealthStatus healthStatus =
        health.isEmpty()
            ? null
            : MilestoneHealthStatus.from(health)
                .orElseThrow(() -> new McpToolInputException("Invalid health_status"));
    MilestoneDetail milestone =
        milestoneWriter.create(
            new CreateMilestoneCommand(
                project.id(),
                context.workspaceId(),
                context.userId(),
                argument(args, "name"),
                argument(args, "description"),
                date(argument(args, "target_date"), "target_date"),
                healthStatus,
                progress(args),
                argument(args, "summary"),
                null,
                List.of()));
    return McpWriteToolText.createdMilestone(milestone, project);
  }

  private String updateMilestone(McpAuthenticationContext context, JsonNode args) {
    MilestoneDetail current = findMilestone(context, argument(args, "milestone"));
    MilestoneDetail milestone =
        milestoneWriter.update(
            new UpdateMilestoneCommand(
                current.id(),
                argument(args, "name"),
                argument(args, "description"),
                argument(args, "status"),
                date(argument(args, "target_date"), "target_date"),
                argument(args, "health_status"),
                progress(args),
                argument(args, "summary")));
    return McpWriteToolText.updatedMilestone(milestone);
  }

  private String deleteMilestone(McpAuthenticationContext context, String reference) {
    MilestoneDetail milestone = findMilestone(context, reference);
    milestoneWriter.delete(milestone.id());
    return "Removed milestone " + milestone.name() + " (" + milestone.id() + ").";
  }

  private TaskSnapshot findTask(McpAuthenticationContext context, String reference) {
    Optional<UUID> id = McpToolReferences.uuid(reference);
    TaskSnapshot task =
        (id.isPresent()
                ? tasks.findSnapshotInWorkspace(context.workspaceId(), id.get())
                : tasks.findSnapshotByLabel(
                    context.workspaceId(), reference.toUpperCase(Locale.ROOT)))
            .orElseThrow(
                () -> new McpToolInputException("No task in this workspace: " + reference));
    requireProject(context, task.projectId());
    return task;
  }

  private void requireProject(McpAuthenticationContext context, UUID projectId) {
    if (projects.listDetailsByWorkspaceId(context.workspaceId()).stream()
        .noneMatch(project -> project.id().equals(projectId))) {
      throw new McpToolInputException("Resource not found in this workspace.");
    }
  }

  private MilestoneDetail findMilestone(McpAuthenticationContext context, String reference) {
    MilestoneDetail milestone =
        McpToolReferences.resolveMilestone(
            milestones.listDetailsByWorkspaceId(context.workspaceId()), reference);
    requireProject(context, milestone.projectId());
    return milestone;
  }

  private UUID resolveMilestone(
      McpAuthenticationContext context, UUID projectId, String reference) {
    return switch (reference.toLowerCase(Locale.ROOT)) {
      case "", "none", "remove", "unassign", "unassigned" -> null;
      default ->
          McpToolReferences.resolveMilestone(
                  milestones.listDetailsByWorkspaceId(context.workspaceId()).stream()
                      .filter(milestone -> milestone.projectId().equals(projectId))
                      .toList(),
                  reference)
              .id();
    };
  }

  private String milestoneName(McpAuthenticationContext context, UUID id) {
    return id == null
        ? ""
        : milestones.listDetailsByWorkspaceId(context.workspaceId()).stream()
            .filter(milestone -> milestone.id().equals(id))
            .map(MilestoneDetail::name)
            .findFirst()
            .orElse("");
  }

  private UUID resolveAssignee(McpAuthenticationContext context, String reference) {
    return switch (reference.toLowerCase(Locale.ROOT)) {
      case "", "none", "unassign", "unassigned" -> null;
      case "me" -> context.userId();
      default ->
          McpToolReferences.resolveAssignee(
              users.getProfiles(
                  memberships.listMembershipDetails(context.workspaceId()).stream()
                      .map(member -> member.userId())
                      .toList()),
              reference);
    };
  }

  private static TaskStatus taskStatus(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    if (normalized.equals("progress") || normalized.equals("in-progress")) {
      normalized = TaskStatus.IN_PROGRESS.value();
    }
    return normalized.isEmpty()
        ? TaskStatus.BACKLOG
        : TaskStatus.from(normalized)
            .orElseThrow(() -> new McpToolInputException("Invalid status"));
  }

  private static TaskPriority taskPriority(String value) {
    String normalized = value.toLowerCase(Locale.ROOT);
    if (normalized.equals("med")) {
      normalized = TaskPriority.MEDIUM.value();
    }
    return normalized.isEmpty()
        ? TaskPriority.MEDIUM
        : TaskPriority.from(normalized)
            .orElseThrow(() -> new McpToolInputException("Invalid priority"));
  }

  private static Integer progress(JsonNode args) {
    if (!args.has("progress")) {
      return null;
    }
    int value = args.get("progress").intValue();
    if (value < 0 || value > 100) {
      throw new McpToolInputException("progress must be between 0 and 100");
    }
    return value;
  }

  private static LocalDate date(String value, String field) {
    if (value.isEmpty()) {
      return null;
    }
    try {
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        throw new DateTimeParseException("Invalid date format", value, 0);
      }
      return LocalDate.parse(value);
    } catch (DateTimeParseException exception) {
      throw new McpToolInputException("could not parse " + field + ", want YYYY-MM-DD");
    }
  }

  private static String argument(JsonNode args, String name) {
    return args.path(name).asText("").strip();
  }

  private static String emptyToNull(String value) {
    return value.isEmpty() ? null : value;
  }
}
