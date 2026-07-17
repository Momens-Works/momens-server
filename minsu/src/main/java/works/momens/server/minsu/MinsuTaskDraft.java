package works.momens.server.minsu;

/**
 * 민수가 생성한 task draft. ADR-0011의 draft 계약(title·role·priority)과 같은 shape입니다.
 *
 * <p>{@code role}은 {@code pm/design/backend/frontend}, {@code priority}는 {@code low/medium/high} 중
 * 하나로 정규화된 값입니다. Signal backing에 저장하지 않고 태스크 생성에만 씁니다(ADR-0011).
 */
public record MinsuTaskDraft(String title, String role, String priority) {}
