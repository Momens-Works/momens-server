package works.momens.server.minsu.internal.generation;

import io.micrometer.observation.Observation;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.PreparedTaskDraft;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.internal.config.MinsuAsyncProperties;
import works.momens.server.minsu.internal.config.MinsuConfigStatus;
import works.momens.server.minsu.internal.json.MinsuJson;
import works.momens.server.minsu.internal.ledger.TaskDraftGenerationEnroller;
import works.momens.server.minsu.internal.llm.LlmClient;
import works.momens.server.minsu.internal.llm.LlmRequest;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.LlmTimeoutException;
import works.momens.server.minsu.internal.llm.LlmUseCase;
import works.momens.server.minsu.internal.llm.ModelSelection;
import works.momens.server.minsu.internal.llm.ModelSelectionPolicy;
import works.momens.server.minsu.internal.prompt.SignalTaskDraftPrompt;

/**
 * Minsu 공개 계약 구현.
 *
 * <p>비동기 활성 판정을 이 클래스가 소유한다(5.5절). 판정은 {@code prepare}에서 한 번만 하고 그 결과를 {@link PreparedTaskDraft}에
 * 담아 내보내므로, 한 요청 안에서 판정이 두 번 갈릴 수 없다.
 */
@Slf4j
@Component
final class DefaultSignalTaskDraftGenerator implements SignalTaskDraftGenerator {

  private final MinsuConfigStatus configStatus;
  private final MinsuAsyncProperties asyncProperties;
  private final TaskDraftGenerationEnroller enroller;
  private final ModelSelectionPolicy selectionPolicy;
  private final LlmClient llmClient;
  private final SignalTaskDraftPrompt prompt;
  private final MinsuJson json;
  private final MinsuObservability observability;

  DefaultSignalTaskDraftGenerator(
      MinsuConfigStatus configStatus,
      MinsuAsyncProperties asyncProperties,
      TaskDraftGenerationEnroller enroller,
      ModelSelectionPolicy selectionPolicy,
      LlmClient llmClient,
      SignalTaskDraftPrompt prompt,
      MinsuJson json,
      MinsuObservability observability) {
    this.configStatus = configStatus;
    this.asyncProperties = asyncProperties;
    this.enroller = enroller;
    this.selectionPolicy = selectionPolicy;
    this.llmClient = llmClient;
    this.prompt = prompt;
    this.json = json;
    this.observability = observability;
  }

  @Override
  public PreparedTaskDraft prepare(SignalTaskDraftInput input) {
    TaskDraft fallback = fallback(input);
    if (!configStatus.enabled()) {
      // provider가 꺼져 있으면 enroll 값과 무관하게 적재하지 않는다(11.2절). 생성할 수단이 없는 원장은
      // deadline까지 generating으로 남았다 닫힐 뿐이다.
      return synchronous(finish(fallback, GenerationOutcome.DISABLED));
    }
    if (asyncProperties.enroll()) {
      // 비동기 활성 경로. 여기서 LLM을 부르면 이 설계의 목적 자체가 사라진다(5.5절).
      return enroller.bind(finish(fallback, GenerationOutcome.DEFERRED), input);
    }
    return synchronous(generate(input, fallback));
  }

  @Override
  public void enroll(PreparedTaskDraft prepared, UUID taskId, UUID workspaceId) {
    if (prepared instanceof SynchronousDraft) {
      return;
    }
    // 우리가 만들지 않은 준비 결과는 적재기가 거부한다. 조용히 넘기면 적재되지 않은 채 ready가 된다.
    enroller.enroll(prepared, taskId, workspaceId);
  }

  /** 설정이 무효하거나 설정 유효성과 무관하게 비동기가 꺼져 있을 때 타는 기존 동기 경로. */
  private TaskDraft generate(SignalTaskDraftInput input, TaskDraft fallback) {
    if (!configStatus.valid()) {
      return finish(fallback, GenerationOutcome.INVALID_CONFIG);
    }
    if (!SignalTaskDraftPrompt.hasSufficientContext(input)) {
      return finish(fallback, GenerationOutcome.INSUFFICIENT_CONTEXT);
    }

    ModelSelection selection = selectionPolicy.select(LlmUseCase.SIGNAL_TASK_DRAFT);
    LlmRequest request = prompt.render(input);
    Observation observation = observability.startProvider(selection, request.promptVersion());
    long startedAt = System.nanoTime();
    try {
      LlmResponse response = llmClient.generate(selection, request);
      observability.recordTokens(selection, response.tokenUsage());
      Result result = validate(response, input, fallback);
      observability.completeProvider(observation, result.outcome(), response.finishReason(), null);
      logResult(selection, response, result.outcome(), startedAt);
      return finish(result.draft(), result.outcome());
    } catch (RuntimeException e) {
      GenerationOutcome outcome =
          e instanceof LlmTimeoutException
              ? GenerationOutcome.TIMEOUT
              : GenerationOutcome.PROVIDER_ERROR;
      observability.completeProvider(observation, outcome, null, e);
      log.warn(
          "Minsu LLM 호출 실패 provider={} model={} durationMs={} outcome={} fallbackReason={}",
          selection.provider(),
          selection.model(),
          elapsedMillis(startedAt),
          outcome.outcome(),
          outcome.reason());
      return finish(fallback, outcome);
    }
  }

  private Result validate(LlmResponse response, SignalTaskDraftInput input, TaskDraft fallback) {
    if (!response.candidatePresent()
        || !"STOP".equalsIgnoreCase(response.finishReason())
        || response.text() == null
        || response.text().isBlank()) {
      return new Result(fallback, GenerationOutcome.INVALID_RESPONSE);
    }

    GeneratedDraft generated = json.read(response.text(), GeneratedDraft.class).orElse(null);
    if (generated == null) {
      return new Result(fallback, GenerationOutcome.INVALID_RESPONSE);
    }

    Role role = Role.fromValue(normalizeEnum(generated.role())).orElse(null);
    Priority priority = Priority.fromValue(normalizeEnum(generated.priority())).orElse(null);
    if (role == null || priority == null) {
      return new Result(fallback, GenerationOutcome.INVALID_OUTPUT);
    }

    String rawTitle = generated.title();
    boolean titleFallback = rawTitle == null || rawTitle.isBlank();
    String title = TaskTitleNormalizer.normalize(titleFallback ? input.title() : rawTitle);
    boolean truncated = !titleFallback && rawTitle.trim().length() > TaskTitleNormalizer.MAX_LENGTH;
    GenerationOutcome outcome =
        titleFallback
            ? GenerationOutcome.GENERATED_TITLE_FALLBACK
            : truncated ? GenerationOutcome.GENERATED_TRUNCATED : GenerationOutcome.GENERATED;
    return new Result(new TaskDraft(title, role, priority), outcome);
  }

  private TaskDraft fallback(SignalTaskDraftInput input) {
    return new TaskDraft(TaskTitleNormalizer.normalize(input.title()), Role.PM, Priority.MEDIUM);
  }

  private static PreparedTaskDraft synchronous(TaskDraft draft) {
    return new SynchronousDraft(draft);
  }

  private TaskDraft finish(TaskDraft draft, GenerationOutcome outcome) {
    observability.recordRequest(outcome);
    return draft;
  }

  private void logResult(
      ModelSelection selection, LlmResponse response, GenerationOutcome outcome, long startedAt) {
    LoggingEventBuilder event =
        outcome == GenerationOutcome.INVALID_RESPONSE || outcome == GenerationOutcome.INVALID_OUTPUT
            ? log.atWarn()
            : log.atDebug();
    event.log(
        "Minsu LLM 호출 완료 provider={} model={} finishReason={} promptTokens={} candidateTokens={} "
            + "thoughtsTokens={} totalTokens={} responseId={} durationMs={} outcome={} "
            + "fallbackReason={}",
        selection.provider(),
        selection.model(),
        response.finishReason(),
        response.tokenUsage().prompt(),
        response.tokenUsage().candidate(),
        response.tokenUsage().thoughts(),
        response.tokenUsage().total(),
        response.responseId(),
        elapsedMillis(startedAt),
        outcome.outcome(),
        outcome.reason());
  }

  private static String normalizeEnum(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000;
  }

  private record GeneratedDraft(String title, String role, String priority) {}

  private record Result(TaskDraft draft, GenerationOutcome outcome) {}
}
