package works.momens.server.workspace.access;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.workspace.ChangeMembershipRoleCommand;
import works.momens.server.workspace.RemoveMembershipCommand;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceMembershipEditor;
import works.momens.server.workspace.WorkspaceRole;

/**
 * 멤버십 변경 구현.
 *
 * <p>역할 변경은 조회한 엔티티의 값을 바꾸고 JPA 더티 체킹으로 반영합니다. 멤버 제거는 엔티티를 삭제합니다. owner 보호와 자기 제거 금지 규칙은 이 구현에서
 * 확인합니다.
 */
@Service
@RequiredArgsConstructor
class WorkspaceMembershipEditorImpl implements WorkspaceMembershipEditor {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  @Override
  @Transactional
  public void changeRole(ChangeMembershipRoleCommand command) {
    WorkspaceMember member = requireMember(command.workspaceId(), command.targetUserId());
    requireNotOwner(member);
    member.changeRole(command.role());
  }

  @Override
  @Transactional
  public void remove(RemoveMembershipCommand command) {
    if (command.userId().equals(command.targetUserId())) {
      throw new BusinessException(
          WorkspaceErrorCode.WORKSPACE_SELF_REMOVAL_NOT_ALLOWED,
          Map.of("user_id", command.targetUserId().toString()));
    }
    WorkspaceMember member = requireMember(command.workspaceId(), command.targetUserId());
    requireNotOwner(member);
    workspaceMemberRepository.delete(member);
  }

  private WorkspaceMember requireMember(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository
        .findByWorkspaceIdAndUserId(workspaceId, userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    WorkspaceErrorCode.WORKSPACE_MEMBER_NOT_FOUND,
                    Map.of("workspace_id", workspaceId.toString(), "user_id", userId.toString())));
  }

  private void requireNotOwner(WorkspaceMember member) {
    if (WorkspaceRole.OWNER.value().equals(member.getRole())) {
      throw new BusinessException(
          WorkspaceErrorCode.WORKSPACE_OWNER_PROTECTED,
          Map.of("user_id", member.getUserId().toString()));
    }
  }
}
