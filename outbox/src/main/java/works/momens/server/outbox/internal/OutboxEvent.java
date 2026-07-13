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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Outbox 발행 로그 row.
 *
 * <p>append-only라 {@link works.momens.server.common.persistence.BaseEntity}를 상속하지 않는다: PK는 UUID가
 * 아니라 발행 순서를 담는 {@code bigserial}이고, 한 번 쓰면 바뀌지 않아 {@code updated_at}이 없다. 쓰기는 {@link
 * OutboxEventRepository#insertIgnoringConflict}의 네이티브 {@code INSERT ... ON CONFLICT DO NOTHING}으로만
 * 하고, 이 엔티티는 조회·매핑 검증(ddl-auto=validate)에 쓴다. {@code created_at}은 DB DEFAULT가 채운다.
 */
@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "issued_by", nullable = false)
  private String issuedBy;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "aggregate_type")
  private String aggregateType;

  @Column(name = "aggregate_id")
  private String aggregateId;

  @Column(name = "event_type")
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
