package works.momens.server.signal.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SignalRepository extends JpaRepository<Signal, UUID> {

  Optional<Signal> findByIdAndDeletedAtIsNull(UUID id);

  // 미처리 = signal_actions에 행이 없는 Signal(원장 존재 여부만 확인, MOM-65가 쓴다). 정렬은 생성
  // 시각 내림차순이고, worker가 evidence와 배치로 같이 insert해(D1) 동률이 날 수 있어 id 내림차순으로
  // 순서를 고정한다(docs/spec/mobile-api.md).
  @Query(
      value =
          "SELECT s.* FROM signals s WHERE s.project_id = :projectId AND s.deleted_at IS NULL"
              + " AND NOT EXISTS (SELECT 1 FROM signal_actions a WHERE a.signal_id = s.id)"
              + " ORDER BY s.created_at DESC, s.id DESC",
      nativeQuery = true)
  List<Signal> findUnprocessedByProjectId(@Param("projectId") UUID projectId);
}
