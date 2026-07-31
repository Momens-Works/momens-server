package works.momens.server.context.internal;

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
}
