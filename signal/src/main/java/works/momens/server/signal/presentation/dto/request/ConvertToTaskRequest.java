package works.momens.server.signal.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import works.momens.server.signal.SignalActionService;

/**
 * convert-to-task 요청. body 자체는 생략 가능하다(docs/spec/mobile-api.md).
 *
 * <p>{@code title}이 없으면 Signal 제목으로, {@code priority}가 없으면 {@code medium}으로 폴백해 둘은 선택이다. {@code
 * role}은 폴백할 값이 없어(Signal 상세의 {@code minsu.task_draft.roles}는 항상 빈 배열) body에 없으면 {@code
 * COMMON_VALIDATION_FAILED}(400)를 반환한다 — 실질적으로 필수다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "convert-to-task 요청. body는 생략 가능하나 보낼 경우 role은 필수다")
public record ConvertToTaskRequest(
    @Schema(
            description = "제목. 생략하면 Signal 제목을 쓴다. 값이 있으면 공백일 수 없다",
            example = "권한 요청 플로우 점검",
            nullable = true)
        @Pattern(regexp = "(?U).*\\S.*")
        String title,
    @Schema(
            description = "역할. pm/design/backend/frontend 중 하나. 폴백이 없어 생략 시 검증 실패",
            example = "backend")
        @Pattern(regexp = "pm|design|backend|frontend")
        String role,
    @Schema(description = "우선순위. low/medium/high. 생략하면 medium", example = "medium")
        @Pattern(regexp = "low|medium|high")
        String priority) {

  public SignalActionService.ConvertToTaskCommand toCommand() {
    return new SignalActionService.ConvertToTaskCommand(title, role, priority);
  }
}
