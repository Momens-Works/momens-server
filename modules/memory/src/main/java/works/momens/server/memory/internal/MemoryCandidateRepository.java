package works.momens.server.memory.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import works.momens.server.memory.MemoryCandidateDetail;

interface MemoryCandidateRepository extends JpaRepository<MemoryCandidate, UUID> {

  /**
   * 웹 목록 조회용 DTO projection입니다. 엔티티를 영속성 컨텍스트에 올리지 않고 필요한 컬럼만 읽습니다.
   *
   * <p>후보에는 소프트 삭제 컬럼이 없어 필터가 워크스페이스뿐입니다. 상태 필터도 두지 않습니다. 레거시 시그니처에는 있지만 snapshot이 빈 문자열로 넘겨 실제로는
   * 거르지 않습니다(웹 snapshot 계약 4.4).
   *
   * <p>정렬은 레거시와 같은 {@code importance desc nulls last, created_at desc}입니다. snapshot의 여덟 목록 가운데 유일하게
   * {@code created_at}이 아닌 키로 정렬하는 컬렉션이며, 보드가 중요도 순으로 후보를 보여주는 동작이라 그대로 보존합니다.
   */
  @Query(
      """
      select new works.momens.server.memory.MemoryCandidateDetail(
          c.id, c.workspaceId, c.label, c.candidateType, c.title, c.summary, c.body,
          c.confidence, c.importance, c.status, c.sourceRefIds, c.relatedEntityIds,
          c.proposedBy, c.reviewedAt, c.reviewedByUserId, c.rejectionReason, c.expiresAt,
          c.metadata, c.createdAt, c.updatedAt)
      from MemoryCandidate c
      where c.workspaceId = :workspaceId
      order by c.importance desc nulls last, c.createdAt desc
      """)
  List<MemoryCandidateDetail> findDetailsByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
