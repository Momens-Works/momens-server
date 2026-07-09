package works.momens.server.mobile.presentation;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.internal.ProjectBriefService;
import works.momens.server.mobile.presentation.dto.response.BriefResponse;

/**
 * 모바일 프로젝트 브리프 조회 엔드포인트.
 *
 * <p>{@code /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link
 * CurrentUser#id(Principal)}로 읽습니다(docs/rules/code-conventions.md 보호 API).
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
class ProjectBriefController implements ProjectBriefControllerDocs {

  private final ProjectBriefService projectBriefService;

  @Override
  @GetMapping(path = "/projects/{projectId}/brief", version = "1")
  public BriefResponse getBrief(@PathVariable UUID projectId, Principal principal) {
    return BriefResponse.from(projectBriefService.getBrief(projectId, CurrentUser.id(principal)));
  }
}
