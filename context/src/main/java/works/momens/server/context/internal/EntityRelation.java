package works.momens.server.context.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 엔티티 간 연결(entity_relations).
 *
 * <p>레거시 {@code momens-api}가 소유하는 {@code entity_relations} 테이블(000002_retrieval_projection.sql)을
 * <b>읽기 전용</b>으로 매핑합니다. 이 서버는 링크를 쓰지 않으므로 {@code BaseEntity}(앱 생성 식별자와 감사)를 상속하지 않고 {@link
 * Immutable}로 둡니다. 태스크 관련자료 조회에 필요한 컬럼만 매핑하며, 매핑하지 않은 레거시 컬럼(weight, source_ref_ids, metadata 등)은
 * {@code ddl-auto=validate}에서 무시됩니다.
 *
 * <p>운영에서는 공유 DB의 실제 레거시 테이블을 validate로 검증하고, local/test는 별도 DB에 이 컬럼들만 만든 미러를 사용합니다([데이터]
 * docs/rules/persistence.md).
 *
 * <p>태스크와 source_ref의 연결은 {@code from_entity_type='TASK'}, {@code to_entity_type='SOURCE_OBJECT'},
 * {@code relation_type='LINKED_TO'}인 행이고 {@code to_entity_id}가 source_ref id입니다(레거시 relation 패키지).
 *
 * <p>조회는 id와 개수만 뽑으므로 엔티티를 구체화하지 않습니다. 게터가 필요 없어 두지 않습니다.
 */
@Entity
@Immutable
@Table(name = "entity_relations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class EntityRelation {

  @Id
  @Column(columnDefinition = "uuid")
  private UUID id;

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "from_entity_type", nullable = false)
  private String fromEntityType;

  @Column(name = "from_entity_id", nullable = false, columnDefinition = "uuid")
  private UUID fromEntityId;

  @Column(name = "relation_type", nullable = false)
  private String relationType;

  @Column(name = "to_entity_type", nullable = false)
  private String toEntityType;

  @Column(name = "to_entity_id", nullable = false, columnDefinition = "uuid")
  private UUID toEntityId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
