package works.momens.server.context.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.context.EntityRelationReader;
import works.momens.server.context.TaskContextLinks;

@Service
@RequiredArgsConstructor
class EntityRelationReaderImpl implements EntityRelationReader {

  private final EntityRelationRepository entityRelationRepository;

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, List<UUID>> findLinkedSourceRefIds(UUID workspaceId, Collection<UUID> taskIds) {
    if (taskIds.isEmpty()) {
      return Map.of();
    }
    // 쿼리가 표시 순서로 정렬해 주므로, 순서를 유지하는 자료구조로 묶어 태스크별 목록에 그대로 담는다.
    return entityRelationRepository.findLinkedSourceRefIdsByTaskIds(workspaceId, taskIds).stream()
        .collect(
            Collectors.groupingBy(
                row -> (UUID) row[0],
                LinkedHashMap::new,
                Collectors.mapping(row -> (UUID) row[1], Collectors.toList())));
  }

  @Override
  @Transactional(readOnly = true)
  public TaskContextLinks findTaskContextLinks(UUID workspaceId, UUID taskId) {
    List<UUID> memoryIds = new ArrayList<>();
    List<UUID> sourceRefIds = new ArrayList<>();
    entityRelationRepository
        .findContextLinks(workspaceId, taskId)
        .forEach(
            row -> {
              if ("MEMORY".equals(row[0])) {
                memoryIds.add((UUID) row[1]);
              } else if ("SOURCE_OBJECT".equals(row[0])) {
                sourceRefIds.add((UUID) row[1]);
              }
            });
    return new TaskContextLinks(memoryIds, sourceRefIds);
  }
}
