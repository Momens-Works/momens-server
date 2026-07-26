package works.momens.server.minsu.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("momens.minsu.llm")
public record MinsuLlmProperties(
    @DefaultValue("google") String provider,
    @DefaultValue("gemini-3.5-flash-lite") String model,
    @DefaultValue Google google) {

  public record Google(@DefaultValue("") String project, @DefaultValue("global") String location) {}
}
