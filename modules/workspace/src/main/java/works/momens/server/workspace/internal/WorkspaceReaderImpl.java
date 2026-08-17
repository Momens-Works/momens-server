package works.momens.server.workspace.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceReader;

@Service
@RequiredArgsConstructor
class WorkspaceReaderImpl implements WorkspaceReader {

  private final WorkspaceRepository workspaceRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<WorkspaceDetail> findById(UUID workspaceId) {
    return workspaceRepository.findById(workspaceId).map(WorkspaceReaderImpl::toDetail);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkspaceDetail> listByMemberUserId(UUID userId) {
    return workspaceRepository.findByMemberUserId(userId).stream()
        .map(WorkspaceReaderImpl::toDetail)
        .toList();
  }

  private static WorkspaceDetail toDetail(Workspace workspace) {
    return new WorkspaceDetail(
        workspace.getId(),
        workspace.getName(),
        workspace.getSlug(),
        workspace.getDescription(),
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }
}
