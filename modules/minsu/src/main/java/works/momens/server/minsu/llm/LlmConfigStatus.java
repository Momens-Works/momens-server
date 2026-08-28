package works.momens.server.minsu.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** 배포된 LLM provider 설정이 현재 지원 범위에 속하는지 판정한 결과. */
@Slf4j
public final class LlmConfigStatus {

  private static final String GOOGLE = "google";
  private static final Set<String> GOOGLE_LOCATIONS = Set.of("global", "us", "eu");
  private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);
  private static final Duration MAX_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

  private final MinsuLlmProperties properties;
  private final List<String> invalidFields;

  LlmConfigStatus(MinsuLlmProperties properties) {
    this.properties = properties;
    invalidFields = invalidFields(properties);
  }

  public boolean valid() {
    return invalidFields.isEmpty();
  }

  public String providerTag() {
    return safeTag(properties.provider());
  }

  public String modelTag() {
    return safeTag(properties.model());
  }

  public void logInvalid() {
    log.error(
        "Minsu LLM 설정이 유효하지 않습니다 fields={} provider={} model={} project={} location={}",
        invalidFields,
        safeLogValue(properties.provider()),
        safeLogValue(properties.model()),
        safeLogValue(properties.google().project()),
        safeLogValue(properties.google().location()));
  }

  private static List<String> invalidFields(MinsuLlmProperties properties) {
    List<String> fields = new ArrayList<>();
    if (!GOOGLE.equals(properties.provider())) {
      fields.add("provider");
    }
    if (!MinsuLlmProperties.DEFAULT_MODEL.equals(properties.model())) {
      fields.add("model");
    }
    if (properties.timeout() == null
        || properties.timeout().compareTo(MIN_TIMEOUT) < 0
        || properties.timeout().compareTo(MAX_TIMEOUT) > 0) {
      fields.add("timeout");
    }
    if (properties.google().project() == null || properties.google().project().isBlank()) {
      fields.add("google.project");
    }
    if (!GOOGLE_LOCATIONS.contains(properties.google().location())) {
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
