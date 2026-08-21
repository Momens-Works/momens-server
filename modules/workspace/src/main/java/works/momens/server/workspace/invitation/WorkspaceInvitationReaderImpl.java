package works.momens.server.workspace.invitation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.WorkspaceInvitationDetail;
import works.momens.server.workspace.WorkspaceInvitationReader;

class WorkspaceInvitationReaderImpl implements WorkspaceInvitationReader {

  private final WorkspaceInvitationRepository repository;
  private final Clock clock;

  WorkspaceInvitationReaderImpl(WorkspaceInvitationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkspaceInvitationDetail> listByWorkspaceId(UUID workspaceId) {
    Instant now = clock.instant();
    return repository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
        .map(invitation -> InvitationDetailMapper.toDetail(invitation, now))
        .toList();
  }
}
