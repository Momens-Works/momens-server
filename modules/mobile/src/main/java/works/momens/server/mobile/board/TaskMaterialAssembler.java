package works.momens.server.mobile.board;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import works.momens.server.context.EntityRelationReader;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;

/**
 * 모바일 태스크 관련자료를 조립합니다.
 *
 * <p>context에서 연결 순서대로 source_ref 식별자를 읽고 source에서 살아 있는 원본만 배치로 조회합니다. 상세 목록과 보드 개수는 이 원본 조회를 공통
 * 생존 기준으로 사용하므로 원본이 삭제된 연결은 양쪽에서 함께 제외됩니다. 링크 수와 무관하게 각 조립은 두 번의 배치 조회로 끝납니다.
 */
@Component
@RequiredArgsConstructor
class TaskMaterialAssembler {

  private final EntityRelationReader entityRelationReader;
  private final SourceRefReader sourceRefReader;

  List<MobileTaskDetail.Material> getMaterials(UUID workspaceId, UUID taskId) {
    List<UUID> sourceRefIds =
        entityRelationReader
            .findLinkedSourceRefIds(workspaceId, List.of(taskId))
            .getOrDefault(taskId, List.of());
    Map<UUID, SourceRefView> refs = findLiveRefs(workspaceId, sourceRefIds);
    return sourceRefIds.stream()
        .filter(refs::containsKey)
        .map(id -> toMaterial(refs.get(id)))
        .toList();
  }

  Map<UUID, Integer> countMaterials(UUID workspaceId, List<UUID> taskIds) {
    Map<UUID, List<UUID>> linksByTask =
        entityRelationReader.findLinkedSourceRefIds(workspaceId, taskIds);
    List<UUID> sourceRefIds =
        linksByTask.values().stream().flatMap(List::stream).distinct().toList();
    Set<UUID> liveIds = findLiveRefs(workspaceId, sourceRefIds).keySet();
    return linksByTask.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> (int) entry.getValue().stream().filter(liveIds::contains).count()));
  }

  private Map<UUID, SourceRefView> findLiveRefs(UUID workspaceId, List<UUID> sourceRefIds) {
    return sourceRefReader.findByIds(workspaceId, sourceRefIds).stream()
        .collect(Collectors.toMap(SourceRefView::id, Function.identity()));
  }

  private static MobileTaskDetail.Material toMaterial(SourceRefView ref) {
    return new MobileTaskDetail.Material(
        ref.id(),
        ref.title(),
        ref.snippet() != null ? ref.snippet() : ref.text(),
        ref.sourceType(),
        ref.sourceCreatedAt(),
        ref.sourceUrl());
  }
}
