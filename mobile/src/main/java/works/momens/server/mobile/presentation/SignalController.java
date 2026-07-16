package works.momens.server.mobile.presentation;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.presentation.dto.response.ConvertToTaskResponse;
import works.momens.server.mobile.presentation.dto.response.DismissResponse;
import works.momens.server.mobile.presentation.dto.response.SignalDetailResponse;
import works.momens.server.mobile.presentation.dto.response.SignalListResponse;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalActionService;
import works.momens.server.signal.SignalDetailService;
import works.momens.server.signal.SignalListService;

/**
 * 모바일 Signal 조회·action 엔드포인트.
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
  private final SignalActionService signalActionService;

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

  @Override
  @PostMapping(path = "/signals/{signalId}/actions/convert-to-task", version = "1")
  public ResponseEntity<ConvertToTaskResponse> convertToTask(
      @PathVariable UUID signalId, Principal principal) {
    SignalActionResult result =
        signalActionService.convertToTask(signalId, CurrentUser.id(principal));
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ConvertToTaskResponse.from(result));
  }

  @Override
  @PostMapping(path = "/signals/{signalId}/actions/dismiss", version = "1")
  public DismissResponse dismiss(@PathVariable UUID signalId, Principal principal) {
    SignalActionResult result = signalActionService.dismiss(signalId, CurrentUser.id(principal));
    return DismissResponse.from(result);
  }
}
