package works.momens.server.project.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.ProjectOwnerReader;

@Service
@RequiredArgsConstructor
class ProjectOwnerReaderImpl implements ProjectOwnerReader {

  private final ProjectOwnerRepository projectOwnerRepository;

  @Override
  @Transactional(readOnly = true)
  public List<UUID> listOwnerUserIds(UUID projectId) {
    return projectOwnerRepository
        .findByProjectIdInOrderByCreatedAtAscOwnerUserIdAsc(List.of(projectId))
        .stream()
        .map(ProjectOwner::getOwnerUserId)
        .toList();
  }
}
