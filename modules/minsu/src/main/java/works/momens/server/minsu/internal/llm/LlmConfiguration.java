package works.momens.server.minsu.internal.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import works.momens.server.minsu.internal.config.MinsuLlmProperties;

@Configuration
class LlmConfiguration {

  @Bean
  ModelSelectionPolicy modelSelectionPolicy(MinsuLlmProperties properties) {
    return new DeploymentModelSelectionPolicy(properties);
  }
}
