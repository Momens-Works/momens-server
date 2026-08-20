package works.momens.server.project.internal;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.project.BlockerDetail;
import works.momens.server.project.BlockerReader;

@Service
@RequiredArgsConstructor
class BlockerReaderImpl implements BlockerReader {

  private final BlockerRepository blockerRepository;

  @Override
  @Transactional(readOnly = true)
  public List<BlockerDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return blockerRepository.findDetailsByWorkspaceId(workspaceId);
  }
}
