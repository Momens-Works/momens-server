package works.momens.server.source.presentation;

import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.source.CompleteInstallCommand;
import works.momens.server.source.CompletedInstall;
import works.momens.server.source.SourceInstaller;
import works.momens.server.source.presentation.dto.response.SourceOAuthCallbackResponse;

/**
 * provider가 승인 결과와 함께 호출하는 OAuth 콜백 endpoint입니다.
 *
 * <p>웹 클라이언트가 호출하는 경로가 아니므로 {@code :web} 모듈이 아닌 이 모듈에서 소유합니다.
 */
@RestController
@RequestMapping("/api/source-connections/oauth")
@RequiredArgsConstructor
class SourceOAuthCallbackController implements SourceOAuthCallbackControllerDocs {

  private final SourceInstaller sourceInstaller;

  @Override
  @GetMapping(path = "/callback", version = "1")
  public ResponseEntity<SourceOAuthCallbackResponse> callback(
      @RequestParam(name = "code", required = false) String code,
      @RequestParam(name = "state", required = false) String state) {
    CompletedInstall completed =
        sourceInstaller.completeInstall(new CompleteInstallCommand(code, state));
    if (completed.successRedirectUri() != null && !completed.successRedirectUri().isBlank()) {
      return ResponseEntity.status(302)
          .location(URI.create(completed.successRedirectUri()))
          .build();
    }
    return ResponseEntity.ok(SourceOAuthCallbackResponse.from(completed.connection()));
  }
}
