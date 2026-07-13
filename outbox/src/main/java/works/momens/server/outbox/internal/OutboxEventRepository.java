package works.momens.server.outbox.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * outbox row를 append한다. 멱등키가 이미 있으면 아무것도 하지 않아, 같은 이벤트 재발행이 중복 row를 만들지 않는다(SD-3). 호출하는 쪽의 트랜잭션에
   * 합류하므로 {@code payload}는 문자열로 받아 {@code jsonb}로 캐스트한다.
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO outbox_events
              (issued_by, workspace_id, aggregate_type, aggregate_id, event_type, payload, idempotency_key)
          VALUES
              (:issuedBy, :workspaceId, :aggregateType, :aggregateId, :eventType, CAST(:payload AS jsonb), :idempotencyKey)
          ON CONFLICT (idempotency_key) DO NOTHING
          """,
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
