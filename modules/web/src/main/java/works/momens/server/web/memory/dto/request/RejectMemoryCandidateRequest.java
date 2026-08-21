package works.momens.server.web.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "메모리 후보 거절 요청")
public record RejectMemoryCandidateRequest(@Schema(description = "거절 사유") String reason) {}
