package works.momens.server.minsu.draft.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.llm.LlmConfigStatus;

class MinsuConfigStatusTest {

  private static final MinsuTaskDraftProperties.Metrics METRICS =
      new MinsuTaskDraftProperties.Metrics(Duration.ofSeconds(10));

  @Test
  void disabledDraftIsValidRegardlessOfLlmStatus() {
    LlmConfigStatus llmStatus = mock(LlmConfigStatus.class);
    when(llmStatus.providerTag()).thenReturn("google");
    when(llmStatus.modelTag()).thenReturn("gemini-3.5-flash-lite");
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    MinsuConfigStatus status =
        new MinsuConfigStatus(new MinsuTaskDraftProperties(false, METRICS), llmStatus, registry);

    assertThat(status.enabled()).isFalse();
    assertThat(status.valid()).isTrue();
    assertThat(registry.get("momens.minsu.llm.config.valid").gauge().value()).isEqualTo(1);
  }

  @Test
  void enabledDraftUsesLlmStatus() {
    LlmConfigStatus llmStatus = mock(LlmConfigStatus.class);
    when(llmStatus.valid()).thenReturn(false);
    when(llmStatus.providerTag()).thenReturn("google");
    when(llmStatus.modelTag()).thenReturn("gemini-3.5-flash-lite");
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    MinsuConfigStatus status =
        new MinsuConfigStatus(new MinsuTaskDraftProperties(true, METRICS), llmStatus, registry);

    assertThat(status.enabled()).isTrue();
    assertThat(status.valid()).isFalse();
    assertThat(registry.get("momens.minsu.llm.config.valid").gauge().value()).isZero();
  }
}
