package works.momens.server.memory.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import works.momens.server.memory.ConfirmedMemoryDetail;

interface ConfirmedMemoryRepository extends JpaRepository<ConfirmedMemory, UUID> {

  /**
   * 웹 목록 조회용 DTO projection입니다. 엔티티를 영속성 컨텍스트에 올리지 않고 필요한 컬럼만 읽습니다.
   *
   * <p>소프트 삭제된 메모리를 제외합니다. 상태로는 거르지 않아 {@code INVALIDATED}·{@code ARCHIVED}도 담깁니다(웹 snapshot 계약
   * 4.4).
   *
   * <p>정렬은 레거시와 같은 생성 시각 내림차순입니다. 레거시에 tie-break가 없어 두지 않습니다.
   */
  @Query(
      """
      select new works.momens.server.memory.ConfirmedMemoryDetail(
          m.id, m.workspaceId, m.label, m.memoryType, m.title, m.summary, m.body, m.status,
          m.sourceRefIds, m.relatedEntityIds, m.createdFromCandidateId, m.confirmedByUserId,
          m.confirmedAt, m.validFrom, m.validUntil, m.invalidatedAt, m.invalidatedByUserId,
          m.invalidationReason, m.metadata, m.createdAt, m.updatedAt)
      from ConfirmedMemory m
      where m.workspaceId = :workspaceId
        and m.deletedAt is null
      order by m.createdAt desc
      """)
  List<ConfirmedMemoryDetail> findDetailsByWorkspaceId(@Param("workspaceId") UUID workspaceId);

  @Query(
      """
      select new works.momens.server.memory.ConfirmedMemoryDetail(
          m.id, m.workspaceId, m.label, m.memoryType, m.title, m.summary, m.body, m.status,
          m.sourceRefIds, m.relatedEntityIds, m.createdFromCandidateId, m.confirmedByUserId,
          m.confirmedAt, m.validFrom, m.validUntil, m.invalidatedAt, m.invalidatedByUserId,
          m.invalidationReason, m.metadata, m.createdAt, m.updatedAt)
      from ConfirmedMemory m
      where m.workspaceId = :workspaceId and m.id in :ids and m.deletedAt is null
      order by m.createdAt desc
      """)
  List<ConfirmedMemoryDetail> findDetailsByWorkspaceIdAndIdIn(
      @Param("workspaceId") UUID workspaceId, @Param("ids") Collection<UUID> ids);
}
