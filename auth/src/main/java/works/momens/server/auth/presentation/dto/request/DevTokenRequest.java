package works.momens.server.auth.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

@Schema(description = "dev 전용 토큰 발급 요청")
public record DevTokenRequest(
    @Email
        @Schema(
            description = "토큰을 발급할 테스트 사용자 이메일(allowlist). 비우면 allowlist 첫 사용자로 발급합니다.",
            example = "owner@momens.works",
            nullable = true)
        String email) {}
