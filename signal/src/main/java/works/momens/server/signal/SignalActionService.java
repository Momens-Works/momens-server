package works.momens.server.signal;

import java.util.UUID;

/**
 * Signal action(convert-to-task, dismiss) 공개 API.
 *
 * <p>같은 Signal에 같은 action을 재요청하면 새로 처리하지 않고 기존 결과를 반환한다({@code created=false}). 이미 다른 action으로 처리된
 * Signal에 다른 action을 요청하면 {@link SignalErrorCode#SIGNAL_INVALID_STATE}를 던진다.
 */
public interface SignalActionService {

  SignalActionResult convertToTask(UUID signalId, UUID userId, ConvertToTaskCommand command);

  SignalActionResult dismiss(UUID signalId, UUID userId);
}
