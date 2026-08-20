package works.momens.server.web.source;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import works.momens.server.common.api.CurrentUser;
import works.momens.server.web.source.dto.response.SourceRefResponse;

/**
 * 웹 source-ref endpoint입니다.
 *
 * <p>검증 결과는 레거시와 동일하게 별도 객체로 감싸지 않고 source-ref 전체 필드를 응답합니다.
 */
@RestController
@RequestMapping("/api/source-refs")
@RequiredArgsConstructor
class SourceRefController implements SourceRefControllerDocs {

  private final SourceRefService sourceRefService;

  @Override
  @PostMapping(path = "/{sourceRefId}/verify", version = "1")
  public SourceRefResponse verify(@PathVariable UUID sourceRefId, Principal principal) {
    return SourceRefResponse.from(sourceRefService.verify(sourceRefId, CurrentUser.id(principal)));
  }
}
