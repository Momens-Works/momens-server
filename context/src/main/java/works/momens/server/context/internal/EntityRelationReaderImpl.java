package works.momens.server.context.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.context.EntityRelationReader;

@Service
@RequiredArgsConstructor
class EntityRelationReaderImpl implements EntityRelationReader {

  private final EntityRelationRepository entityRelationRepository;

  @Override
  @Transactional(readOnly = true)
  public List<UUID> findLinkedSourceRefIds(UUID workspaceId, UUID taskId) {
    return entityRelationRepository.findLinkedSourceRefIds(workspaceId, taskId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Integer> countLinkedSourceRefs(UUID workspaceId, Collection<UUID> taskIds) {
    if (taskIds.isEmpty()) {
      return Map.of();
    }
    return entityRelationRepository.countLinkedSourceRefsByTaskId(workspaceId, taskIds).stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
  }
}
