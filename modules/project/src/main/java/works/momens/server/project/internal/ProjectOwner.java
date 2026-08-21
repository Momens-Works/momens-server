package works.momens.server.project.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 소유자.
 *
 * <p>레거시 {@code momens-api}의 {@code project_owners} 테이블과 호환됩니다. 웹 응답의 {@code owner_user_ids}
 * backing이며, 프로젝트 생성 작업(MOM-0866)에서 프로젝트와 함께 이 행을 저장합니다. 조회는 {@link ProjectOwnerRepository}가 배치로 한
 * 번만 합니다(웹 snapshot 계약 4.7의 쿼리 예산).
 *
 * <p>{@code (project_id, owner_user_id)} 복합 PK라 {@link
 * works.momens.server.common.persistence.BaseEntity}를 상속하지 않고 식별자를 {@link ProjectOwnerId}로 둡니다. 레거시
 * 원본에 {@code updated_at}이 없어 {@code created_at}만 있고, 이 컬럼은 조회 정렬 기준입니다.
 *
 * <p>{@code created_at}은 INSERT 대상에서 제외하고 DB DEFAULT로 채웁니다. 이 컬럼은 조회 정렬 기준이지만 PostgreSQL의 {@code
 * NOW()}는 현재 트랜잭션이 시작된 시각을 반환하므로, 한 번에 추가한 소유자 행에는 모두 같은 값이 저장됩니다. 따라서 동률인 행은 레거시와 동일하게 {@code
 * owner_user_id}를 기준으로 정렬됩니다. 애플리케이션에서 행마다 시각을 생성하면 삽입 순서에 따라 조회 순서가 달라질 수 있습니다.
 */
@Getter
@Entity
@Table(name = "project_owners")
@IdClass(ProjectOwnerId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProjectOwner {

  @Id
  @Column(name = "project_id", columnDefinition = "uuid")
  private UUID projectId;

  @Id
  @Column(name = "owner_user_id", columnDefinition = "uuid")
  private UUID ownerUserId;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  static ProjectOwner of(UUID projectId, UUID ownerUserId) {
    ProjectOwner owner = new ProjectOwner();
    owner.projectId = projectId;
    owner.ownerUserId = ownerUserId;
    return owner;
  }
}
