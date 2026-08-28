package works.momens.server.memory.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.memory.MemoryCandidateDetail;
import works.momens.server.memory.MemoryCandidateReader;

@Service
@RequiredArgsConstructor
class MemoryCandidateReaderImpl implements MemoryCandidateReader {

  private final MemoryCandidateRepository memoryCandidateRepository;

  @Override
  @Transactional(readOnly = true)
  public List<MemoryCandidateDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return memoryCandidateRepository.findDetailsByWorkspaceId(workspaceId);
  }
}
