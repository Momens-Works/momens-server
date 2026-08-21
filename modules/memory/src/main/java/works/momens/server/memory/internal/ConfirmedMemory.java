package works.momens.server.memory.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 리뷰를 거쳐 확정된 메모리.
 *
 * <p>레거시 {@code momens-api}의 {@code confirmed_memories} 테이블과 호환됩니다. snapshot 응답 키는 {@code memories}
 * 지만 테이블 이름은 레거시를 따릅니다. prod는 공유 DB라 이름을 고를 여지가 없습니다.
 *
 * <p>이 엔티티를 거치는 쓰기가 없어 전체를 {@link Immutable}로 둡니다. 조회는 {@link
 * works.momens.server.memory.ConfirmedMemoryReader}의 DTO projection이, 쓰기(MOM-0869)는 네이티브 SQL이
 * 담당합니다. 레거시가 소유한 테이블이라 우리가 채우는 컬럼만 정확히 건드려야 하기 때문입니다. 그래도 매핑해 두는 것은 prod에서 {@code
 * ddl-auto=validate}가 공유 스키마와의 어긋남을 기동 시점에 잡게 하기 위해서입니다.
 *
 * <p>후보와 달리 {@code deleted_at}이 있습니다. 레거시 삭제(H094)가 상태를 {@code DELETED}로 바꾸면서 이 컬럼을 함께 채우고, 모든 조회가
 * 이 컬럼으로 거릅니다. 상태값 {@code DELETED}가 아니라 이 컬럼이 조회 기준입니다.
 */
@Getter
@Entity
@Immutable
@Table(name = "confirmed_memories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ConfirmedMemory extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column private String label;

  @Column(name = "memory_type", nullable = false)
  private String memoryType;

  @Column(nullable = false)
  private String title;

  @Column private String summary;

  @Column private String body;

  @Column(nullable = false)
  private String status;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "source_ref_ids", columnDefinition = "uuid[]")
  private List<UUID> sourceRefIds;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "related_entity_ids", columnDefinition = "uuid[]")
  private List<UUID> relatedEntityIds;

  @Column(name = "created_from_candidate_id", columnDefinition = "uuid")
  private UUID createdFromCandidateId;

  @Column(name = "confirmed_by_user_id", columnDefinition = "uuid")
  private UUID confirmedByUserId;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "valid_from")
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "invalidated_at")
  private Instant invalidatedAt;

  @Column(name = "invalidated_by_user_id", columnDefinition = "uuid")
  private UUID invalidatedByUserId;

  @Column(name = "invalidation_reason")
  private String invalidationReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
