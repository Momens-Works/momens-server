package works.momens.server.web.memory.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 메모리 후보 병합 요청입니다.
 *
 * <p>필드 이름은 레거시 계약을 그대로 씁니다. 전역 {@code SNAKE_CASE} 전략이 걸려 있어 wire 키는 {@code
 * merge_target_memory_id}가 되고, 이것이 웹 FE가 보내는 키입니다({@code client.ts mergeCandidate}).
 */
@Schema(description = "메모리 후보 병합 요청")
public record MergeMemoryCandidateRequest(
    @Schema(
            description = "병합 대상 메모리 식별자",
            format = "uuid",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID mergeTargetMemoryId) {}
