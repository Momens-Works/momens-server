package works.momens.server.outbox.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * append-only outbox 발행 로그(SD-2). {@code id}는 consumer의 polling watermark라 공유 베이스 엔티티의 UUID PK 대신
 * DB {@code bigserial} 시퀀스를 그대로 쓰고, 발행 로그는 수정되지 않으므로 {@code updated_at} 감사 필드도 두지 않는다(persistence
 * 규칙의 append-only 예외, docs/rules/persistence.md).
 */
@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "issued_by", nullable = false)
  private String issuedBy;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "aggregate_type", nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private String aggregateId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Builder
  OutboxEvent(
      String issuedBy,
      UUID workspaceId,
      String aggregateType,
      String aggregateId,
      String eventType,
      String payload,
      String idempotencyKey) {
    this.issuedBy = issuedBy;
    this.workspaceId = workspaceId;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = Instant.now();
  }
}
