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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 큐레이터가 제안한 메모리 후보.
 *
 * <p>레거시 {@code momens-api}의 {@code memory_candidates} 테이블과 호환됩니다.
 *
 * <p>조회 projection과 웹 write가 같은 엔티티 매핑을 사용합니다. 조회는 {@link
 * works.momens.server.memory.MemoryCandidateReader}의 DTO projection이 담당하고, write는 memory
 * application API가 담당합니다. prod에서 {@code ddl-auto=validate}가 공유 스키마와의 어긋남을 기동 시점에 잡게 하기 위해서입니다.
 *
 * <p>소프트 삭제 컬럼이 없습니다. 레거시가 후보를 지우는 대신 상태로만 다룹니다.
 *
 * <p>{@code status}와 {@code candidate_type}은 base persistence 단계라 문자열로만 둡니다. {@code status}는 DB
 * CHECK 제약이 허용값을 강제하고, {@code candidate_type}은 레거시에도 제약이 없어 워커가 넣는 값을 그대로 읽습니다.
 */
@Getter
@Entity
@Table(name = "memory_candidates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MemoryCandidate extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column private String label;

  @Column(name = "candidate_type", nullable = false)
  private String candidateType;

  @Column(nullable = false)
  private String title;

  @Column private String summary;

  @Column private String body;

  @Column private Double confidence;

  @Column private Double importance;

  @Column(nullable = false)
  private String status;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "source_ref_ids", columnDefinition = "uuid[]")
  private List<UUID> sourceRefIds;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "related_entity_ids", columnDefinition = "uuid[]")
  private List<UUID> relatedEntityIds;

  @Column(name = "proposed_by", nullable = false)
  private String proposedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "reviewed_by_user_id", columnDefinition = "uuid")
  private UUID reviewedByUserId;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata;
}
