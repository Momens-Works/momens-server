package works.momens.server.signal.action;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;
import works.momens.server.signal.SignalActionResult;
import works.momens.server.signal.SignalActionService;
import works.momens.server.signal.SignalErrorCode;
import works.momens.server.signal.SignalReader;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal action 멱등·충돌 정책 facade.
 *
 * <p>실제 원자 쓰기는 {@link SignalActionExecutor}에 위임한다({@code @Transactional} 프록시가 걸리도록 빈을 분리). ledger
 * {@code UNIQUE(signal_id)}로 인한 동시성 레이스(같은 Signal에 대한 두 요청이 동시에 처리 없음을 확인하고 둘 다 insert를 시도하는 경우)는
 * {@link DataIntegrityViolationException}을 잡아 재조회 후 replay/충돌로 되돌린다. {@code action_type} 문자열은
 * {@link SignalActionType}이 유일한 출처다.
 */
@Service
@RequiredArgsConstructor
class SignalActionServiceImpl implements SignalActionService {

  /**
   * convert-to-task 고정 목 draft(ADR-0011). task draft(title·role·priority)는 민수 산출물이며, 민수가 서버 모듈로
   * 구현되기 전까지 서버가 이 고정 값으로 task를 만든다. role·priority는 클라이언트가 보내지 않고, title은 Signal 제목을 쓴다. 고정 값이라 같은
   * Signal 요청은 결정적이다. 실제 민수 연동은 MOM-0691·MOM-0692에서 이 상수를 대체한다.
   */
  private static final String MOCK_DRAFT_ROLE = "pm";

  private static final String MOCK_DRAFT_PRIORITY = "medium";

  private final SignalReader signalReader;
  private final WorkspaceAccess workspaceAccess;
  private final SignalActionRepository signalActionRepository;
  private final SignalActionExecutor executor;
  private final TaskReader taskReader;

  @Override
  public SignalActionResult convertToTask(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), SignalActionType.CONVERT_TO_TASK);
    }

    try {
      return executor.convert(signal, userId, signal.title(), MOCK_DRAFT_ROLE, MOCK_DRAFT_PRIORITY);
    } catch (DataIntegrityViolationException raced) {
      return replayOrConflict(
          signalActionRepository.findBySignalId(signalId).orElseThrow(() -> raced),
          SignalActionType.CONVERT_TO_TASK);
    }
  }

  @Override
  public SignalActionResult dismiss(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), SignalActionType.DISMISS);
    }

    try {
      return executor.dismiss(signal, userId);
    } catch (DataIntegrityViolationException raced) {
      return replayOrConflict(
          signalActionRepository.findBySignalId(signalId).orElseThrow(() -> raced),
          SignalActionType.DISMISS);
    }
  }

  private SignalReader.Snapshot loadAuthorized(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal =
        signalReader
            .findLive(signalId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        SignalErrorCode.SIGNAL_NOT_FOUND,
                        Map.of("signal_id", signalId.toString())));
    if (!workspaceAccess.isMember(signal.workspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("signal_id", signalId.toString()));
    }
    return signal;
  }

  private SignalActionResult replayOrConflict(
      SignalAction existing, SignalActionType requestedActionType) {
    if (!existing.getActionType().equals(requestedActionType.value())) {
      throw new BusinessException(
          SignalErrorCode.SIGNAL_INVALID_STATE,
          Map.of(
              "signal_id", existing.getSignalId().toString(),
              "processed_action", existing.getActionType()));
    }
    if (requestedActionType == SignalActionType.CONVERT_TO_TASK) {
      TaskDetail detail =
          taskReader
              .findDetail(existing.getResultTaskId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "signal_actions.result_task_id가 존재하지 않는 task를 가리킴: signal_id="
                              + existing.getSignalId()
                              + ", result_task_id="
                              + existing.getResultTaskId()));
      return new SignalActionResult(
          existing.getSignalId(),
          SignalActionType.CONVERT_TO_TASK.value(),
          false,
          new SignalActionResult.TaskResult(detail.id(), detail.title(), detail.status()));
    }
    return new SignalActionResult(
        existing.getSignalId(), SignalActionType.DISMISS.value(), false, null);
  }
}
