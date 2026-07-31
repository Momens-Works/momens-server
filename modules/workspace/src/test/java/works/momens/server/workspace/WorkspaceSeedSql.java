package works.momens.server.workspace;

import java.util.UUID;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * workspace 모듈 테스트의 네이티브 SQL 시드 모음.
 *
 * <p>하위 도메인 테스트는 코어의 {@code WorkspaceRepository}가 package scope 밖이라 FK를 만족할 행을 SQL로 넣습니다. 같은 헬퍼가
 * 테스트마다 복제되지 않도록 모듈 루트에 한 벌만 둡니다.
 */
public final class WorkspaceSeedSql {

  private WorkspaceSeedSql() {}

  /** FK를 만족할 workspace 행을 삽입합니다. id는 클라이언트에서 만들고 감사 필드는 DB default로 채웁니다. */
  public static UUID insertWorkspace(TestEntityManager entityManager, String slug) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery("INSERT INTO workspaces (id, name, slug) VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, "모멘스")
        .setParameter(3, slug)
        .executeUpdate();
    return id;
  }

  /** workspace_members.user_id FK를 만족시킬 사용자 행을 삽입합니다. */
  public static UUID insertUser(TestEntityManager entityManager, String email) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery("INSERT INTO users (id, email, name) VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, email)
        .setParameter(3, "이름")
        .executeUpdate();
    return id;
  }
}
