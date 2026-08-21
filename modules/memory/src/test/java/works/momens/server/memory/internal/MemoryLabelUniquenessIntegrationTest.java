package works.momens.server.memory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.memory.MemorySeedSql;

/**
 * 미러가 레거시의 라벨 UNIQUE 제약을 그대로 갖는지 검증합니다.
 *
 * <p>레거시 {@code 000006_fe_contract.sql:186-192}가 {@code memory_candidates}·{@code
 * confirmed_memories}에 워크스페이스 범위 부분 UNIQUE 인덱스를 걸어 둡니다. 이 서버가 두 테이블에 쓰기 시작했으므로 미러도 같은 제약을 가져야 합니다.
 * 미러가 더 느슨하면 prod가 거부할 중복 라벨을 local/test가 통과시킵니다(docs/rules/persistence.md 미러 기준).
 *
 * <p>행동과 정의를 함께 봅니다. 앞의 세 테스트는 거부하는 집합을(같은 워크스페이스 중복은 거부, 다른 워크스페이스는 허용, 라벨 없는 행은 공존) 확인하고, 마지막
 * 테스트는 시스템 카탈로그에서 인덱스 정의 자체를 확인합니다.
 *
 * <p>정의를 따로 보는 이유는 행동만으로는 부분 조건이 드러나지 않기 때문입니다. {@code workspace_id}가 {@code NOT NULL}이고 Postgres
 * 기본이 {@code NULLS DISTINCT}라, 조건 없는 UNIQUE 인덱스도 라벨이 없는 행을 여러 개 허용합니다. 즉 부분 조건을 빼도 앞의 세 테스트는 그대로
 * 통과합니다. 미러의 기준은 결과가 같아 보이는가가 아니라 레거시와 같은가이므로 정의를 직접 봅니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("memory 라벨 UNIQUE 제약 통합 테스트")
class MemoryLabelUniquenessIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("같은 워크스페이스에 같은 확정 메모리 라벨을 두 번 넣을 수 없다")
  void rejectsDuplicateMemoryLabelInSameWorkspace() {
    UUID workspaceId = MemorySeedSql.insertWorkspace(entityManager);
    insertMemoryWithLabel(workspaceId, "MEM-0001");

    assertThatThrownBy(() -> insertMemoryWithLabel(workspaceId, "MEM-0001"))
        .hasMessageContaining("idx_confirmed_memories_workspace_label");
  }

  @Test
  @DisplayName("같은 워크스페이스에 같은 후보 라벨을 두 번 넣을 수 없다")
  void rejectsDuplicateCandidateLabelInSameWorkspace() {
    UUID workspaceId = MemorySeedSql.insertWorkspace(entityManager);
    insertCandidateWithLabel(workspaceId, "SUG-0001");

    assertThatThrownBy(() -> insertCandidateWithLabel(workspaceId, "SUG-0001"))
        .hasMessageContaining("idx_memory_candidates_workspace_label");
  }

  @Test
  @DisplayName("워크스페이스가 다르면 같은 라벨을 쓸 수 있다")
  void allowsSameLabelInDifferentWorkspaces() {
    UUID first = MemorySeedSql.insertWorkspace(entityManager);
    UUID second = MemorySeedSql.insertWorkspace(entityManager);

    insertMemoryWithLabel(first, "MEM-0001");
    insertMemoryWithLabel(second, "MEM-0001");

    assertThat(memoryCount("MEM-0001")).isEqualTo(2);
  }

  @Test
  @DisplayName("라벨이 없는 행은 부분 조건 밖이라 여러 개 공존한다")
  void allowsManyRowsWithoutLabel() {
    UUID workspaceId = MemorySeedSql.insertWorkspace(entityManager);

    insertMemoryWithLabel(workspaceId, null);
    insertMemoryWithLabel(workspaceId, null);

    assertThat(memoryCount(null)).isEqualTo(2);
  }

  @Test
  @DisplayName("두 인덱스는 레거시와 같은 부분 조건을 가진 UNIQUE 인덱스다")
  void indexesAreUniqueAndPartialOnLabelPresence() {
    assertThat(indexDefinition("idx_confirmed_memories_workspace_label"))
        .contains("CREATE UNIQUE INDEX")
        .contains("ON public.confirmed_memories USING btree (workspace_id, label)")
        .contains("WHERE (label IS NOT NULL)");
    assertThat(indexDefinition("idx_memory_candidates_workspace_label"))
        .contains("CREATE UNIQUE INDEX")
        .contains("ON public.memory_candidates USING btree (workspace_id, label)")
        .contains("WHERE (label IS NOT NULL)");
  }

  private String indexDefinition(String indexName) {
    return (String)
        entityManager
            .getEntityManager()
            .createNativeQuery("SELECT indexdef FROM pg_indexes WHERE indexname = ?1")
            .setParameter(1, indexName)
            .getSingleResult();
  }

  private void insertMemoryWithLabel(UUID workspaceId, String label) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO confirmed_memories (id, workspace_id, label, memory_type, title)"
                + " VALUES (?1, ?2, ?3, 'DECISION', ?4)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, workspaceId)
        .setParameter(3, label)
        .setParameter(4, "결제 재시도는 3회로 고정한다")
        .executeUpdate();
    entityManager.flush();
  }

  private void insertCandidateWithLabel(UUID workspaceId, String label) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO memory_candidates (id, workspace_id, label, candidate_type, title)"
                + " VALUES (?1, ?2, ?3, 'DECISION', ?4)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, workspaceId)
        .setParameter(3, label)
        .setParameter(4, "결제 재시도 정책 후보")
        .executeUpdate();
    entityManager.flush();
  }

  private long memoryCount(String label) {
    String sql =
        label == null
            ? "SELECT count(*) FROM confirmed_memories WHERE label IS NULL"
            : "SELECT count(*) FROM confirmed_memories WHERE label = ?1";
    var query = entityManager.getEntityManager().createNativeQuery(sql);
    if (label != null) {
      query.setParameter(1, label);
    }
    return ((Number) query.getSingleResult()).longValue();
  }
}
