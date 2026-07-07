package works.momens.server.project;

import java.util.List;
import java.util.UUID;

/**
 * 상세 조회용 태스크 한 건.
 *
 * <p>{@code status}, {@code priority}, {@code description}은 저장된 원본 그대로입니다. 모바일 표기 매핑(urgent를 high로,
 * description을 purpose로)은 조회하는 쪽(mobile)이 정합니다({@link BoardTask}와 같은 원칙). {@code workspaceId}는 호출
 * 쪽이 태스크 접근 권한(멤버십)을 확인할 때 씁니다. {@code roles}는 결정적 순서를 위해 정렬해 담고, {@code checklistItems}는 저장 순서
 * 그대로입니다.
 */
public record TaskDetail(
    UUID id,
    UUID projectId,
    UUID workspaceId,
    String title,
    String status,
    String priority,
    List<String> roles,
    UUID assigneeId,
    String description,
    List<ChecklistItem> checklistItems) {

  /** 완료기준 항목 한 건. */
  public record ChecklistItem(UUID id, String title, boolean completed) {}
}
