package works.momens.server.project.milestone.internal;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.milestone.MilestoneDirectory;

@Service
@RequiredArgsConstructor
class MilestoneDirectoryImpl implements MilestoneDirectory {

  private final MilestoneRepository milestoneRepository;

  @Override
  @Transactional(readOnly = true)
  public boolean existsInProject(UUID milestoneId, UUID projectId) {
    return milestoneRepository.existsByIdAndProjectIdAndDeletedAtIsNull(milestoneId, projectId);
  }
}
