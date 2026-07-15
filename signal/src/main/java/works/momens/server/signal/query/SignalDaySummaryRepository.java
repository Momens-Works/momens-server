package works.momens.server.signal.query;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SignalDaySummaryRepository extends JpaRepository<SignalDaySummary, UUID> {

  // 프로젝트 스코프 UNIQUE(project_id, summary_date)라 하루에 한 건만 존재한다. workspace_id도 함께 걸어
  // 교차 워크스페이스 노출을 쿼리 단계에서 막는다(source_refs·signals 조회와 같은 방어).
  @Query(
      value =
          "SELECT s.* FROM signal_day_summaries s WHERE s.workspace_id = :workspaceId"
              + " AND s.project_id = :projectId AND s.summary_date = :summaryDate"
              + " AND s.deleted_at IS NULL",
      nativeQuery = true)
  Optional<SignalDaySummary> findSummary(
      @Param("workspaceId") UUID workspaceId,
      @Param("projectId") UUID projectId,
      @Param("summaryDate") LocalDate summaryDate);
}
