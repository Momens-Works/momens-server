package works.momens.server.signal.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Signal 한 건의 상세 조회 결과.
 *
 * <p>상세 bottom sheet가 필요로 하는 조립 모델입니다(docs/spec/mobile-api.md 시그널 절). {@code impact}, {@code
 * minsuSuggestion}은 worker/Minsu 미생산 시 null일 수 있습니다. 근거는 sort_order 순서로 담고, 각 근거의 summary는 snippet이
 * 없으면 text로 폴백합니다. task_draft 최소 초안·고정 action 목록·빈 fields는 표현 계층(DTO)이 정책으로 채웁니다.
 */
public record SignalDetail(
    UUID id,
    UUID projectId,
    String projectName,
    String type,
    String title,
    String description,
    String impact,
    String minsuSuggestion,
    List<Evidence> evidence) {

  public record Evidence(
      UUID id,
      String source,
      String sourceTitle,
      Instant occurredAt,
      String summary,
      String sourceUrl) {}
}
