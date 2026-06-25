package works.momens.server.auth.presentation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 명령 성공 응답")
record AuthMessageResponse(
    @Schema(description = "결과 메시지", example = "logged out") String message) {}
