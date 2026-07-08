package works.momens.server.signal.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.signal.ConvertToTaskCommand;

/**
 * convert-to-task 요청. body 전체와 각 필드가 모두 생략 가능하다(docs/spec/mobile-api.md).
 *
 * <p>{@code title}이 없으면 Signal 제목으로, {@code priority}가 없으면 {@code medium}으로 폴백한다. {@code role}은 폴백이
 * 없어 생략하면 {@code COMMON_VALIDATION_FAILED}(400)를 반환한다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "convert-to-task 요청. 전체와 각 필드 모두 생략 가능")
public record ConvertToTaskRequest(
    @Schema(description = "제목. 생략하면 Signal 제목을 쓴다", example = "권한 요청 플로우 점검") String title,
    @Schema(
            description = "역할. pm/design/backend/frontend 중 하나. 폴백이 없어 생략 시 검증 실패",
            example = "backend")
        @Pattern(regexp = "pm|design|backend|frontend")
        String role,
    @Schema(description = "우선순위. low/medium/high. 생략하면 medium", example = "medium")
        @Pattern(regexp = "low|medium|high")
        String priority) {

  public ConvertToTaskCommand toCommand() {
    return new ConvertToTaskCommand(title, role, priority);
  }
}
