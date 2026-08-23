package works.momens.server.project.milestone;

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
 * 마일스톤 소유자.
 *
 * <p>레거시 {@code momens-api}의 {@code milestone_owners} 테이블과 호환됩니다. 웹 응답의 {@code owner_user_ids}
 * backing이며, 마일스톤 생성 작업(MOM-0866)에서 마일스톤과 함께 이 행을 저장합니다. 조회는 {@link MilestoneOwnerRepository}가 배치로
 * 한 번만 합니다(웹 snapshot 계약 4.7의 쿼리 예산).
 *
 * <p>프로젝트 소유자 행과 모양은 같지만 폴백이 다릅니다. 소유자 행이 없을 때 project는 {@code owner_id}로 폴백하는 반면 마일스톤은 폴백할 컬럼이 없어
 * 빈 목록이 그대로 결과가 됩니다.
 *
 * <p>{@code (milestone_id, owner_user_id)} 복합 PK라 {@link
 * works.momens.server.common.persistence.BaseEntity}를 상속하지 않고 식별자를 {@link MilestoneOwnerId}로 둡니다.
 * 레거시 원본에 {@code updated_at}이 없어 {@code created_at}만 있고, 이 컬럼은 조회 정렬 기준입니다.
 *
 * <p>{@code created_at}은 DB DEFAULT로 채워 같은 트랜잭션에서 생성된 행의 정렬 기준을 일관되게 유지합니다.
 */
@Getter
@Entity
@Table(name = "milestone_owners")
@IdClass(MilestoneOwnerId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MilestoneOwner {

  @Id
  @Column(name = "milestone_id", columnDefinition = "uuid")
  private UUID milestoneId;

  @Id
  @Column(name = "owner_user_id", columnDefinition = "uuid")
  private UUID ownerUserId;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  static MilestoneOwner of(UUID milestoneId, UUID ownerUserId) {
    MilestoneOwner owner = new MilestoneOwner();
    owner.milestoneId = milestoneId;
    owner.ownerUserId = ownerUserId;
    return owner;
  }
}
