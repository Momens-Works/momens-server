package works.momens.server.minsu.draft.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import works.momens.server.minsu.llm.LlmConfigStatus;

public final class MinsuConfigStatus {

  private final boolean enabled;
  private final boolean valid;

  public MinsuConfigStatus(
      MinsuTaskDraftProperties taskDraft, LlmConfigStatus llmStatus, MeterRegistry meterRegistry) {
    enabled = taskDraft.enabled();
    valid = !enabled || llmStatus.valid();

    Gauge.builder("momens.minsu.llm.config.valid", this, status -> status.valid ? 1 : 0)
        .description("Minsu LLM deployment configuration validity")
        .tag("enabled", Boolean.toString(enabled))
        .tag("provider", llmStatus.providerTag())
        .tag("model", llmStatus.modelTag())
        .register(meterRegistry);

    if (!valid) {
      llmStatus.logInvalid();
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean valid() {
    return valid;
  }
}
