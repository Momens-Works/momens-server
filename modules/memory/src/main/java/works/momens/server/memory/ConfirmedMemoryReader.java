package works.momens.server.memory;

import java.util.List;
import java.util.UUID;

/**
 * memory 모듈의 확정 메모리 조회 public API.
 *
 * <p>워크스페이스 단위 목록 하나만 두는 이유는 {@link MemoryCandidateReader}와 같습니다. 레거시 단건 조회(H089)와 목록(H044) 모두
 * snapshot 폴백 밖의 소비자가 없습니다.
 *
 * <p>{@code task_contexts} 번들도 이 모델을 씁니다(계약 4.5). 다만 id 배치 조회는 두지 않았습니다. 번들을 만드는 MOM-0862가 같은 응답의
 * 워크스페이스 목록을 이미 갖고 있어 그 안에서 고르면 되는지, 별도 조회가 필요한지는 그 작업에서 정합니다.
 */
public interface ConfirmedMemoryReader {

  /**
   * workspaceId의 확정 메모리를 모두 조회합니다.
   *
   * <p>정렬은 레거시와 같은 생성 시각 내림차순입니다. 레거시에 tie-break가 없어 두지 않습니다.
   *
   * <p>소프트 삭제된 메모리는 없는 것으로 취급합니다. 상태로는 거르지 않아 {@code INVALIDATED}·{@code ARCHIVED} 메모리도 함께 담깁니다.
   */
  List<ConfirmedMemoryDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
