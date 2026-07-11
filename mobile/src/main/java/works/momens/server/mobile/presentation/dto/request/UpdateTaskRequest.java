package works.momens.server.mobile.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 태스크 수정 요청. 요청 형식은 docs/spec/mobile-api.md 태스크 수정 절을 따릅니다.
 *
 * <p>수정 화면은 저장할 때 편집 상태 전체를 보내므로 title, role, priority, status는 항상 채워진 값입니다. title은 생성과 달리 빈 문자열을
 * 허용합니다. 제목을 비우면 상세 화면이 '새 태스크'로 표시하기 때문입니다. 그래서 값이 있는지만 확인하고 공백은 막지 않습니다. {@code assigneeId}는 값이면
 * 지정, null이면 담당자 비우기입니다. {@code purpose}는 목적이고 저장 시 description에 매핑됩니다. {@code checklistItems}는
 * 완료기준 최종 목록이며 0개에서 5개까지 허용합니다. 글자 수 제한은 별도 이슈로 보류되어 여기서 검증하지 않습니다.
 */
@Schema(description = "태스크 수정 요청")
public record UpdateTaskRequest(
    @Schema(description = "제목. 값은 있어야 하고 빈 문자열은 허용합니다.", example = "1차 와이어프레임") @NotNull
        String title,
    @Schema(description = "역할. pm/design/backend/frontend 중 하나", example = "pm")
        @NotBlank
        @Pattern(regexp = "pm|design|backend|frontend")
        String role,
    @Schema(description = "담당자 식별자. 비우려면 null을 보냅니다.") UUID assigneeId,
    @Schema(description = "우선순위. low/medium/high", example = "medium")
        @NotBlank
        @Pattern(regexp = "low|medium|high")
        String priority,
    @Schema(
            description = "상태. backlog/todo/in_progress/done/cancelled 중 하나",
            example = "in_progress")
        @NotBlank
        @Pattern(regexp = "backlog|todo|in_progress|done|cancelled")
        String status,
    @Schema(description = "목적. 비우면 빈 문자열이나 null입니다.") String purpose,
    @Schema(description = "완료기준 최종 목록. 0개에서 5개까지 허용합니다.") @Size(max = 5) @Valid
        List<ChecklistItemRequest> checklistItems) {

  public UpdateTaskRequest {
    // 수정 화면은 완료기준을 항상 보내지만, 필드가 빠져도 빈 목록으로 다뤄 전체 교체가 안전하게 동작하도록 한다.
    checklistItems = checklistItems == null ? List.of() : checklistItems;
  }

  /** 완료기준 한 항목. id가 있으면 기존 항목, 없으면 새 항목입니다. */
  @Schema(description = "완료기준 항목")
  public record ChecklistItemRequest(
      @Schema(description = "항목 식별자. 새 항목이면 생략합니다.") UUID id,
      @Schema(description = "항목 제목", example = "회원가입 에러 메시지 반영") @NotBlank String title) {}
}
