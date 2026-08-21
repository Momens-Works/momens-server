package works.momens.server.mobile.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token 요청")
public record RefreshTokenRequest(
    @NotBlank @Schema(description = "Refresh token", example = "mF_9.B5f-4.1JqM")
        String refreshToken) {}
