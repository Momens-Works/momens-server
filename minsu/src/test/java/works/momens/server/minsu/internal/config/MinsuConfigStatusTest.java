package works.momens.server.minsu.internal.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MinsuConfigStatusTest {

  @Test
  void disabledDefaultIsValidWithoutGoogleProject() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    MinsuConfigStatus status =
        new MinsuConfigStatus(
            new MinsuTaskDraftProperties(false),
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
        new MinsuConfigStatus(new MinsuTaskDraftProperties(true), properties, registry);

    assertThat(status.valid()).isFalse();
    assertThat(registry.get("momens.minsu.llm.config.valid").gauge().value()).isZero();
  }

  @Test
  void enabledCatalogConfigurationIsValid() {
    MinsuConfigStatus status =
        new MinsuConfigStatus(
            new MinsuTaskDraftProperties(true),
            properties("google", "gemini-3.5-flash-lite", "project", "eu"),
            new SimpleMeterRegistry());

    assertThat(status.valid()).isTrue();
  }

  private static Stream<Arguments> invalidProperties() {
    return Stream.of(
        Arguments.of(properties("other", "gemini-3.5-flash-lite", "project", "global")),
        Arguments.of(properties("google", "other-model", "project", "global")),
        Arguments.of(properties("google", "gemini-3.5-flash-lite", "", "global")),
        Arguments.of(properties("google", "gemini-3.5-flash-lite", "project", "asia-northeast3")));
  }

  private static MinsuLlmProperties properties(
      String provider, String model, String project, String location) {
    return new MinsuLlmProperties(
        provider, model, new MinsuLlmProperties.Google(project, location));
  }
}
