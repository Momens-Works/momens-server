package works.momens.server.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "로그아웃 요청")
public record LogoutRequest(
    @NotBlank @Schema(description = "폐기할 refresh token", example = "mF_9.B5f-4.1JqM")
        String refreshToken) {}
