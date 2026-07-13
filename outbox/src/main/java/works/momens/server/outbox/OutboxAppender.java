package works.momens.server.outbox;

import java.util.Map;
import java.util.UUID;

/**
 * 확정 액션 결과를 outbox에 발행한다.
 *
 * <p>호출하는 쪽의 {@code @Transactional} 안에서 호출해 도메인 write와 같은 트랜잭션에 합류시킨다(ADR-0008). 발행 주체 ({@code
 * issued_by=api-server})와 멱등키({@code "{event_type}:{aggregate_id}"})는 구현이 결정하고, 같은 이벤트를 다시 append해도
 * {@code ON CONFLICT DO NOTHING}으로 중복 row를 만들지 않는다.
 *
 * <p>{@code eventType}·{@code aggregateType} 문자열은 이벤트를 발행하는 모듈이 소유한다(ADR-0010 네이밍 규약). {@code
 * payload}는 ID 중심의 작은 데이터이며 {@code null} 값을 담을 수 있다({@code origin_signal_id} 등).
 */
public interface OutboxAppender {

  void append(
      UUID workspaceId,
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload);
}
