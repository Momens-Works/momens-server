package works.momens.server.outbox.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  List<OutboxEvent> findByIdGreaterThanAndCreatedAtLessThanEqualOrderByIdAsc(
      long afterId, Instant createdBefore, Limit limit);

  @Query("select coalesce(max(e.id), 0) from OutboxEvent e where e.createdAt <= :createdBefore")
  long findMaxIdCreatedBefore(@Param("createdBefore") Instant createdBefore);

  /**
   * {@code idempotency_key UNIQUE} 위반은 저장이 아니라 무시로 처리한다(SD-3). JPA {@code save()}는 이 시맨틱을 표현할 수 없어
   * native insert로 직접 {@code ON CONFLICT DO NOTHING}을 건다.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO outbox_events "
              + "(issued_by, workspace_id, aggregate_type, aggregate_id, event_type, payload, "
              + "idempotency_key, created_at) "
              + "VALUES (:issuedBy, :workspaceId, :aggregateType, :aggregateId, :eventType, "
              + "CAST(:payload AS jsonb), :idempotencyKey, NOW()) "
              + "ON CONFLICT (idempotency_key) DO NOTHING",
      nativeQuery = true)
  void insertIgnoringConflict(
      @Param("issuedBy") String issuedBy,
      @Param("workspaceId") UUID workspaceId,
      @Param("aggregateType") String aggregateType,
      @Param("aggregateId") String aggregateId,
      @Param("eventType") String eventType,
      @Param("payload") String payload,
      @Param("idempotencyKey") String idempotencyKey);
}
