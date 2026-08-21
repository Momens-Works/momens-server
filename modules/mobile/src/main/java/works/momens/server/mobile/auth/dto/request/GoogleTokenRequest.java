package works.momens.server.mobile.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "모바일 Google ID 토큰 교환 요청")
public record GoogleTokenRequest(
    @NotBlank
        @Schema(description = "Google Sign-In에서 받은 ID 토큰", example = "eyJhbGciOiJSUzI1NiIs...")
        String idToken,
    @Size(max = 255)
        @Schema(description = "클라이언트 기기 식별용 표시값", example = "iPhone 15 Pro", nullable = true)
        String device) {}
