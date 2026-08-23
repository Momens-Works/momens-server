package works.momens.server.memory.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.memory.ConfirmedMemoryDetail;
import works.momens.server.memory.ConfirmedMemoryReader;

@Service
@RequiredArgsConstructor
class ConfirmedMemoryReaderImpl implements ConfirmedMemoryReader {

  private final ConfirmedMemoryRepository confirmedMemoryRepository;

  @Override
  @Transactional(readOnly = true)
  public List<ConfirmedMemoryDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return confirmedMemoryRepository.findDetailsByWorkspaceId(workspaceId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConfirmedMemoryDetail> findDetailsByIds(UUID workspaceId, Collection<UUID> ids) {
    return ids.isEmpty()
        ? List.of()
        : confirmedMemoryRepository.findDetailsByWorkspaceIdAndIdIn(workspaceId, ids);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> findWorkspaceId(UUID memoryId) {
    return confirmedMemoryRepository.findWorkspaceIdById(memoryId);
  }
}
