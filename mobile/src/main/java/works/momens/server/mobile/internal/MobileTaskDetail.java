package works.momens.server.mobile.internal;

import java.util.List;
import java.util.UUID;
import works.momens.server.project.TaskDetail;

/**
 * 모바일 태스크 상세 조합 결과.
 *
 * <p>{@code purpose}는 도메인의 description을 화면 언어로 개명한 값이고(와이어프레임 task_002), {@code priority}는 모바일
 * 3종(low/medium/high)으로 매핑된 값입니다. 담당자가 없으면 {@code assignee}가 null입니다. 완료기준 항목은 모바일 규칙 없이 그대로 내려가므로
 * project public API 타입({@link TaskDetail.ChecklistItem})을 재사용합니다.
 */
public record MobileTaskDetail(
    UUID id,
    UUID projectId,
    String title,
    String status,
    String role,
    Assignee assignee,
    String priority,
    String purpose,
    List<TaskDetail.ChecklistItem> checklistItems) {

  /** 담당자 표시 정보. */
  public record Assignee(UUID id, String name) {}
}
