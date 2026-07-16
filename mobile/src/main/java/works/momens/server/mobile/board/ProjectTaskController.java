package works.momens.server.mobile.board;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.board.dto.request.CreateTaskRequest;
import works.momens.server.mobile.board.dto.response.TaskBoardResponse;
import works.momens.server.mobile.board.dto.response.TaskCreateResponse;

/**
 * 모바일 프로젝트 태스크 보드 조회와 생성 엔드포인트.
 *
 * <p>{@code /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link
 * CurrentUser#id(Principal)}로 읽습니다(docs/rules/code-conventions.md 보호 API).
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
class ProjectTaskController implements ProjectTaskControllerDocs {

  private final ProjectTaskService projectTaskService;

  @Override
  @GetMapping(path = "/projects/{projectId}/tasks", version = "1")
  public TaskBoardResponse getBoard(@PathVariable UUID projectId, Principal principal) {
    return TaskBoardResponse.from(
        projectTaskService.getBoard(projectId, CurrentUser.id(principal)));
  }

  @Override
  @PostMapping(path = "/projects/{projectId}/tasks", version = "1")
  @ResponseStatus(HttpStatus.CREATED)
  public TaskCreateResponse createTask(
      @PathVariable UUID projectId,
      @Valid @RequestBody CreateTaskRequest request,
      Principal principal) {
    return TaskCreateResponse.from(
        projectTaskService.createTask(
            projectId,
            CurrentUser.id(principal),
            request.title(),
            request.role(),
            request.priority()));
  }
}
