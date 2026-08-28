package works.momens.server.web.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "메모리 해결 요청")
public record ResolveMemoryRequest(
    @Schema(
            description = "해결하는 메모리 식별자",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID resolvingMemoryId) {}
