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
 * 엔티티 간 연결(entity_relations)입니다.
 *
 * <p>레거시 {@code momens-api}가 소유하는 {@code entity_relations} 테이블(000002_retrieval_projection.sql)을 읽기
 * 전용으로 매핑합니다. 이 서버는 링크를 생성하거나 수정하지 않으므로 {@code BaseEntity}(앱 생성 식별자와 감사)를 상속하지 않고 {@link
 * Immutable}로 선언했습니다.
 *
 * <p>태스크 관련자료 조회에 필요한 컬럼만 매핑합니다. 레거시 컬럼 중 {@code weight}, {@code source_ref_ids}, {@code metadata}
 * 등 매핑하지 않는 컬럼은 {@code ddl-auto=validate} 대상에서 제외됩니다.
 *
 * <p>운영에서는 공유 DB의 레거시 테이블을 {@code ddl-auto=validate}로 검증하고, local/test에서는 별도 DB에 필요한 컬럼만 생성해
 * 사용합니다([데이터] docs/rules/persistence.md).
 *
 * <p>태스크와 {@code source_ref}의 연결은 {@code from_entity_type='TASK'}, {@code
 * to_entity_type='SOURCE_OBJECT'}, {@code relation_type='LINKED_TO'}인 행이며, {@code to_entity_id}가
 * {@code source_ref}의 식별자입니다(레거시 relation 패키지 기준).
 *
 * <p>조회에는 연결 id와 개수만 사용하므로 엔티티를 구체화하지 않습니다. 읽기 전용 엔티티라 게터도 두지 않습니다.
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
