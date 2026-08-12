package works.momens.server.minsu.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LlmConfigStatusTest {

  @ParameterizedTest
  @MethodSource("invalidProperties")
  void rejectsUnsupportedDeploymentConfiguration(MinsuLlmProperties properties) {
    assertThat(new LlmConfigStatus(properties).valid()).isFalse();
  }

  @Test
  void acceptsSupportedDeploymentConfiguration() {
    assertThat(
            new LlmConfigStatus(properties("google", "gemini-3.5-flash-lite", "project", "eu"))
                .valid())
        .isTrue();
  }

  @Test
  void acceptsMaximumTimeout() {
    MinsuLlmProperties properties =
        new MinsuLlmProperties(
            "google",
            "gemini-3.5-flash-lite",
            Duration.ofMillis(Integer.MAX_VALUE),
            new MinsuLlmProperties.Google("project", "global"));

    assertThat(new LlmConfigStatus(properties).valid()).isTrue();
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
