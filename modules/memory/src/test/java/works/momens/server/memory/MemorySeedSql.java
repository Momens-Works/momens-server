package works.momens.server.memory;

import java.util.UUID;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * memory 모듈 테스트의 네이티브 SQL 시드 모음.
 *
 * <p>같은 헬퍼가 테스트마다 복제되지 않도록 모듈 루트에 한 벌만 둡니다(project 모듈의 {@code ProjectSeedSql}과 같은 방식).
 *
 * <p>워크스페이스와 사용자 행은 만들지 않습니다. 미러가 다른 모듈 테이블로 나가는 FK를 두지 않아 아무 UUID나 그대로 쓸 수 있습니다.
 *
 * <p>후보·메모리 자체는 이 서버에 쓰기 경로가 전혀 없어(쓰기는 MOM-0869) 각 테스트가 레거시 워커처럼 네이티브 SQL로 넣습니다.
 */
public final class MemorySeedSql {

  private MemorySeedSql() {}

  /**
   * {@code confirmed_memories.created_from_candidate_id} FK를 만족할 후보 행을 넣습니다.
   *
   * <p>메모리 테스트가 그 FK 하나 때문에 후보를 만들어야 할 때만 씁니다. 후보 자체의 조회 계약은 {@code
   * MemoryCandidateReaderIntegrationTest}가 검증합니다.
   */
  public static UUID insertCandidate(TestEntityManager entityManager, UUID workspaceId) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO memory_candidates (id, workspace_id, candidate_type, title)"
                + " VALUES (?1, ?2, 'DECISION', ?3)")
        .setParameter(1, id)
        .setParameter(2, workspaceId)
        .setParameter(3, "확정된 후보")
        .executeUpdate();
    return id;
  }

  /**
   * {@code uuid[]} 컬럼에 바인딩할 PostgreSQL 배열 리터럴을 만듭니다.
   *
   * <p>네이티브 SQL에서 {@code CAST(? AS uuid[])}로 받습니다. 인자가 없으면 빈 배열 리터럴이라 SQL {@code NULL}과 구분됩니다. 조회
   * 결과에서 둘을 구분하지 않는다는 계약을 그 차이로 검증할 수 있습니다.
   */
  public static String uuidArray(UUID... ids) {
    StringBuilder literal = new StringBuilder("{");
    for (int i = 0; i < ids.length; i++) {
      if (i > 0) {
        literal.append(',');
      }
      literal.append(ids[i]);
    }
    return literal.append('}').toString();
  }
}
