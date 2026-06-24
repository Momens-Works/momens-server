package works.momens.server.support.openapi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "momens.openapi")
public record OpenApiProperties(@NotBlank String serverUrl) {}
