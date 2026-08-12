package works.momens.server.minsu.draft;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.minsu.draft.config.MinsuAsyncProperties;
import works.momens.server.minsu.draft.config.MinsuConfigStatus;
import works.momens.server.minsu.draft.config.MinsuTaskDraftProperties;
import works.momens.server.minsu.llm.MinsuLlmProperties;

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
