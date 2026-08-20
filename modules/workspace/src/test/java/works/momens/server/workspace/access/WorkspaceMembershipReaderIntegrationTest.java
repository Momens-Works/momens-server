package works.momens.server.workspace.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
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
import works.momens.server.workspace.WorkspaceMembershipDetail;
import works.momens.server.workspace.WorkspaceMembershipReader;
import works.momens.server.workspace.WorkspaceSeedSql;

/**
 * 멤버십 조회 public API를 검증합니다.
 *
 * <p>실제 PostgreSQL에서 역할과 감사 시각을 함께 반환하는지, 다른 워크스페이스의 멤버가 결과에 포함되지 않는지 확인합니다. 정렬은 이 계층의 책임이 아니므로
 * 검증하지 않습니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceMembershipReaderImpl.class})
class WorkspaceMembershipReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceMembershipReader workspaceMembershipReader;
  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("역할과 멤버가 된 시각을 함께 반환한다")
  void listDetailsReturnsRoleAndAuditFields() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-roster");
    UUID owner = WorkspaceSeedSql.insertUser(entityManager, "owner@momens.works");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "member@momens.works");
    addMember(workspaceId, owner, "owner");
    addMember(workspaceId, member, "member");
    entityManager.flush();

    List<WorkspaceMembershipDetail> details =
        workspaceMembershipReader.listDetailsByWorkspaceId(workspaceId);

    assertThat(details).hasSize(2);
    assertThat(details)
        .extracting(WorkspaceMembershipDetail::userId, WorkspaceMembershipDetail::role)
        .containsExactlyInAnyOrder(tuple(owner, "owner"), tuple(member, "member"));
    assertThat(details)
        .allSatisfy(
            detail -> {
              assertThat(detail.createdAt()).isNotNull();
              assertThat(detail.updatedAt()).isNotNull();
            });
  }

  @Test
  @DisplayName("요청한 워크스페이스의 멤버만 반환한다")
  void listDetailsReturnsOnlyMembersOfGivenWorkspace() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-mine");
    UUID otherWorkspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-other");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "mine@momens.works");
    UUID otherMember = WorkspaceSeedSql.insertUser(entityManager, "other@momens.works");
    addMember(workspaceId, member, "member");
    addMember(otherWorkspaceId, otherMember, "member");
    entityManager.flush();

    assertThat(workspaceMembershipReader.listDetailsByWorkspaceId(workspaceId))
        .extracting(WorkspaceMembershipDetail::userId)
        .containsExactly(member);
  }

  @Test
  @DisplayName("멤버가 없으면 빈 목록을 반환한다")
  void listDetailsIsEmptyForWorkspaceWithoutMembers() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-nobody");

    assertThat(workspaceMembershipReader.listDetailsByWorkspaceId(workspaceId)).isEmpty();
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    workspaceMemberRepository.save(
        WorkspaceMember.builder().workspaceId(workspaceId).userId(userId).role(role).build());
  }
}
