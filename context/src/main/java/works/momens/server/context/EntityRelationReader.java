package works.momens.server.context;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * entity_relations 조회 public API입니다.
 *
 * <p>태스크에 연결된 관련자료를 조합할 때 다른 모듈이 context 내부 repository에 직접 의존하지 않고 연결 정보를 조회할 수 있도록 제공합니다. {@code
 * entity_relations}는 레거시 {@code momens-api}가 소유하는 외부 테이블이며, 이 서버는 읽기 전용으로만 사용합니다.
 *
 * <p>이 API는 source_ref 식별자와 연결 개수만 반환합니다. source_ref의 상세 정보는 호출하는 쪽이 {@code SourceRefReader}로 조회해
 * 조합합니다. context는 연결 정보만 제공하는 얇은 연결 capability를 유지합니다(module-map).
 *
 * <p>조회는 {@code workspaceId} 범위로 제한합니다. 다른 워크스페이스의 연결은 결과에 포함되지 않으며, 소프트 삭제된 연결도 조회 대상에서 제외합니다.
 */
public interface EntityRelationReader {

  /**
   * 태스크에 연결된 source_ref id를 표시 순서대로 조회합니다.
   *
   * <p>정렬은 링크 생성 시각 내림차순, 링크 id 내림차순입니다. 레거시와 달리 source_ref 생성 시각이 아니라 링크를 기준으로 정렬합니다. Signal
   * evidence의 표시 순서 관리 방식과 일관성을 맞추기 위한 선택이며, nullable한 source 생성 시각을 정렬 기준으로 사용하지 않습니다.
   */
  List<UUID> findLinkedSourceRefIds(UUID workspaceId, UUID taskId);

  /**
   * 여러 태스크의 연결된 source_ref 개수를 한 번에 조회합니다.
   *
   * <p>보드 조회에서 사용하는 배치 조회입니다. 여러 태스크의 연결 개수를 한 번에 집계해 N+1 조회를 방지합니다.
   *
   * <p>연결이 없는 태스크는 결과 map에 포함하지 않습니다. 개수 0은 호출하는 쪽에서 처리합니다.
   */
  Map<UUID, Integer> countLinkedSourceRefs(UUID workspaceId, Collection<UUID> taskIds);
}
