package works.momens.server.source.connection;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@code source_connections} 테이블의 조회와 저장을 담당합니다.
 *
 * <p>{@code findByWorkspaceIdOrderByCreatedAtDesc}의 정렬 기준은 레거시의 연결 목록 조회 쿼리와 같습니다.
 *
 * <p>{@code findByWorkspaceIdAndSourceTypeAndExternalWorkspaceId}는 기존 연결이 다시 승인되었을 때 갱신할 대상을 조회합니다.
 * 세 컬럼의 조합에 UNIQUE 제약이 없어 여러 행이 조회될 수 있지만, 레거시도 한 건만 갱신하므로 동일한 동작을 유지합니다.
 */
public interface SourceConnectionRepository extends JpaRepository<SourceConnection, UUID> {

  @Query("select c.workspaceId from SourceConnection c where c.id = :connectionId")
  Optional<UUID> findWorkspaceId(UUID connectionId);

  @Modifying
  @Query(
      "update SourceConnection c"
          + " set c.resyncRequestedAt = greatest(c.resyncRequestedAt, :requestedAt),"
          + " c.updatedAt = greatest(c.updatedAt, c.resyncRequestedAt, :requestedAt)"
          + " where c.id = :connectionId and c.workspaceId = :workspaceId")
  int requestResync(UUID connectionId, UUID workspaceId, Instant requestedAt);

  /** 재승인에서 소유하는 컬럼만 갱신하며, 응답 재조회가 최신 값을 읽도록 영속성 컨텍스트를 비웁니다. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update SourceConnection c set c.status = :status,"
          + " c.externalWorkspaceName = :externalWorkspaceName, c.connectedByUserId = :connectedByUserId,"
          + " c.connectedAt = :connectedAt, c.metadata = :metadata, c.disabledAt = null,"
          + " c.updatedAt = greatest(c.updatedAt, :connectedAt) where c.id = :connectionId")
  int reconnect(
      UUID connectionId,
      String status,
      String externalWorkspaceName,
      UUID connectedByUserId,
      Instant connectedAt,
      Map<String, Object> metadata);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from SourceConnection c where c.id = :id and c.workspaceId = :workspaceId")
  Optional<SourceConnection> findForUpdate(UUID id, UUID workspaceId);

  /** worker 소유 통계·동기화 시각과 metadata를 덮어쓰지 않습니다. */
  @Modifying
  @Query(
      "update SourceConnection c set c.status = 'DISABLED', c.disabledAt = :now,"
          + " c.updatedAt = :now where c.id = :id")
  void disable(UUID id, Instant now);

  List<SourceConnection> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

  List<SourceConnection> findByWorkspaceIdAndSourceTypeAndExternalWorkspaceIdOrderByCreatedAtAsc(
      UUID workspaceId, String sourceType, String externalWorkspaceId);
}
