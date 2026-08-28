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
import works.momens.server.web.project.dto.request.CreateMilestoneRequest;
import works.momens.server.web.project.dto.response.WebMilestoneResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class MilestoneController implements MilestoneControllerDocs {

  private final MilestoneService milestoneService;

  @PostMapping(path = "/projects/{projectId}/milestones", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public WebMilestoneResponse createMilestone(
      @PathVariable UUID projectId,
      @RequestBody CreateMilestoneRequest request,
      Principal principal) {
    return WebMilestoneResponse.from(
        milestoneService.create(projectId, CurrentUser.id(principal), request));
  }
}
