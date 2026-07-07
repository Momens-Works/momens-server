package works.momens.server.project.task;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
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
 * <p>레거시 {@code momens-api}의 {@code tasks} 테이블과 호환됩니다. 모바일 보드/생성(MOM-62)이 읽고 쓰는 컬럼에 태스크 상세(MOM-63)가
 * 읽는 description, assignee_id를 더해 매핑하고, 모바일이 안 쓰는 레거시 컬럼(milestone_id, due_date)은 웹 이관에서 추가합니다.
 * assignee_id는 다른 모듈(user) 소유 리소스라 엔티티 연관 없이 식별자로만 참조합니다(projectId와 같은 기준).
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

  @Column private String description;

  @Column(name = "assignee_id", columnDefinition = "uuid")
  private UUID assigneeId;

  @ElementCollection
  @CollectionTable(name = "task_roles", joinColumns = @JoinColumn(name = "task_id"))
  @Column(name = "role", nullable = false)
  @BatchSize(size = 100)
  private Set<String> roles = new HashSet<>();

  /**
   * 완료기준 항목. 자식 엔티티의 전체 생명주기를 이 aggregate가 소유하므로 cascade ALL과 orphanRemoval로 컬렉션 변경이 곧 저장 변경이 되게
   * 합니다. 리스트 순서가 저장 순서라(수정 화면이 전체 목록 순서로 저장) {@code @OrderColumn}을 부모 소유 단방향으로 둡니다. mappedBy 컬렉션의
   * {@code @OrderColumn}은 Hibernate 7부터 리스트 위치를 자동 유지하지 않습니다(7.0 migration guide).
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "task_id", nullable = false, updatable = false)
  @OrderColumn(name = "position")
  private List<TaskChecklistItem> checklistItems = new ArrayList<>();

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
