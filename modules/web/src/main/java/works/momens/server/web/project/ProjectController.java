package works.momens.server.web.project;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.web.project.dto.request.CreateProjectRequest;
import works.momens.server.web.project.dto.response.WebProjectResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class ProjectController implements ProjectControllerDocs {

  private final ProjectService projectService;

  @PostMapping(path = "/workspaces/{workspaceId}/projects", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public WebProjectResponse createProject(
      @PathVariable UUID workspaceId,
      @RequestBody CreateProjectRequest request,
      Principal principal) {
    return WebProjectResponse.from(
        projectService.create(workspaceId, CurrentUser.id(principal), request));
  }
}
