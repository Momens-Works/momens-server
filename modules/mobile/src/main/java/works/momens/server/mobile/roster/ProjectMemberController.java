package works.momens.server.mobile.roster;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.roster.dto.response.ProjectMembersResponse;

/**
 * 모바일 프로젝트 멤버 조회 엔드포인트.
 *
 * <p>{@code /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link
 * CurrentUser#id(Principal)}로 읽습니다(docs/rules/code-conventions.md 보호 API).
 */
@RestController
@RequiredArgsConstructor
class ProjectMemberController implements ProjectMemberControllerDocs {

  private final ProjectMemberService projectMemberService;

  @Override
  @GetMapping(path = "/api/mobile/projects/{projectId}/members", version = "1")
  public ProjectMembersResponse listMembers(
      @PathVariable UUID projectId,
      @RequestParam(required = false) String query,
      Principal principal) {
    return ProjectMembersResponse.from(
        projectMemberService.list(projectId, CurrentUser.id(principal), query));
  }
}
