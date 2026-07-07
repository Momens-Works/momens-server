package works.momens.server.project;

import java.util.UUID;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * project 모듈 테스트의 네이티브 SQL 시드 모음.
 *
 * <p>users/workspaces는 다른 모듈 소유 테이블이라 FK를 만족할 행을 SQL로 넣습니다. projects는 코어 {@code ProjectRepository}가
 * package scope 밖인 task 테스트를 위해 함께 둡니다. 같은 헬퍼가 테스트마다 복제되지 않도록 모듈 루트에 한 벌만 둡니다({@code workspace} 모듈의
 * {@code WorkspaceSeedSql}과 같은 방식).
 */
public final class ProjectSeedSql {

  private ProjectSeedSql() {}

  /** users FK(projects.owner_id, tasks.assignee_id)를 만족할 사용자 행을 삽입합니다. */
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

  /** workspaces FK를 만족할 workspace 행을 삽입합니다. */
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

  /** tasks.project_id FK를 만족할 project 행을 삽입합니다. */
  public static UUID insertProject(
      TestEntityManager entityManager, UUID workspaceId, UUID ownerId) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO projects (id, workspace_id, name, owner_id) VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, workspaceId)
        .setParameter(3, "프로젝트")
        .setParameter(4, ownerId)
        .executeUpdate();
    return id;
  }
}
