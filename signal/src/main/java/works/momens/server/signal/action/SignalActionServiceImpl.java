package works.momens.server.signal.action;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
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
  private final SignalTaskDraftGenerator taskDraftGenerator;

  @Override
  public SignalActionResult convertToTask(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), SignalActionType.CONVERT_TO_TASK);
    }

    TaskDraft draft = generateDraft(signal);
    try {
      return executor.convert(
          signal, userId, draft.title(), draft.role().value(), draft.priority().value());
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

  /**
   * task draft를 만든다(MOM-0804). evidence 조회와 모델 호출은 신규 convert에서만 수행하고, replay·충돌·dismiss 경로는 여기까지
   * 오지 않는다. 외부 LLM 호출이 쓰기 트랜잭션과 DB connection을 점유하지 않도록 {@link SignalActionExecutor} 밖에서 호출한다.
   * generator는 실패를 전파하지 않고 항상 유효한 고정 fallback draft를 반환하므로 이 경로에 별도 예외 처리가 없다.
   */
  private TaskDraft generateDraft(SignalReader.Snapshot signal) {
    List<SignalTaskDraftInput.Evidence> evidence =
        signalReader.findDraftEvidence(signal.id()).stream()
            .map(
                item ->
                    new SignalTaskDraftInput.Evidence(item.target(), item.change(), item.impact()))
            .toList();
    return taskDraftGenerator.generate(
        new SignalTaskDraftInput(
            signal.title(), signal.type(), signal.description(), signal.impact(), evidence));
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
