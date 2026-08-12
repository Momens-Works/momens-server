package works.momens.server.minsu.draft.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import works.momens.server.minsu.llm.MinsuLlmProperties;

class MinsuConfigStatusTest {

  /** 이 테스트는 provider 축만 보므로 지표 주기는 기본값이면 된다. */
  private static final MinsuTaskDraftProperties.Metrics METRICS =
      new MinsuTaskDraftProperties.Metrics(java.time.Duration.ofSeconds(10));

  @Test
  void disabledDefaultIsValidWithoutGoogleProject() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    MinsuConfigStatus status =
        new MinsuConfigStatus(
            new MinsuTaskDraftProperties(false, METRICS),
            properties("google", "gemini-3.5-flash-lite", "", "global"),
            registry);

    assertThat(status.enabled()).isFalse();
    assertThat(status.valid()).isTrue();
    assertThat(registry.get("momens.minsu.llm.config.valid").gauge().value()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("invalidProperties")
  void enabledInvalidConfigurationDoesNotThrowAndExposesZeroGauge(MinsuLlmProperties properties) {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    MinsuConfigStatus status =
        new MinsuConfigStatus(new MinsuTaskDraftProperties(true, METRICS), properties, registry);

    assertThat(status.valid()).isFalse();
    assertThat(registry.get("momens.minsu.llm.config.valid").gauge().value()).isZero();
  }

  @Test
  void enabledCatalogConfigurationIsValid() {
    MinsuConfigStatus status =
        new MinsuConfigStatus(
            new MinsuTaskDraftProperties(true, METRICS),
            properties("google", "gemini-3.5-flash-lite", "project", "eu"),
            new SimpleMeterRegistry());

    assertThat(status.valid()).isTrue();
  }

  @Test
  void enabledMaximumTimeoutIsValid() {
    MinsuConfigStatus status =
        new MinsuConfigStatus(
            new MinsuTaskDraftProperties(true, METRICS),
            new MinsuLlmProperties(
                "google",
                "gemini-3.5-flash-lite",
                Duration.ofMillis(Integer.MAX_VALUE),
                new MinsuLlmProperties.Google("project", "global")),
            new SimpleMeterRegistry());

    assertThat(status.valid()).isTrue();
  }

  private static Stream<Arguments> invalidProperties() {
    return Stream.of(
        Arguments.of(properties("other", "gemini-3.5-flash-lite", "project", "global")),
        Arguments.of(properties("google", "other-model", "project", "global")),
        Arguments.of(
            new MinsuLlmProperties(
                "google",
                "gemini-3.5-flash-lite",
                Duration.ZERO,
                new MinsuLlmProperties.Google("project", "global"))),
        Arguments.of(
            new MinsuLlmProperties(
                "google",
                "gemini-3.5-flash-lite",
                Duration.ofNanos(1),
                new MinsuLlmProperties.Google("project", "global"))),
        Arguments.of(
            new MinsuLlmProperties(
                "google",
                "gemini-3.5-flash-lite",
                Duration.ofMillis(Integer.MAX_VALUE).plusMillis(1),
                new MinsuLlmProperties.Google("project", "global"))),
        Arguments.of(properties("google", "gemini-3.5-flash-lite", "", "global")),
        Arguments.of(properties("google", "gemini-3.5-flash-lite", "project", "asia-northeast3")));
  }

  private static MinsuLlmProperties properties(
      String provider, String model, String project, String location) {
    return new MinsuLlmProperties(
        provider, model, Duration.ofSeconds(8), new MinsuLlmProperties.Google(project, location));
  }
}
