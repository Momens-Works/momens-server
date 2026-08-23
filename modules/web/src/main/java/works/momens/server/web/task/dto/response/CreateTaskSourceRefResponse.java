package works.momens.server.web.task.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "링크 첨부 성공 응답")
public record CreateTaskSourceRefResponse(
    @Schema(description = "생성된 source-ref 식별자") UUID id,
    @Schema(description = "결과 메시지") String message) {}
