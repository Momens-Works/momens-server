package works.momens.server.web.task;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.web.task.dto.response.TaskContextResponse;
import works.momens.server.web.task.dto.response.TaskUpdateListResponse;
import works.momens.server.web.task.dto.response.WebTaskListResponse;
import works.momens.server.web.task.dto.response.WebTaskResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class TaskReadController implements TaskReadControllerDocs {
  private final TaskReadService taskReadService;

  @Override
  @GetMapping(path = "/projects/{projectId}/tasks", version = "1")
  public WebTaskListResponse list(@PathVariable UUID projectId, Principal principal) {
    return WebTaskListResponse.from(taskReadService.list(projectId, CurrentUser.id(principal)));
  }

  @Override
  @GetMapping(path = "/tasks/{taskId}", version = "1")
  public WebTaskResponse get(@PathVariable UUID taskId, Principal principal) {
    return WebTaskResponse.from(taskReadService.get(taskId, CurrentUser.id(principal)));
  }

  @Override
  @GetMapping(path = "/tasks/{taskId}/updates", version = "1")
  public TaskUpdateListResponse updates(@PathVariable UUID taskId, Principal principal) {
    return TaskUpdateListResponse.from(taskReadService.updates(taskId, CurrentUser.id(principal)));
  }

  @Override
  @GetMapping(path = "/tasks/{taskId}/context", version = "1")
  public TaskContextResponse context(@PathVariable UUID taskId, Principal principal) {
    TaskReadService.TaskContext context =
        taskReadService.context(taskId, CurrentUser.id(principal));
    return TaskContextResponse.from(context.taskId(), context.memories(), context.sourceRefs());
  }
}
