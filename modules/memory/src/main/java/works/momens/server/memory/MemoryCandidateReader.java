package works.momens.server.memory;

import java.util.List;
import java.util.UUID;

/**
 * memory 모듈의 후보 조회 public API.
 *
 * <p>워크스페이스 단위 목록 하나만 둡니다. snapshot 합성(MOM-0862)이 이 목록만 쓰고, 레거시 단건 조회(H083)와 프로젝트 밖의 다른 소비자는 웹
 * 클라이언트에 호출 코드가 없습니다. 워크스페이스 목록 endpoint(H042)의 유일한 호출자도 snapshot이 없을 때만 타는 FE 폴백입니다. 필요해지면 그때
 * 추가합니다(project 모듈의 MilestoneReader와 같은 판단).
 *
 * <p>상태 필터 파라미터도 두지 않습니다. 레거시 시그니처에는 있지만 snapshot이 빈 문자열로 넘기고 FE도 넘기지 않아, 실제로 거르는 호출자가 없습니다. 후보의 상태
 * 분기는 클라이언트가 합니다(웹 snapshot 계약 4.4).
 */
public interface MemoryCandidateReader {

  /**
   * workspaceId의 후보를 모두 조회합니다.
   *
   * <p>정렬은 레거시와 같은 {@code importance} 내림차순이며, 값이 없는 후보가 뒤로 갑니다. 같은 중요도에서는 생성 시각 내림차순입니다.
   *
   * <p>상태로 거르지 않습니다. {@code REJECTED}·{@code EXPIRED} 후보도 함께 담깁니다.
   */
  List<MemoryCandidateDetail> listDetailsByWorkspaceId(UUID workspaceId);
}
