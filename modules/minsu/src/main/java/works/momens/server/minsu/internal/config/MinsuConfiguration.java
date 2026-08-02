package works.momens.server.minsu.internal.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  MinsuTaskDraftProperties.class,
  MinsuLlmProperties.class,
  MinsuAsyncProperties.class
})
class MinsuConfiguration {

  @Bean
  MinsuConfigStatus minsuConfigStatus(
      MinsuTaskDraftProperties taskDraft, MinsuLlmProperties llm, MeterRegistry meterRegistry) {
    return new MinsuConfigStatus(taskDraft, llm, meterRegistry);
  }
}
