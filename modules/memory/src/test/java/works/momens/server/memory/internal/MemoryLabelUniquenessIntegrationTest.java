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
 * <p>제약의 존재만이 아니라 <b>거부하는 집합</b>까지 봅니다. 부분 조건이 {@code WHERE label IS NOT NULL}이라 라벨이 없는 행은 몇 개든
 * 공존해야 하고, 워크스페이스가 다르면 같은 라벨이 허용되어야 합니다. 이름만 같고 조건이 다른 인덱스는 이 세 가지로 갈립니다.
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
