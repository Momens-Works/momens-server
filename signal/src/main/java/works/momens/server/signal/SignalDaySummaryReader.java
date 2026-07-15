package works.momens.server.signal;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * signal_day_summaries 조회 public API.
 *
 * <p>mobile 브리프(MOM-0787)가 당일 시그널 요약 문단({@code signal_summary.summary})을 채울 때 signal 내부 repository를
 * 직접 참조하지 않고 조회할 수 있도록 합니다. 멤버십 검사는 이 API의 책임이 아니라 호출 표면(브리프 조합 서비스)이 project/ workspace 조회 과정에서 이미
 * 수행하므로, 여기서는 workspaceId 스코프 필터만 걸고 별도로 권한을 검사하지 않습니다.
 *
 * <p>요약이 아직 없거나(소프트 삭제 포함) worker/fixture가 그 날짜를 채우지 않았으면 빈 값을 반환합니다. 서버는 근거 없는 값을 임의 생성하지 않습니다.
 */
public interface SignalDaySummaryReader {

  /** workspaceId·projectId·summaryDate에 해당하는 소프트 삭제되지 않은 요약을 조회합니다. 없으면 빈 값입니다. */
  Optional<String> findSummary(UUID workspaceId, UUID projectId, LocalDate summaryDate);
}
