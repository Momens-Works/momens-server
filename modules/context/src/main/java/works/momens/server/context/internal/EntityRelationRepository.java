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
 * <p>엔티티를 구체화하지 않고 태스크 식별자와 source_ref 식별자 쌍만 조회하므로 파생 메서드 대신 {@link Query}를 사용했습니다.
 */
interface EntityRelationRepository extends JpaRepository<EntityRelation, UUID> {

  /**
   * 태스크에 연결된 source_ref 식별자를 (태스크 id, source_ref id) 쌍으로 조회합니다. 표시 순서(링크 생성 시각 내림차순, 같으면 링크 id
   * 내림차순)로 정렬해 반환하므로, 호출하는 쪽이 태스크별로 묶으면 순서가 유지됩니다.
   */
  @Query(
      """
      select r.fromEntityId, r.toEntityId from EntityRelation r
      where r.workspaceId = :workspaceId
        and r.fromEntityType = 'TASK'
        and r.fromEntityId in :taskIds
        and r.relationType = 'LINKED_TO'
        and r.toEntityType = 'SOURCE_OBJECT'
        and r.deletedAt is null
      order by r.createdAt desc, r.id desc
      """)
  List<Object[]> findLinkedSourceRefIdsByTaskIds(
      @Param("workspaceId") UUID workspaceId, @Param("taskIds") Collection<UUID> taskIds);

  @Query(
      """
      select r.toEntityType, r.toEntityId from EntityRelation r
      where r.workspaceId = :workspaceId and r.fromEntityType = 'TASK' and r.fromEntityId = :taskId
        and r.relationType = 'LINKED_TO' and r.toEntityType in ('MEMORY', 'SOURCE_OBJECT')
        and r.deletedAt is null
      """)
  List<Object[]> findContextLinks(
      @Param("workspaceId") UUID workspaceId, @Param("taskId") UUID taskId);
}
