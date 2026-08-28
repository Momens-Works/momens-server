package works.momens.server.web.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메모리 후보 수정 후 확정 요청")
public record EditAndConfirmMemoryCandidateRequest(
    @Schema(description = "수정할 제목") String title,
    @Schema(description = "수정할 요약") String summary,
    @Schema(description = "수정할 본문") String body) {}
