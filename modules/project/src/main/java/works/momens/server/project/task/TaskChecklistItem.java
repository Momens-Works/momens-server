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
 * Task#checklistItems} 컬렉션으로만 접근합니다. 저장 순서를 담는 {@code position}은 부모 컬렉션이 {@code @OrderBy}로 정렬에만 쓰고
 * 소유는 이 엔티티가 합니다. 순서 부여는 {@code Task#replaceChecklist}가 0부터 연속으로 채웁니다.
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

  @Column(nullable = false)
  private int position;

  /** 새 완료기준 항목의 완료 상태는 요청 값을 따릅니다. 수정 화면이 추가와 동시에 체크할 수 있어서입니다. */
  TaskChecklistItem(String title, boolean completed, int position) {
    this.title = title;
    this.completed = completed;
    this.position = position;
  }

  void updateTitle(String title) {
    this.title = title;
  }

  void changeCompleted(boolean completed) {
    this.completed = completed;
  }

  void changePosition(int position) {
    this.position = position;
  }
}
