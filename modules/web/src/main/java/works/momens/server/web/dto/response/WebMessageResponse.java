package works.momens.server.web.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "명령 성공 응답")
public record WebMessageResponse(
    @Schema(description = "결과 메시지", example = "updated") String message) {}
