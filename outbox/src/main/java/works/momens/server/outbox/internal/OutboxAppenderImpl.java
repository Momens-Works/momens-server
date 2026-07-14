package works.momens.server.outbox.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.outbox.OutboxAppender;

/**
 * api-server가 발행하는 outbox row를 호출자 트랜잭션에 합류시킨다(ADR-0008). {@code issued_by}는 이 서버가 발행하는 이벤트만 다루므로
 * {@code "api-server"}로 고정한다.
 *
 * <p>{@code payload} 직렬화는 web 계층 {@code ObjectMapper} 설정(날짜 포맷, 프로퍼티 네이밍 등)과 절연하기 위해 전용 {@link
 * ObjectMapper}를 쓴다. 멱등키는 {@code "{event_type}:{aggregate_id}"}로 결정적으로 조립한다(SD-3).
 */
@Component
@RequiredArgsConstructor
class OutboxAppenderImpl implements OutboxAppender {

  private static final String ISSUED_BY = "api-server";

  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper outboxObjectMapper = new ObjectMapper();

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void append(
      UUID workspaceId,
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload) {
    String serializedPayload = serialize(payload);
    String idempotencyKey = eventType + ":" + aggregateId;
    outboxEventRepository.insertIgnoringConflict(
        ISSUED_BY,
        workspaceId,
        aggregateType,
        aggregateId,
        eventType,
        serializedPayload,
        idempotencyKey);
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return outboxObjectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("outbox payload 직렬화 실패: " + payload, e);
    }
  }
}
