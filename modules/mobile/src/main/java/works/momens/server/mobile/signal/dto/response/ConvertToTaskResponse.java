package works.momens.server.mobile.signal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import works.momens.server.signal.SignalActionResult;

/**
 * {@code POST /api/mobile/signals/{signalId}/actions/convert-to-task} 응답. shape는
 * docs/spec/mobile-api.md의 시그널 action 절을 따릅니다.
 *
 * <p>새로 처리됐으면 201, 같은 action 재요청(멱등 replay)이면 200으로 응답하되 body shape는 같습니다. {@code signal.action}은
 * ledger 값 그대로 underscore 표기({@code convert_to_task})입니다.
 */
@Schema(description = "convert-to-task 처리 결과")
public record ConvertToTaskResponse(Task task, Signal signal) {

  public record Task(UUID id, String title, String status) {}

  public record Signal(UUID id, String action) {}

  public static ConvertToTaskResponse from(SignalActionResult result) {
    SignalActionResult.TaskResult task = result.task();
    return new ConvertToTaskResponse(
        new Task(task.id(), task.title(), task.status()),
        new Signal(result.signalId(), result.actionType()));
  }
}
