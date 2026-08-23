package works.momens.server.project.milestone;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link MilestoneOwner}의 복합 식별자.
 *
 * <p>레거시 {@code milestone_owners}는 {@code (milestone_id, owner_user_id)} 복합 PK라 단일 UUID PK용 {@link
 * works.momens.server.common.persistence.BaseEntity}를 쓰지 않고 {@code @IdClass}로 둡니다. JPA
 * {@code @IdClass} 규약대로 public 클래스와 public no-arg 생성자, {@code equals}/{@code hashCode}를 둡니다.
 */
public class MilestoneOwnerId implements Serializable {

  private UUID milestoneId;
  private UUID ownerUserId;

  public MilestoneOwnerId() {}

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MilestoneOwnerId that)) {
      return false;
    }
    return Objects.equals(milestoneId, that.milestoneId)
        && Objects.equals(ownerUserId, that.ownerUserId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(milestoneId, ownerUserId);
  }
}
