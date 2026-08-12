package works.momens.server.minsu.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinsuLlmProperties.class)
class LlmConfiguration {

  @Bean
  LlmConfigStatus llmConfigStatus(MinsuLlmProperties properties) {
    return new LlmConfigStatus(properties);
  }

  @Bean
  ModelSelectionPolicy modelSelectionPolicy(MinsuLlmProperties properties) {
    return new DeploymentModelSelectionPolicy(properties);
  }
}
