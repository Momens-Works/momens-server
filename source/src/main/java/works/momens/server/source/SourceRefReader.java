package works.momens.server.source;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * source_refs 조회 public API.
 *
 * <p>Signal evidence(MOM-64)처럼 다른 모듈이 근거 원본을 hydrate할 때, source 내부 repository를 직접 참조하지 않고
 * source_ref를 읽을 수 있도록 합니다. {@code source_refs}는 레거시 {@code momens-api}가 소유하는 외부 테이블이며, 이 서버는 읽기만
 * 합니다(ADR-0008 read/write 경계).
 *
 * <p>소프트 삭제된 source_ref는 없는 것으로 취급합니다. 반환 순서는 보장하지 않으며, 표시 순서(예: Signal evidence의 sort_order)는 호출하는
 * 쪽이 정합니다.
 */
public interface SourceRefReader {

  /** ids에 해당하는, 소프트 삭제되지 않은 source_ref를 조회합니다. 없는 id는 결과에서 빠집니다. */
  List<SourceRefView> findByIds(Collection<UUID> ids);
}
