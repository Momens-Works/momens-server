package works.momens.server.mobile.bootstrap;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.mobile.bootstrap.dto.response.BootstrapResponse;

/**
 * 모바일 진입 엔드포인트.
 *
 * <p>{@code /api/mobile/*}는 보호 체인의 기본 인증 대상이라 별도 보안 설정이 없고, 현재 사용자는 {@link
 * CurrentUser#id(Principal)}로 읽습니다(docs/rules/code-conventions.md 보호 API).
 */
@RestController
@RequiredArgsConstructor
class BootstrapController implements BootstrapControllerDocs {

  private final BootstrapService bootstrapService;

  @Override
  @GetMapping(path = "/api/mobile/bootstrap", version = "1")
  public BootstrapResponse getBootstrap(Principal principal) {
    return BootstrapResponse.from(bootstrapService.load(CurrentUser.id(principal)));
  }
}
