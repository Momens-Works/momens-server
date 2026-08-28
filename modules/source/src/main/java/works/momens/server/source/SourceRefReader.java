package works.momens.server.source;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * source_refs 조회 public API.
 *
 * <p>Signal evidence(MOM-64)처럼 다른 모듈이 근거 원본을 hydrate할 때, source 내부 repository를 직접 참조하지 않고
 * source_ref를 읽을 수 있도록 합니다. {@code source_refs}는 레거시 {@code momens-api}가 소유하는 외부 테이블입니다. 해당 서버는 주로
 * 읽으며, 검증 결과 기록({@code SourceRefVerifier})과 사용자가 붙여넣은 링크 저장({@code SourceRefWriter}) 경로에서만 씁니다.
 *
 * <p>소프트 삭제된 source_ref는 없는 것으로 취급합니다. 반환 순서는 보장하지 않으며, 표시 순서(예: Signal evidence의 sort_order)는 호출하는
 * 쪽이 정합니다.
 *
 * <p>조회는 {@code workspaceId} 스코프로 한정합니다. 다른 워크스페이스의 id가 섞여 들어와도 결과에서 빠지므로, 호출하는 쪽의 상위 권한 검사와 별개로 교차
 * 워크스페이스 콘텐츠 노출을 쿼리 단계에서 막습니다.
 */
public interface SourceRefReader {

  /** workspaceId에 속하고 ids에 해당하는, 소프트 삭제되지 않은 source_ref를 조회합니다. 없거나 다른 워크스페이스의 id는 결과에서 빠집니다. */
  List<SourceRefView> findByIds(UUID workspaceId, Collection<UUID> ids);

  /** 웹 Product API 호환 응답에 필요한 source_ref 전체 투영입니다. */
  List<LegacySourceRefDetail> findLegacyDetailsByIds(UUID workspaceId, Collection<UUID> ids);

  /**
   * source-ref가 속한 워크스페이스의 식별자를 조회합니다.
   *
   * <p>소프트 삭제된 source-ref는 존재하지 않는 것으로 처리해 빈 값을 반환합니다. 검증 요청의 권한을 확인하려면 대상이 속한 워크스페이스를 먼저 알아야 하므로 이
   * 조회 기능을 public API로 제공합니다.
   */
  Optional<UUID> findWorkspaceId(UUID sourceRefId);
}
