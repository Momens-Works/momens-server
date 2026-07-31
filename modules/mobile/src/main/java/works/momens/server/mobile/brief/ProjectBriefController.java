package works.momens.server.mobile.brief;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.brief.dto.response.BriefResponse;
import works.momens.server.mobile.brief.dto.response.BriefSignalSummaryPageResponse;

/**
 * 모바일 프로젝트 브리프 조회 엔드포인트. 브리프 본체는 초기 화면 로드 전용이고, 시그널 요약의 필터 전환과 더보기 페이지 이동은 하위 엔드포인트가
 * 담당합니다(docs/spec/mobile-api.md 브리프 절).
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

  @Override
  @GetMapping(path = "/projects/{projectId}/brief/signal-summary", version = "1")
  public BriefSignalSummaryPageResponse getSignalSummaryPage(
      @PathVariable UUID projectId,
      @RequestParam(name = "filter", defaultValue = "all") String filter,
      @RequestParam(name = "cursor", required = false) String cursor,
      @RequestParam(name = "limit", required = false) Integer limit,
      Principal principal) {
    return BriefSignalSummaryPageResponse.from(
        projectBriefService.getSignalSummaryPage(
            projectId, CurrentUser.id(principal), filter, cursor, limit));
  }
}
