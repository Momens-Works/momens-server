package works.momens.server.project.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 태스크 완료기준 항목.
 *
 * <p>{@code Task} aggregate 내부에서만 생성/수정/삭제되는 자식 엔티티입니다. 별도 repository를 두지 않고 {@code
 * Task#checklistItems} 컬렉션으로만 접근합니다. 저장 순서를 담는 {@code position} 컬럼은 부모 컬렉션의 {@code @OrderColumn}이
 * 소유하므로 필드로 매핑하지 않습니다.
 */
@Getter
@Entity
@Table(name = "task_checklist_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TaskChecklistItem extends BaseEntity {

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private boolean completed;

  /** 새 완료기준 항목의 완료 상태는 요청 값을 따릅니다. 수정 화면이 추가와 동시에 체크할 수 있어서입니다. */
  TaskChecklistItem(String title, boolean completed) {
    this.title = title;
    this.completed = completed;
  }

  void updateTitle(String title) {
    this.title = title;
  }

  void changeCompleted(boolean completed) {
    this.completed = completed;
  }
}
