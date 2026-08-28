package works.momens.server.mobile.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access/refresh token 응답")
public record TokenResponse(
    @Schema(description = "Momens access token") String accessToken,
    @Schema(description = "Momens refresh token") String refreshToken,
    @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
    @Schema(description = "access token 만료까지 남은 초", example = "900") long expiresIn) {}
