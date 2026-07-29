package works.momens.server.signal.query;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SignalEvidenceRepository extends JpaRepository<SignalEvidence, SignalEvidence.Key> {

  /**
   * Signal의 근거 연결을 표시 순서(sort_order 오름차순)로 조회합니다. sort_order는 {@code DEFAULT 0}이라 worker가 순서를 생산하지
   * 않으면 전부 0일 수 있어, 복합 PK 구성원인 source_ref_id를 보조 정렬키로 두어 순서를 결정적으로 고정합니다.
   */
  List<SignalEvidence> findBySignalIdOrderBySortOrderAscSourceRefIdAsc(UUID signalId);

  /** task draft에 사용할 의미 있는 근거 연결만 같은 표시 순서로 제한 조회합니다. */
  @Query(
      """
      SELECT evidence
      FROM SignalEvidence evidence
      WHERE evidence.signalId = :signalId
        AND (
          FUNCTION(
            'regexp_replace',
            COALESCE(evidence.target, ''),
            '[\\u0001-\\u0020]',
            '',
            'g'
          ) <> ''
          OR FUNCTION(
            'regexp_replace',
            COALESCE(evidence.change, ''),
            '[\\u0001-\\u0020]',
            '',
            'g'
          ) <> ''
          OR FUNCTION(
            'regexp_replace',
            COALESCE(evidence.impact, ''),
            '[\\u0001-\\u0020]',
            '',
            'g'
          ) <> ''
        )
      ORDER BY evidence.sortOrder ASC, evidence.sourceRefId ASC
      """)
  List<SignalEvidence> findDraftEvidence(@Param("signalId") UUID signalId, Pageable pageable);
}
