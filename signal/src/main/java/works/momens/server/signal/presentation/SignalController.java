package works.momens.server.signal.presentation;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.signal.SignalDetailService;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.presentation.dto.response.SignalDetailResponse;
import works.momens.server.signal.presentation.dto.response.SignalListResponse;

/**
 * 모바일 Signal 조회 엔드포인트.
 *
 * <p>경로는 {@code /api/mobile/*}이지만 Signal 도메인 정책·영속성은 mobile이 아니라 signal 모듈이 소유합니다(ADR-0007). {@code
 * /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link CurrentUser#id(Principal)}로 읽습니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mobile")
class SignalController implements SignalControllerDocs {

  private final SignalListService signalListService;
  private final SignalDetailService signalDetailService;

  @Override
  @GetMapping(path = "/projects/{projectId}/signals", version = "1")
  public SignalListResponse listSignals(@PathVariable UUID projectId, Principal principal) {
    return SignalListResponse.from(
        signalListService.listUnprocessed(projectId, CurrentUser.id(principal)));
  }

  @Override
  @GetMapping(path = "/signals/{signalId}", version = "1")
  public SignalDetailResponse getSignal(@PathVariable UUID signalId, Principal principal) {
    return SignalDetailResponse.from(
        signalDetailService.getDetail(signalId, CurrentUser.id(principal)));
  }
}
