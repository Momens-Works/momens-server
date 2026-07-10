package works.momens.server.auth.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access/refresh token 응답")
public record TokenResponse(
    @JsonProperty("access_token") @Schema(description = "Momens access token") String accessToken,
    @JsonProperty("refresh_token") @Schema(description = "Momens refresh token")
        String refreshToken,
    @JsonProperty("token_type") @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
    @JsonProperty("expires_in") @Schema(description = "access token 만료까지 남은 초", example = "900")
        long expiresIn) {}
