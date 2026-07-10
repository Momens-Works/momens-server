package works.momens.server.auth.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token 요청")
public record RefreshTokenRequest(
    @NotBlank
        @JsonProperty("refresh_token")
        @Schema(description = "Refresh token", example = "mF_9.B5f-4.1JqM")
        String refreshToken) {}
