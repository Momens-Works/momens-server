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
 * {@link DataIntegrityViolationException}을 잡아 재조회 후 replay/충돌로 되돌린다.
 */
@Service
@RequiredArgsConstructor
class SignalActionServiceImpl implements SignalActionService {

  private static final String CONVERT_TO_TASK = "convert_to_task";
  private static final String DISMISS = "dismiss";
  private static final String DEFAULT_PRIORITY = "medium";

  private final SignalReader signalReader;
  private final WorkspaceAccess workspaceAccess;
  private final SignalActionRepository signalActionRepository;
  private final SignalActionExecutor executor;
  private final TaskReader taskReader;

  @Override
  public SignalActionResult convertToTask(
      UUID signalId, UUID userId, SignalActionService.ConvertToTaskCommand command) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), CONVERT_TO_TASK);
    }

    String title = command.title() != null ? command.title() : signal.title();
    String priority = command.priority() != null ? command.priority() : DEFAULT_PRIORITY;
    String role = command.role();
    if (role == null) {
      throw new BusinessException(
          CommonErrorCode.COMMON_VALIDATION_FAILED, Map.of("field", "role"));
    }

    try {
      return executor.convert(signal, userId, title, role, priority);
    } catch (DataIntegrityViolationException raced) {
      return replayOrConflict(
          signalActionRepository.findBySignalId(signalId).orElseThrow(() -> raced),
          CONVERT_TO_TASK);
    }
  }

  @Override
  public SignalActionResult dismiss(UUID signalId, UUID userId) {
    SignalReader.Snapshot signal = loadAuthorized(signalId, userId);
    Optional<SignalAction> existing = signalActionRepository.findBySignalId(signalId);
    if (existing.isPresent()) {
      return replayOrConflict(existing.get(), DISMISS);
    }

    try {
      return executor.dismiss(signal, userId);
    } catch (DataIntegrityViolationException raced) {
      return replayOrConflict(
          signalActionRepository.findBySignalId(signalId).orElseThrow(() -> raced), DISMISS);
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

  private SignalActionResult replayOrConflict(SignalAction existing, String requestedActionType) {
    if (!existing.getActionType().equals(requestedActionType)) {
      throw new BusinessException(
          SignalErrorCode.SIGNAL_INVALID_STATE,
          Map.of(
              "signal_id", existing.getSignalId().toString(),
              "processed_action", existing.getActionType()));
    }
    if (CONVERT_TO_TASK.equals(requestedActionType)) {
      TaskDetail detail = taskReader.findDetail(existing.getResultTaskId()).orElseThrow();
      return new SignalActionResult(
          existing.getSignalId(),
          CONVERT_TO_TASK,
          false,
          new SignalActionResult.TaskResult(detail.id(), detail.title(), detail.status()));
    }
    return new SignalActionResult(existing.getSignalId(), DISMISS, false, null);
  }
}
