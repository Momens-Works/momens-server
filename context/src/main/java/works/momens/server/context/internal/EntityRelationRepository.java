package works.momens.server.context.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 태스크와 source_ref의 연결을 조회합니다.
 *
 * <p>연결 조건은 레거시 상수를 그대로 사용합니다(momens-api internal/domain/models.go). 태스크와 관련자료의 연결은 {@code
 * from_entity_type='TASK'}, {@code to_entity_type='SOURCE_OBJECT'}, {@code
 * relation_type='LINKED_TO'}로 정의됩니다. 현재 조회 대상이 이 연결뿐이라 조회 조건을 쿼리에 고정했습니다.
 *
 * <p>관련자료 조합에 필요한 source_ref 식별자와 링크 개수만 조회합니다. 식별자는 projection으로 조회하고, 개수 집계는 {@code GROUP BY}가
 * 필요해 파생 메서드 대신 {@link Query}를 사용했습니다.
 */
interface EntityRelationRepository extends JpaRepository<EntityRelation, UUID> {

  /** 태스크에 연결된 source_ref id를 표시 순서(링크 생성 시각 내림차순, 같으면 링크 id 내림차순)로 조회합니다. */
  @Query(
      """
      select r.toEntityId from EntityRelation r
      where r.workspaceId = :workspaceId
        and r.fromEntityType = 'TASK'
        and r.fromEntityId = :taskId
        and r.relationType = 'LINKED_TO'
        and r.toEntityType = 'SOURCE_OBJECT'
        and r.deletedAt is null
      order by r.createdAt desc, r.id desc
      """)
  List<UUID> findLinkedSourceRefIds(
      @Param("workspaceId") UUID workspaceId, @Param("taskId") UUID taskId);

  /** 여러 태스크의 연결 개수를 한 번에 조회합니다. 연결이 없는 태스크는 결과에 담기지 않습니다. */
  @Query(
      """
      select r.fromEntityId, count(r) from EntityRelation r
      where r.workspaceId = :workspaceId
        and r.fromEntityType = 'TASK'
        and r.fromEntityId in :taskIds
        and r.relationType = 'LINKED_TO'
        and r.toEntityType = 'SOURCE_OBJECT'
        and r.deletedAt is null
      group by r.fromEntityId
      """)
  List<Object[]> countLinkedSourceRefsByTaskId(
      @Param("workspaceId") UUID workspaceId, @Param("taskIds") Collection<UUID> taskIds);
}
