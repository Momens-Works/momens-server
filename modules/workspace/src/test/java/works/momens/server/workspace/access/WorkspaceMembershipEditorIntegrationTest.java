package works.momens.server.workspace.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.workspace.ChangeMembershipRoleCommand;
import works.momens.server.workspace.RemoveMembershipCommand;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceMembershipEditor;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceSeedSql;

/**
 * 멤버십 변경 public API를 검증합니다.
 *
 * <p>역할 변경과 멤버 제거가 실제 PostgreSQL에 반영되는지, owner 보호와 자기 제거 금지 규칙이 지켜지는지 확인합니다. 요청자의 역할이 충분한지는 이 계층의
 * 책임이 아니므로 검증하지 않습니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceMembershipEditorImpl.class})
class WorkspaceMembershipEditorIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceMembershipEditor workspaceMembershipEditor;
  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("변경한 역할이 저장된다")
  void changeRoleUpdatesStoredRole() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-promote");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "promote@momens.works");
    addMember(workspaceId, member, "member");
    entityManager.flush();
    entityManager.clear();

    workspaceMembershipEditor.changeRole(
        new ChangeMembershipRoleCommand(workspaceId, member, WorkspaceRole.ADMIN));
    entityManager.flush();
    entityManager.clear();

    assertThat(readRole(workspaceId, member)).isEqualTo("admin");
  }

  @Test
  @DisplayName("대상이 owner이면 역할을 변경하지 않는다")
  void changeRoleRejectsOwnerTarget() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-owner");
    UUID owner = WorkspaceSeedSql.insertUser(entityManager, "protected@momens.works");
    addMember(workspaceId, owner, "owner");
    entityManager.flush();

    assertThatThrownBy(
            () ->
                workspaceMembershipEditor.changeRole(
                    new ChangeMembershipRoleCommand(workspaceId, owner, WorkspaceRole.MEMBER)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_PROTECTED);
  }

  @Test
  @DisplayName("대상이 멤버가 아니면 역할을 변경하지 않는다")
  void changeRoleRejectsUserWhoIsNotMember() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-absent");
    UUID stranger = WorkspaceSeedSql.insertUser(entityManager, "stranger@momens.works");
    entityManager.flush();

    assertThatThrownBy(
            () ->
                workspaceMembershipEditor.changeRole(
                    new ChangeMembershipRoleCommand(workspaceId, stranger, WorkspaceRole.ADMIN)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
  }

  @Test
  @DisplayName("멤버를 제거하면 해당 멤버십은 삭제되고 다른 멤버십은 유지된다")
  void removeDeletesMembership() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-remove");
    UUID admin = WorkspaceSeedSql.insertUser(entityManager, "admin@momens.works");
    UUID member = WorkspaceSeedSql.insertUser(entityManager, "leaving@momens.works");
    addMember(workspaceId, admin, "admin");
    addMember(workspaceId, member, "member");
    entityManager.flush();
    entityManager.clear();

    workspaceMembershipEditor.remove(new RemoveMembershipCommand(workspaceId, admin, member));
    entityManager.flush();
    entityManager.clear();

    assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, member)).isEmpty();
    assertThat(workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, admin))
        .isPresent();
  }

  @Test
  @DisplayName("자기 제거 요청은 대상 멤버십을 조회하기 전에 거부한다")
  void removeRejectsSelfBeforeLookingUpMembership() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-self");
    UUID stranger = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                workspaceMembershipEditor.remove(
                    new RemoveMembershipCommand(workspaceId, stranger, stranger)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_SELF_REMOVAL_NOT_ALLOWED);
  }

  @Test
  @DisplayName("대상이 owner이면 멤버십을 제거하지 않는다")
  void removeRejectsOwnerTarget() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-keep-owner");
    UUID admin = WorkspaceSeedSql.insertUser(entityManager, "remover@momens.works");
    UUID owner = WorkspaceSeedSql.insertUser(entityManager, "keep@momens.works");
    addMember(workspaceId, admin, "admin");
    addMember(workspaceId, owner, "owner");
    entityManager.flush();

    assertThatThrownBy(
            () ->
                workspaceMembershipEditor.remove(
                    new RemoveMembershipCommand(workspaceId, admin, owner)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_OWNER_PROTECTED);
  }

  @Test
  @DisplayName("대상이 멤버가 아니면 멤버십을 제거하지 않는다")
  void removeRejectsUserWhoIsNotMember() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "momens-gone");
    UUID admin = WorkspaceSeedSql.insertUser(entityManager, "gone-admin@momens.works");
    UUID stranger = WorkspaceSeedSql.insertUser(entityManager, "gone@momens.works");
    addMember(workspaceId, admin, "admin");
    entityManager.flush();

    assertThatThrownBy(
            () ->
                workspaceMembershipEditor.remove(
                    new RemoveMembershipCommand(workspaceId, admin, stranger)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    workspaceMemberRepository.save(
        WorkspaceMember.builder().workspaceId(workspaceId).userId(userId).role(role).build());
  }

  private String readRole(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository
        .findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow()
        .getRole();
  }
}
