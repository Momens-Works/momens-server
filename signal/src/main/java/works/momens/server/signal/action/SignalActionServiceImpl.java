package works.momens.server.signal.action;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.minsu.Minsu;
import works.momens.server.minsu.MinsuSignalContext;
import works.momens.server.minsu.MinsuTaskDraft;
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

  private final SignalReader signalReader;
  private final WorkspaceAccess workspaceAccess;
  private final SignalActionRepository signalActionRepository;
  private final SignalActionExecutor executor;
  private final TaskReader taskReader;
  private final Minsu minsu;

  @Override
  public SignalActionResult convertToTask(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), SignalActionType.CONVERT_TO_TASK);
    }

    // draft(title·role·priority)는 민수가 Signal 컨텍스트(제목·근거)로 생성한다(ADR-0011, MOM-0692). 최초
    // 변환에서만 호출하고 재요청은 멱등 replay라 다시 생성하지 않는다. 민수는 하드 의존이라 실패 시 변환도 실패한다.
    MinsuTaskDraft draft = minsu.draftTask(buildContext(signal, signalId));
    try {
      return executor.convert(signal, userId, draft.title(), draft.role(), draft.priority());
    } catch (DataIntegrityViolationException raced) {
      return replayOrConflict(
          signalActionRepository.findBySignalId(signalId).orElseThrow(() -> raced),
          SignalActionType.CONVERT_TO_TASK);
    }
  }

  private MinsuSignalContext buildContext(SignalReader.Snapshot signal, UUID signalId) {
    return new MinsuSignalContext(
        signal.title(),
        signalReader.readEvidence(signalId).stream()
            .map(e -> new MinsuSignalContext.Evidence(e.target(), e.change(), e.impact()))
            .toList());
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
