package works.momens.server.mobile.internal;

import java.util.List;
import java.util.UUID;
import works.momens.server.project.ProjectSnapshot;

/**
 * 브리프 화면 한 장을 채우는 조합 결과. 프로젝트 스냅샷, 시그널 요약(타입별 개수, 최신순 첫 페이지), 현재 우선순위를 담습니다.
 *
 * <p>{@code nextCursor}는 시그널 요약 다음 페이지를 여는 커서 문자열이고, 다음 페이지가 없으면 null입니다.
 */
public record MobileBrief(
    ProjectSnapshot project,
    List<FilterCount> filters,
    List<SignalItem> items,
    String nextCursor,
    List<Priority> priorities) {

  /** 필터 칩 하나의 개수. ALL은 노출 type 전체 합이라 화면 헤더 숫자로도 쓰입니다. */
  public record FilterCount(BriefSignalFilter filter, long count) {}

  /** 시그널 요약 목록의 한 줄(색 점 type + 제목). */
  public record SignalItem(UUID id, String type, String title) {}

  /** 현재 우선순위 한 줄. rank는 정렬 순번(1부터)이고 화면의 01~04 표기와 같습니다. */
  public record Priority(int rank, String title, UUID taskId) {}
}
