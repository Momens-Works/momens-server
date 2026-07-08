package works.momens.server.mobile.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 완료기준 항목 완료 상태 변경 요청. 요청 형식은 docs/spec/mobile-api.md 태스크 수정 절을 따릅니다.
 *
 * <p>완료 상태를 그대로 지정하는 값이라 항상 채워야 합니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "완료기준 완료 상태 변경 요청")
public record ToggleChecklistItemRequest(
    @Schema(description = "완료 여부", example = "true") @NotNull Boolean completed) {}
