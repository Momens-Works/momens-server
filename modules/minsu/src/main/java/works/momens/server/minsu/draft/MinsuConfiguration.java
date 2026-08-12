package works.momens.server.minsu.draft;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.minsu.draft.config.MinsuAsyncProperties;
import works.momens.server.minsu.draft.config.MinsuConfigStatus;
import works.momens.server.minsu.draft.config.MinsuTaskDraftProperties;
import works.momens.server.minsu.llm.LlmConfigStatus;

@Configuration
@EnableConfigurationProperties({MinsuTaskDraftProperties.class, MinsuAsyncProperties.class})
class MinsuConfiguration {

  @Bean
  MinsuConfigStatus minsuConfigStatus(
      MinsuTaskDraftProperties taskDraft, LlmConfigStatus llmStatus, MeterRegistry meterRegistry) {
    return new MinsuConfigStatus(taskDraft, llmStatus, meterRegistry);
  }
}
