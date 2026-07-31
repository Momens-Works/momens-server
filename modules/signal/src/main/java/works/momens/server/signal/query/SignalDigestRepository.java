package works.momens.server.signal.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SignalDigestRepository extends JpaRepository<SignalDigest, UUID> {

  /**
   * 생성 시각이 {@code [from, toExclusive)} 범위인 요약 문단을 최신순으로 조회합니다. 같은 범위에 여러 건이면 민수가 다시 만든 것이므로 최신이
   * 이깁니다. 정렬 동률은 id 내림차순으로 고정해 결과가 흔들리지 않게 합니다(signals 목록과 같은 기준).
   *
   * <p>workspace_id는 멤버십 검사와 별개로 교차 워크스페이스 노출을 쿼리 단계에서 막는 방어 스코프이고, 소프트 삭제는 없는 것으로
   * 취급합니다(source_refs·signals 조회와 같은 방어).
   */
  @Query(
      """
      select d from SignalDigest d
      where d.workspaceId = :workspaceId
        and d.projectId = :projectId
        and d.createdAt >= :from
        and d.createdAt < :toExclusive
        and d.deletedAt is null
      order by d.createdAt desc, d.id desc
      """)
  List<SignalDigest> findByProjectIdAndCreatedRange(
      @Param("workspaceId") UUID workspaceId,
      @Param("projectId") UUID projectId,
      @Param("from") Instant from,
      @Param("toExclusive") Instant toExclusive,
      Limit limit);
}
