package works.momens.server.project.core.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectOwnerRepository extends JpaRepository<ProjectOwner, ProjectOwnerId> {

  /**
   * projectIds의 소유자를 한 번에 조회합니다. 정렬은 레거시 집계와 같은 {@code created_at, owner_user_id}입니다.
   *
   * <p>project별로 나눠 읽지 않는 것은 계약이 행별 추가 조회를 금지하기 때문입니다(웹 snapshot 계약 4.7).
   */
  List<ProjectOwner> findByProjectIdInOrderByCreatedAtAscOwnerUserIdAsc(
      Collection<UUID> projectIds);
}
