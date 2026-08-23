package works.momens.server.web.task;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.web.dto.response.WebMessageResponse;
import works.momens.server.web.task.dto.request.CreateTaskSourceRefRequest;
import works.momens.server.web.task.dto.response.CreateTaskSourceRefResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class TaskLinkController implements TaskLinkControllerDocs {

  private final TaskLinkService taskLinkService;

  @Override
  @PostMapping(path = "/tasks/{taskId}/memories/{memoryId}", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public WebMessageResponse linkTaskMemory(
      @PathVariable UUID taskId, @PathVariable UUID memoryId, Principal principal) {
    taskLinkService.linkMemory(taskId, memoryId, CurrentUser.id(principal));
    return new WebMessageResponse("linked");
  }

  @Override
  @DeleteMapping(path = "/tasks/{taskId}/memories/{memoryId}", version = "1")
  public WebMessageResponse unlinkTaskMemory(
      @PathVariable UUID taskId, @PathVariable UUID memoryId, Principal principal) {
    taskLinkService.unlinkMemory(taskId, memoryId, CurrentUser.id(principal));
    return new WebMessageResponse("unlinked");
  }

  @Override
  @PostMapping(path = "/tasks/{taskId}/source-refs", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateTaskSourceRefResponse createTaskSourceRef(
      @PathVariable UUID taskId,
      @Valid @RequestBody CreateTaskSourceRefRequest request,
      Principal principal) {
    UUID sourceRefId =
        taskLinkService.createSourceRef(
            taskId,
            CurrentUser.id(principal),
            request.sourceUrl(),
            request.sourceType(),
            request.title());
    return new CreateTaskSourceRefResponse(sourceRefId, "linked");
  }

  @Override
  @DeleteMapping(path = "/tasks/{taskId}/source-refs/{sourceRefId}", version = "1")
  public WebMessageResponse unlinkTaskSourceRef(
      @PathVariable UUID taskId, @PathVariable UUID sourceRefId, Principal principal) {
    taskLinkService.unlinkSourceRef(taskId, sourceRefId, CurrentUser.id(principal));
    return new WebMessageResponse("unlinked");
  }
}
