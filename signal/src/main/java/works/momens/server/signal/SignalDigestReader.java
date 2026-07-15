package works.momens.server.signal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 브리프의 시그널 요약 문단 조회 공개 API. 요청자의 workspace 멤버십을 검사합니다.
 *
 * <p>{@link SignalListService}에 두지 않고 분리합니다. 그쪽은 Signal 목록을 조회하는 API인데 이 문단은 목록이 아니라 프로젝트의 그날 신호
 * 전체를 요약한 값이라서입니다. 목록 카드 한 건을 뜻하는 {@link SignalSummary}와도 다릅니다.
 */
public interface SignalDigestReader {

  /**
   * 생성 시각이 {@code [createdFrom, createdToExclusive)} 범위인 요약 문단을 반환합니다. 브리프가 시그널을 거르는 범위를 그대로 넘기면
   * 문단과 시그널 목록이 항상 같은 날 기준이 됩니다. 하루 경계를 어떤 타임존으로 볼지는 호출하는 표면이 정합니다.
   *
   * <p>범위에 문단이 없으면 empty입니다. 여러 건이면 민수가 다시 만든 것이므로 가장 최근 한 건을 반환합니다.
   *
   * @param createdToExclusive 범위의 끝. 이 값은 포함하지 않습니다.
   */
  Optional<String> findByCreatedRange(
      UUID projectId, UUID userId, Instant createdFrom, Instant createdToExclusive);
}
