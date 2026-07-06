package works.momens.server.workspace.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.workspace.UserWorkspaceMembership;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;
import works.momens.server.workspace.WorkspaceSeedSql;

/**
 * 멤버십 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 isMember가 멤버와 비멤버를 구분하는지, listMemberships가 workspace의 멤버를
 * 역할과 함께 반환하는지 확인합니다. 멤버십은 readOnly 트랜잭션에서 읽으므로, 조회 전에 저장한 데이터가 DB에 반영되도록 flush합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceAccessImpl.class})
class WorkspaceAccessIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceAccess workspaceAccess;
  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void isMemberDistinguishesMemberFromNonMember() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-access");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "member@momens.works");
    UUID stranger = WorkspaceSeedSql.insertUser(entityManager, "stranger@momens.works");
    addMember(workspaceId, member, "member");
    entityManager.flush();

    assertThat(workspaceAccess.isMember(workspaceId, member)).isTrue();
    assertThat(workspaceAccess.isMember(workspaceId, stranger)).isFalse();
  }

  @Test
  void listMembershipsReturnsMembersWithRoles() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-team");
    UUID owner = WorkspaceSeedSql.insertUser(entityManager, "owner@momens.works");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "member2@momens.works");
    addMember(workspaceId, owner, "owner");
    addMember(workspaceId, member, "member");
    entityManager.flush();

    List<WorkspaceMembership> memberships = workspaceAccess.listMemberships(workspaceId);

    assertThat(memberships)
        .containsExactlyInAnyOrder(
            new WorkspaceMembership(owner, "owner"), new WorkspaceMembership(member, "member"));
  }

  @Test
  void listMembershipsIsEmptyForWorkspaceWithoutMembers() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-empty");

    assertThat(workspaceAccess.listMemberships(workspaceId)).isEmpty();
  }

  @Test
  void listUserMembershipsReturnsOnlyWorkspacesUserBelongsToWithRoles() {
    UUID first = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-first");
    UUID second = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-second");
    UUID others = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-others");
    UUID user = WorkspaceSeedSql.insertUser(entityManager, "multi@momens.works");
    UUID otherUser = WorkspaceSeedSql.insertUser(entityManager, "other@momens.works");
    addMember(first, user, "owner");
    addMember(second, user, "member");
    addMember(others, otherUser, "owner");
    entityManager.flush();

    assertThat(workspaceAccess.listUserMemberships(user))
        .containsExactlyInAnyOrder(
            new UserWorkspaceMembership(first, "owner"),
            new UserWorkspaceMembership(second, "member"));
    assertThat(workspaceAccess.listUserMemberships(UUID.randomUUID())).isEmpty();
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    workspaceMemberRepository.save(
        WorkspaceMember.builder().workspaceId(workspaceId).userId(userId).role(role).build());
  }
}
