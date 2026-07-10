package works.momens.server.signal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 프로젝트 범위의 미처리 Signal 목록 조회 공개 API. 모든 메서드가 요청자의 workspace 멤버십을 검사합니다. */
public interface SignalListService {

  List<SignalSummary> listUnprocessed(UUID projectId, UUID userId);

  /**
   * 주어진 type의 미처리 Signal을 커서 페이지로 조회합니다. 어떤 type을 보일지는 호출하는 표면이 정합니다(브리프 시그널 요약은 change를 넘기지 않습니다.
   * MOM-67). 정렬은 생성 시각 내림차순이고 같으면 id 내림차순입니다.
   *
   * @param cursor 이전 페이지의 {@link SignalSummaryPage#nextCursor()}. null이면 첫 페이지. 형식이 잘못되면
   *     COMMON_VALIDATION_FAILED로 실패합니다.
   * @param limit 페이지 크기. 1 이상이어야 하고 상한을 넘으면 상한으로 줄여 조회합니다.
   */
  SignalSummaryPage listUnprocessedPage(
      UUID projectId, UUID userId, Collection<String> types, String cursor, int limit);

  /** 미처리 Signal 개수를 type별로 집계합니다. 저장된 모든 type을 반환하고, 노출할 type은 호출하는 표면이 고릅니다. */
  Map<String, Long> countUnprocessedByType(UUID projectId, UUID userId);
}
