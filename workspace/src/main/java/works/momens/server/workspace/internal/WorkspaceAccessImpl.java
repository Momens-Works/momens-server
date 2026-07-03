package works.momens.server.workspace.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;

@Service
@RequiredArgsConstructor
class WorkspaceAccessImpl implements WorkspaceAccess {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean isMember(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository.existsByWorkspaceIdAndUserId(workspaceId, userId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkspaceMembership> listMemberships(UUID workspaceId) {
    // 멤버십(userId, role)만 노출하고, 이름과 아바타는 호출하는 쪽에서 user public API를 통해 결합한다(MOM-61).
    return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
        .map(member -> new WorkspaceMembership(member.getUserId(), member.getRole()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> listWorkspaceIds(UUID userId) {
    return workspaceMemberRepository.findByUserId(userId).stream()
        .map(WorkspaceMember::getWorkspaceId)
        .toList();
  }
}
