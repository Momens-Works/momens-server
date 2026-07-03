package works.momens.server.workspace.internal;

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
  @Autowired private WorkspaceRepository workspaceRepository;
  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void isMemberDistinguishesMemberFromNonMember() {
    UUID workspaceId = saveWorkspace("momens-access").getId();
    UUID member = insertUser("member@momens.works");
    UUID stranger = insertUser("stranger@momens.works");
    addMember(workspaceId, member, "member");
    entityManager.flush();

    assertThat(workspaceAccess.isMember(workspaceId, member)).isTrue();
    assertThat(workspaceAccess.isMember(workspaceId, stranger)).isFalse();
  }

  @Test
  void listMembershipsReturnsMembersWithRoles() {
    UUID workspaceId = saveWorkspace("momens-team").getId();
    UUID owner = insertUser("owner@momens.works");
    UUID member = insertUser("member2@momens.works");
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
    UUID workspaceId = saveWorkspace("momens-empty").getId();

    assertThat(workspaceAccess.listMemberships(workspaceId)).isEmpty();
  }

  @Test
  void listUserMembershipsReturnsOnlyWorkspacesUserBelongsToWithRoles() {
    UUID first = saveWorkspace("momens-first").getId();
    UUID second = saveWorkspace("momens-second").getId();
    UUID others = saveWorkspace("momens-others").getId();
    UUID user = insertUser("multi@momens.works");
    UUID otherUser = insertUser("other@momens.works");
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

  private Workspace saveWorkspace(String slug) {
    return workspaceRepository.saveAndFlush(Workspace.builder().name("모멘스").slug(slug).build());
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    workspaceMemberRepository.save(
        WorkspaceMember.builder().workspaceId(workspaceId).userId(userId).role(role).build());
  }

  /** workspace_members.user_id FK를 만족하도록 사용자 데이터를 네이티브 SQL로 삽입합니다. */
  private UUID insertUser(String email) {
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
