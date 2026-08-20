package works.momens.server.source.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code source_connections} 테이블의 조회와 저장을 담당합니다.
 *
 * <p>{@code findByWorkspaceIdOrderByCreatedAtDesc}의 정렬 기준은 레거시의 연결 목록 조회 쿼리와 같습니다.
 *
 * <p>{@code findByWorkspaceIdAndSourceTypeAndExternalWorkspaceId}는 기존 연결이 다시 승인되었을 때 갱신할 대상을 조회합니다.
 * 세 컬럼의 조합에 UNIQUE 제약이 없어 여러 행이 조회될 수 있지만, 레거시도 한 건만 갱신하므로 동일한 동작을 유지합니다.
 */
public interface SourceConnectionRepository extends JpaRepository<SourceConnection, UUID> {

  List<SourceConnection> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

  List<SourceConnection> findByWorkspaceIdAndSourceTypeAndExternalWorkspaceIdOrderByCreatedAtAsc(
      UUID workspaceId, String sourceType, String externalWorkspaceId);
}
