package works.momens.server.minsu.internal.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class MinsuConfigStatus {

  static final String GOOGLE = "google";
  private static final Set<String> GOOGLE_LOCATIONS = Set.of("global", "us", "eu");
  private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);
  private static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  private final boolean enabled;
  private final boolean valid;

  public MinsuConfigStatus(
      MinsuTaskDraftProperties taskDraft, MinsuLlmProperties llm, MeterRegistry meterRegistry) {
    enabled = taskDraft.enabled();
    List<String> invalidFields = enabled ? invalidFields(llm) : List.of();
    valid = invalidFields.isEmpty();

    Gauge.builder("momens.minsu.llm.config.valid", this, status -> status.valid ? 1 : 0)
        .description("Minsu LLM deployment configuration validity")
        .tag("enabled", Boolean.toString(enabled))
        .tag("provider", safeTag(llm.provider()))
        .tag("model", safeTag(llm.model()))
        .register(meterRegistry);

    if (!valid) {
      log.error(
          "Minsu LLM 설정이 유효하지 않습니다 fields={} provider={} model={} project={} location={}",
          invalidFields,
          safeLogValue(llm.provider()),
          safeLogValue(llm.model()),
          safeLogValue(llm.google().project()),
          safeLogValue(llm.google().location()));
    }
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean valid() {
    return valid;
  }

  private static List<String> invalidFields(MinsuLlmProperties llm) {
    List<String> fields = new ArrayList<>();
    if (!GOOGLE.equals(llm.provider())) {
      fields.add("provider");
    }
    if (!MinsuLlmProperties.DEFAULT_MODEL.equals(llm.model())) {
      fields.add("model");
    }
    if (llm.timeout() == null
        || llm.timeout().compareTo(MIN_TIMEOUT) < 0
        || llm.timeout().compareTo(MAX_TIMEOUT) > 0) {
      fields.add("timeout");
    }
    if (llm.google().project() == null || llm.google().project().isBlank()) {
      fields.add("google.project");
    }
    if (!GOOGLE_LOCATIONS.contains(llm.google().location())) {
      fields.add("google.location");
    }
    return List.copyOf(fields);
  }

  private static String safeTag(String value) {
    return value == null || value.isBlank() ? "unset" : value;
  }

  private static String safeLogValue(String value) {
    return value == null || value.isBlank() ? "<blank>" : value;
  }
}
