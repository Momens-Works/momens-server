package works.momens.server.signal.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.signal.SignalActionResult;

/**
 * {@code POST /api/mobile/signals/{signalId}/actions/dismiss} 응답. shape는 docs/spec/mobile-api.md의
 * 시그널 action 절을 따릅니다. 새로 처리·멱등 replay 모두 200이며 body shape는 같습니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "dismiss 처리 결과")
public record DismissResponse(Signal signal) {

  public record Signal(UUID id, String action) {}

  public static DismissResponse from(SignalActionResult result) {
    return new DismissResponse(new Signal(result.signalId(), result.actionType()));
  }
}
