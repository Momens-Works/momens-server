package works.momens.server.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.auth.internal.jwt.TokenPair;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Access/refresh token 응답")
record TokenResponse(
    @Schema(description = "Momens access token") String accessToken,
    @Schema(description = "Momens refresh token") String refreshToken,
    @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
    @Schema(description = "access token 만료까지 남은 초", example = "900") long expiresIn) {

  static TokenResponse from(TokenPair tokenPair) {
    return new TokenResponse(
        tokenPair.accessToken(), tokenPair.refreshToken(), "Bearer", tokenPair.expiresInSeconds());
  }
}
