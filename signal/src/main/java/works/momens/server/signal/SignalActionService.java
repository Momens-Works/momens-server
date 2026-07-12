package works.momens.server.signal;

import java.util.UUID;

/**
 * Signal action(convert-to-task, dismiss) 공개 API.
 *
 * <p>같은 Signal에 같은 action을 재요청하면 새로 처리하지 않고 기존 결과를 반환한다({@code created=false}). 이미 다른 action으로 처리된
 * Signal에 다른 action을 요청하면 {@link SignalErrorCode#SIGNAL_INVALID_STATE}를 던진다.
 *
 * <p>convert-to-task는 요청 body 없는 원탭 action이다(ADR-0011). task draft(title·role·priority)는 민수 산출물이며,
 * 민수 구현 전 MVP에서는 구현체가 고정 목 draft를 쓴다.
 */
public interface SignalActionService {

  SignalActionResult convertToTask(UUID signalId, UUID userId);

  SignalActionResult dismiss(UUID signalId, UUID userId);
}
