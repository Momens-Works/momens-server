package works.momens.server.context.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 태스크와 source_ref의 연결 조회.
 *
 * <p>연결 종류는 레거시 상수를 그대로 씁니다(momens-api internal/domain/models.go): 태스크에서 나가는 연결은 {@code
 * from_entity_type='TASK'}, source_ref를 가리키는 연결은 {@code to_entity_type='SOURCE_OBJECT'}, 관련자료 연결은
 * {@code relation_type='LINKED_TO'}입니다. 지금 읽는 연결이 이 한 종류뿐이라 조건을 파라미터로 열지 않고 쿼리에 고정합니다.
 *
 * <p>엔티티를 구체화하지 않고 id와 개수만 뽑고(첫 쿼리), group by가 필요해(둘째 쿼리) 파생 메서드 대신 {@link Query}를 씁니다.
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
