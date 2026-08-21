package works.momens.server.auth.dev.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "dev 전용 토큰 발급 응답")
public record DevTokenResponse(
    @Schema(description = "발급된 access token", example = "eyJhbGciOiJIUzI1NiIs...")
        String accessToken,
    @Schema(description = "토큰 타입", example = "Bearer") String tokenType) {}
