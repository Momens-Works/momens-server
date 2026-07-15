package works.momens.server.context;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 엔티티 간 연결(entity_relations) 조회 public API.
 *
 * <p>태스크에 연결된 관련자료를 조립할 때, 다른 모듈이 context 내부 repository를 직접 참조하지 않고 링크를 읽을 수 있도록 합니다. {@code
 * entity_relations}는 레거시 {@code momens-api}가 소유하는 외부 테이블이며, 이 서버는 읽기만 합니다.
 *
 * <p>이 모듈은 <b>식별자만</b> 반환합니다. source_ref 본문 hydrate는 호출하는 쪽이 {@code SourceRefReader}로
 * 조합합니다(module-map의 얇은 연결 capability).
 *
 * <p>조회는 {@code workspaceId} 스코프로 한정합니다. 다른 워크스페이스의 id가 섞여 들어와도 결과에서 빠지므로, 호출하는 쪽의 상위 권한 검사와 별개로 교차
 * 워크스페이스 노출을 쿼리 단계에서 막습니다. 소프트 삭제된 링크는 없는 것으로 취급합니다.
 */
public interface EntityRelationReader {

  /**
   * 태스크에 연결된 source_ref id를 표시 순서로 조회합니다.
   *
   * <p>순서는 링크 생성 시각 내림차순이고, 같으면 링크 id 내림차순으로 고정합니다. 레거시는 source_ref의 생성 시각으로 정렬하지만, 표시 순서를 링크가 소유하는
   * 편이 Signal evidence(sort_order)와 일관되고 nullable한 source 시각에 기대지 않아 결정적입니다(의도된 차이).
   */
  List<UUID> findLinkedSourceRefIds(UUID workspaceId, UUID taskId);

  /**
   * 여러 태스크의 연결된 source_ref 개수를 한 번에 조회합니다. 보드처럼 태스크가 여러 건일 때 N+1을 피하기 위한 배치 조회입니다.
   *
   * <p>연결이 없는 태스크는 결과 map에 담기지 않습니다. 개수 0 표시는 호출하는 쪽이 정합니다.
   */
  Map<UUID, Integer> countLinkedSourceRefs(UUID workspaceId, Collection<UUID> taskIds);
}
