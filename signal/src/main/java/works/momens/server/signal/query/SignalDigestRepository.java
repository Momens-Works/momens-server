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
   */
  @Query(
      """
      select d from SignalDigest d
      where d.projectId = :projectId
        and d.createdAt >= :from
        and d.createdAt < :toExclusive
      order by d.createdAt desc, d.id desc
      """)
  List<SignalDigest> findByProjectIdAndCreatedRange(
      @Param("projectId") UUID projectId,
      @Param("from") Instant from,
      @Param("toExclusive") Instant toExclusive,
      Limit limit);
}
