package works.momens.server.mobile.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 일반 태스크 생성 요청. 요청 형식은 docs/spec/mobile-api.md 태스크 절을 따릅니다.
 *
 * <p>title, role, priority 모두 필수입니다(2026-07-06 기획 확정). role은 하나만 선택하고 pm/design/backend/frontend/
 * android/qa 중 하나입니다(생성 화면 역할 칩은 단일 선택). priority는 low/medium/high 중 하나입니다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "일반 태스크 생성 요청")
public record CreateTaskRequest(
    @Schema(description = "제목", example = "권한 요청 플로우 점검") @NotBlank String title,
    @Schema(description = "역할. pm/design/backend/frontend/android/qa 중 하나", example = "android")
        @NotBlank
        @Pattern(regexp = "pm|design|backend|frontend|android|qa")
        String role,
    @Schema(description = "우선순위. low/medium/high", example = "medium")
        @NotBlank
        @Pattern(regexp = "low|medium|high")
        String priority) {}
