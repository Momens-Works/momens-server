package works.momens.server.web.workspace;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.web.workspace.dto.request.AcceptInvitationRequest;
import works.momens.server.web.workspace.dto.response.AcceptInvitationResponse;

/**
 * 웹에서 사용하는 초대 수락 엔드포인트입니다.
 *
 * <p>워크스페이스 하위가 아닌 별도 경로로 제공합니다. 초대를 수락하기 전에는 해당 워크스페이스의 멤버가 아니므로 워크스페이스 권한을 기준으로 접근을 제어할 수 없습니다.
 * 따라서 로그인 여부만 확인합니다.
 */
@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
class InvitationAcceptController implements InvitationAcceptControllerDocs {

  private final InvitationAcceptService invitationAcceptService;

  @Override
  @PostMapping(path = "/accept", version = "1")
  public AcceptInvitationResponse accept(
      @RequestBody AcceptInvitationRequest request, Principal principal) {
    return invitationAcceptService.accept(CurrentUser.id(principal), request.token());
  }
}
