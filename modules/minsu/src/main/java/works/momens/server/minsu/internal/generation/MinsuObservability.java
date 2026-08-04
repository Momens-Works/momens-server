package works.momens.server.minsu.internal.generation;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.internal.llm.LlmResponse;
import works.momens.server.minsu.internal.llm.ModelSelection;

@Component
final class MinsuObservability {

  static final String OBSERVATION_NAME = "momens.minsu.llm.generate";

  private final MeterRegistry meterRegistry;
  private final ObservationRegistry observationRegistry;

  MinsuObservability(MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
    this.meterRegistry = meterRegistry;
    this.observationRegistry = observationRegistry;
  }

  /** {@code mode}는 동기 요청 경로와 비동기 실행 경로를 나눠 보기 위한 tag다(설계 9.3절). 값이 둘뿐이라 카디널리티가 늘지 않는다. */
  Observation startProvider(ModelSelection selection, String promptVersion, GenerationMode mode) {
    return Observation.start(OBSERVATION_NAME, observationRegistry)
        .lowCardinalityKeyValue("provider", selection.provider())
        .lowCardinalityKeyValue("model", selection.model())
        .lowCardinalityKeyValue("prompt.version", promptVersion)
        .lowCardinalityKeyValue("mode", mode.tag());
  }

  void completeProvider(
      Observation observation, GenerationOutcome outcome, String finishReason, Throwable error) {
    observation
        .lowCardinalityKeyValue("outcome", outcome.outcome())
        .lowCardinalityKeyValue("fallback.reason", outcome.reason())
        .lowCardinalityKeyValue("finish.reason", safeFinishReason(finishReason));
    if (error != null) {
      observation.error(error);
    }
    observation.stop();
  }

  void recordRequest(GenerationOutcome outcome) {
    meterRegistry
        .counter(
            "momens.minsu.task.draft.requests",
            "outcome",
            outcome.outcome(),
            "fallback.reason",
            outcome.reason())
        .increment();
  }

  void recordTokens(ModelSelection selection, LlmResponse.TokenUsage usage) {
    recordToken("prompt", selection, usage.prompt());
    recordToken("candidate", selection, usage.candidate());
    recordToken("thoughts", selection, usage.thoughts());
    recordToken("total", selection, usage.total());
  }

  private void recordToken(String type, ModelSelection selection, int count) {
    DistributionSummary.builder("momens.minsu.llm.tokens")
        .baseUnit("tokens")
        .tag("provider", selection.provider())
        .tag("model", selection.model())
        .tag("type", type)
        .register(meterRegistry)
        .record(count);
  }

  private static String safeFinishReason(String finishReason) {
    return finishReason == null || finishReason.isBlank()
        ? "none"
        : finishReason.toLowerCase(java.util.Locale.ROOT);
  }
}
