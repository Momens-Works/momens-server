package works.momens.server.workspace.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;
import works.momens.server.workspace.WorkspaceSeedSql;

/** 멤버십에 저장된 역할을 그대로 조회하는지 실제 PostgreSQL을 사용해 검증합니다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceRoleReaderImpl.class})
class WorkspaceRoleReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceRoleReader workspaceRoleReader;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("멤버십에 저장된 역할을 그대로 반환한다")
  void readsStoredRole() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "role-it-workspace");
    UUID ownerId = WorkspaceSeedSql.insertUser(entityManager, "role-it-owner@momens.works");
    UUID memberId = WorkspaceSeedSql.insertUser(entityManager, "role-it-member@momens.works");
    addMember(workspaceId, ownerId, "owner");
    addMember(workspaceId, memberId, "member");
    entityManager.flush();
    entityManager.clear();

    assertThat(workspaceRoleReader.roleOf(workspaceId, ownerId)).contains(WorkspaceRole.OWNER);
    assertThat(workspaceRoleReader.roleOf(workspaceId, memberId)).contains(WorkspaceRole.MEMBER);
  }

  @Test
  @DisplayName("멤버가 아니면 빈 Optional을 반환한다")
  void isEmptyWhenUserIsNotMember() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "role-it-stranger");
    UUID strangerId = WorkspaceSeedSql.insertUser(entityManager, "role-it-stranger@momens.works");
    entityManager.flush();
    entityManager.clear();

    assertThat(workspaceRoleReader.roleOf(workspaceId, strangerId)).isEmpty();
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?1, ?2, ?3)")
        .setParameter(1, workspaceId)
        .setParameter(2, userId)
        .setParameter(3, role)
        .executeUpdate();
  }
}
