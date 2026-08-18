package works.momens.server.workspace.access;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;

@Service
@RequiredArgsConstructor
class WorkspaceRoleReaderImpl implements WorkspaceRoleReader {

  private final WorkspaceMemberRepository workspaceMemberRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<WorkspaceRole> roleOf(UUID workspaceId, UUID userId) {
    return workspaceMemberRepository
        .findByWorkspaceIdAndUserId(workspaceId, userId)
        .map(WorkspaceMember::getRole)
        .flatMap(WorkspaceRole::from);
  }
}
