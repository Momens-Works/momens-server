package works.momens.server.workspace.email;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "momens.workspace.invitation.email.resend")
record ResendEmailProperties(@NotBlank String apiKey) {}
