package works.momens.server.project.task;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 태스크.
 *
 * <p>레거시 {@code momens-api}의 {@code tasks} 테이블과 호환됩니다. 모바일 보드/생성(MOM-62)이 읽고 쓰는 컬럼만 매핑하고, 나머지 레거시
 * 컬럼(milestone_id, description, assignee_id, due_date)은 태스크 상세/수정(MOM-63)과 웹 이관에서 추가합니다.
 *
 * <p>{@code status}와 {@code priority}는 {@code Project}와 같은 기준으로 문자열로 둡니다. DB CHECK 제약이 각각 레거시
 * 5종/4종만 허용합니다. {@code roles}는 레거시 {@code tasks}에 없는 신규 속성이라, 기존 테이블을 바꾸지 않고 부가 테이블 {@code
 * task_roles}에 값 컬렉션으로 저장합니다.
 */
@Getter
@Entity
@Table(name = "tasks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Task extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
  private UUID projectId;

  @Column private String label;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String priority;

  @ElementCollection
  @CollectionTable(name = "task_roles", joinColumns = @JoinColumn(name = "task_id"))
  @Column(name = "role", nullable = false)
  @BatchSize(size = 100)
  private Set<String> roles = new HashSet<>();

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Builder
  private Task(
      UUID workspaceId,
      UUID projectId,
      String label,
      String title,
      String status,
      String priority,
      Collection<String> roles) {
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.label = label;
    this.title = title;
    // INSERT 시 엔티티 값이 DB DEFAULT 보다 우선하므로, 레거시 기본값을 앱 생성에서도 보장한다.
    this.status = status != null ? status : "backlog";
    this.priority = priority != null ? priority : "medium";
    this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
  }

  /** roles를 결정적 순서(정렬)로 반환한다. 보드 응답과 생성 응답의 순서를 한곳에서 고정한다. */
  List<String> sortedRoles() {
    return roles.stream().sorted().toList();
  }
}
