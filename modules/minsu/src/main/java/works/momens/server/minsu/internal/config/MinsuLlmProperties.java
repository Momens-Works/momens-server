package works.momens.server.minsu.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("momens.minsu.llm")
public record MinsuLlmProperties(
    @DefaultValue("google") String provider,
    @DefaultValue(MinsuLlmProperties.DEFAULT_MODEL) String model,
    @DefaultValue("8s") Duration timeout,
    @DefaultValue Google google) {

  static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

  public record Google(@DefaultValue("") String project, @DefaultValue("global") String location) {}
}
