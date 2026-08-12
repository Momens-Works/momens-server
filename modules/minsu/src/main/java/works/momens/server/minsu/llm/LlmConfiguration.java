package works.momens.server.minsu.llm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LlmConfiguration {

  @Bean
  ModelSelectionPolicy modelSelectionPolicy(MinsuLlmProperties properties) {
    return new DeploymentModelSelectionPolicy(properties);
  }
}
