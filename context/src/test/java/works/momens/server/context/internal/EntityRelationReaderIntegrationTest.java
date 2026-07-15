package works.momens.server.context.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.context.EntityRelationReader;

/**
 * 엔티티 연결 조회 public API를 검증합니다.
 *
 * <p>실제 PostgreSQL(Testcontainers)를 사용해 source_ref 연결 조회 계약을 검증합니다. 표시 순서와 배치 개수 조회, 소프트 삭제된 연결 제외,
 * 다른 워크스페이스와 연결 종류 제외를 함께 확인합니다.
 *
 * <p>entity_relations는 레거시가 소유하는 읽기 전용 테이블이라 fixture는 엔티티를 사용하지 않고 네이티브 SQL로 구성합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EntityRelationReaderImpl.class)
@DisplayName("EntityRelationReader 통합 테스트")
class EntityRelationReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private EntityRelationReader entityRelationReader;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("태스크에 연결된 source_ref id를 링크 생성 최신순으로 반환한다")
  void findLinkedSourceRefIdsReturnsNewestLinkFirst() {
    UUID workspaceId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID earlier = UUID.randomUUID();
    UUID later = UUID.randomUUID();
    insertLink(workspaceId, taskId, earlier, Instant.parse("2026-07-01T00:00:00Z"), null);
    insertLink(workspaceId, taskId, later, Instant.parse("2026-07-02T00:00:00Z"), null);

    List<UUID> ids = entityRelationReader.findLinkedSourceRefIds(workspaceId, taskId);

    assertThat(ids).containsExactly(later, earlier);
  }

  @Test
  @DisplayName("소프트 삭제된 링크와 다른 워크스페이스의 링크는 제외한다")
  void findLinkedSourceRefIdsExcludesDeletedAndOtherWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID live = UUID.randomUUID();
    insertLink(workspaceId, taskId, live, Instant.parse("2026-07-01T00:00:00Z"), null);
    insertLink(
        workspaceId,
        taskId,
        UUID.randomUUID(),
        Instant.parse("2026-07-02T00:00:00Z"),
        Instant.now());
    insertLink(
        UUID.randomUUID(), taskId, UUID.randomUUID(), Instant.parse("2026-07-03T00:00:00Z"), null);

    List<UUID> ids = entityRelationReader.findLinkedSourceRefIds(workspaceId, taskId);

    assertThat(ids).containsExactly(live);
  }

  @Test
  @DisplayName("태스크와 source_ref 연결이 아닌 행은 제외한다")
  void findLinkedSourceRefIdsExcludesOtherRelationKinds() {
    UUID workspaceId = UUID.randomUUID();
    UUID taskId = UUID.randomUUID();
    UUID linked = UUID.randomUUID();
    insertLink(workspaceId, taskId, linked, Instant.parse("2026-07-01T00:00:00Z"), null);
    // 같은 태스크의 memory 연결과, 다른 relation_type은 관련자료가 아니다.
    insertRelation(
        workspaceId,
        "TASK",
        taskId,
        "LINKED_TO",
        "MEMORY",
        UUID.randomUUID(),
        Instant.parse("2026-07-02T00:00:00Z"),
        null);
    insertRelation(
        workspaceId,
        "TASK",
        taskId,
        "DERIVED_FROM",
        "SOURCE_OBJECT",
        UUID.randomUUID(),
        Instant.parse("2026-07-03T00:00:00Z"),
        null);
    // 같은 id를 가진 다른 종류의 엔티티에서 나가는 연결도 이 태스크의 관련자료가 아니다.
    insertRelation(
        workspaceId,
        "MEMORY",
        taskId,
        "LINKED_TO",
        "SOURCE_OBJECT",
        UUID.randomUUID(),
        Instant.parse("2026-07-04T00:00:00Z"),
        null);

    List<UUID> ids = entityRelationReader.findLinkedSourceRefIds(workspaceId, taskId);

    assertThat(ids).containsExactly(linked);
  }

  @Test
  @DisplayName("여러 태스크의 연결 개수를 한 번에 반환하고 연결이 없는 태스크는 담지 않는다")
  void countLinkedSourceRefsReturnsCountsPerTask() {
    UUID workspaceId = UUID.randomUUID();
    UUID twoLinks = UUID.randomUUID();
    UUID oneLink = UUID.randomUUID();
    UUID noLink = UUID.randomUUID();
    insertLink(
        workspaceId, twoLinks, UUID.randomUUID(), Instant.parse("2026-07-01T00:00:00Z"), null);
    insertLink(
        workspaceId, twoLinks, UUID.randomUUID(), Instant.parse("2026-07-02T00:00:00Z"), null);
    insertLink(
        workspaceId, oneLink, UUID.randomUUID(), Instant.parse("2026-07-01T00:00:00Z"), null);

    Map<UUID, Integer> counts =
        entityRelationReader.countLinkedSourceRefs(workspaceId, List.of(twoLinks, oneLink, noLink));

    assertThat(counts).containsOnly(Map.entry(twoLinks, 2), Map.entry(oneLink, 1));
  }

  @Test
  @DisplayName("빈 태스크 목록이면 DB 조회 없이 빈 결과를 반환한다")
  void countLinkedSourceRefsIsEmptyForEmptyTaskIds() {
    assertThat(entityRelationReader.countLinkedSourceRefs(UUID.randomUUID(), List.of())).isEmpty();
  }

  private void insertLink(
      UUID workspaceId, UUID taskId, UUID sourceRefId, Instant createdAt, Instant deletedAt) {
    insertRelation(
        workspaceId,
        "TASK",
        taskId,
        "LINKED_TO",
        "SOURCE_OBJECT",
        sourceRefId,
        createdAt,
        deletedAt);
  }

  private void insertRelation(
      UUID workspaceId,
      String fromEntityType,
      UUID fromEntityId,
      String relationType,
      String toEntityType,
      UUID toEntityId,
      Instant createdAt,
      Instant deletedAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO entity_relations (id, workspace_id, from_entity_type, from_entity_id,"
                + " relation_type, to_entity_type, to_entity_id, created_at, deleted_at)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, workspaceId)
        .setParameter(3, fromEntityType)
        .setParameter(4, fromEntityId)
        .setParameter(5, relationType)
        .setParameter(6, toEntityType)
        .setParameter(7, toEntityId)
        .setParameter(8, createdAt)
        .setParameter(9, deletedAt)
        .executeUpdate();
    entityManager.clear();
  }
}
