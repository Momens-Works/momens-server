package works.momens.server.minsu.internal.generation;

import io.micrometer.observation.Observation;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftGenerator;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.internal.config.MinsuConfigStatus;
import works.momens.server.minsu.internal.json.MinsuJson;
import works.momens.server.minsu.internal.llm.LlmClient;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.LlmUseCase;
import works.momens.server.minsu.internal.llm.ModelSelection;
import works.momens.server.minsu.internal.llm.ModelSelectionPolicy;
import works.momens.server.minsu.internal.prompt.SignalTaskDraftPrompt;

@Slf4j
@Component
final class DefaultSignalTaskDraftGenerator implements SignalTaskDraftGenerator {

  private final MinsuConfigStatus configStatus;
  private final ModelSelectionPolicy selectionPolicy;
  private final LlmClient llmClient;
  private final SignalTaskDraftPrompt prompt;
  private final MinsuJson json;
  private final MinsuObservability observability;

  DefaultSignalTaskDraftGenerator(
      MinsuConfigStatus configStatus,
      ModelSelectionPolicy selectionPolicy,
      LlmClient llmClient,
      SignalTaskDraftPrompt prompt,
      MinsuJson json,
      MinsuObservability observability) {
    this.configStatus = configStatus;
    this.selectionPolicy = selectionPolicy;
    this.llmClient = llmClient;
    this.prompt = prompt;
    this.json = json;
    this.observability = observability;
  }

  @Override
  public TaskDraft generate(SignalTaskDraftInput input) {
    TaskDraft fallback = fallback(input);
    if (!configStatus.enabled()) {
      return finish(fallback, GenerationOutcome.DISABLED);
    }
    if (!configStatus.valid()) {
      return finish(fallback, GenerationOutcome.INVALID_CONFIG);
    }
    if (!SignalTaskDraftPrompt.hasSufficientContext(input)) {
      return finish(fallback, GenerationOutcome.INSUFFICIENT_CONTEXT);
    }

    ModelSelection selection = selectionPolicy.select(LlmUseCase.SIGNAL_TASK_DRAFT);
    Observation observation = observability.startProvider(selection);
    long startedAt = System.nanoTime();
    try {
      LlmResponse response = llmClient.generate(selection, prompt.render(input));
      observability.recordTokens(selection, response.tokenUsage());
      Result result = validate(response, input, fallback);
      observability.completeProvider(observation, result.outcome(), response.finishReason(), null);
      logResult(selection, response, result.outcome(), startedAt);
      return finish(result.draft(), result.outcome());
    } catch (RuntimeException e) {
      observability.completeProvider(observation, GenerationOutcome.PROVIDER_ERROR, null, e);
      log.warn(
          "Minsu LLM 호출 실패 provider={} model={} durationMs={} outcome={} fallbackReason={}",
          selection.provider(),
          selection.model(),
          elapsedMillis(startedAt),
          GenerationOutcome.PROVIDER_ERROR.outcome(),
          GenerationOutcome.PROVIDER_ERROR.reason());
      return finish(fallback, GenerationOutcome.PROVIDER_ERROR);
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

  private TaskDraft finish(TaskDraft draft, GenerationOutcome outcome) {
    observability.recordRequest(outcome);
    return draft;
  }

  private void logResult(
      ModelSelection selection, LlmResponse response, GenerationOutcome outcome, long startedAt) {
    log.debug(
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
