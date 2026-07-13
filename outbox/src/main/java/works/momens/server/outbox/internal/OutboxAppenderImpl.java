package works.momens.server.outbox.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import works.momens.server.outbox.OutboxAppender;

@Component
@RequiredArgsConstructor
class OutboxAppenderImpl implements OutboxAppender {

  /** MVP에서 서버가 발행하는 이벤트의 주체는 전부 api-server다(worker는 signal.created를 자기 트랜잭션에서 발행). */
  private static final String ISSUED_BY_API_SERVER = "api-server";

  // payload는 flat한 ID map이라 웹 계층의 ObjectMapper(Boot 4 기본 Jackson 3)에 의존하지 않고
  // 전용 인스턴스로 직렬화한다. ObjectMapper는 구성 후 thread-safe라 단일 필드로 재사용한다.
  private final ObjectMapper objectMapper = new ObjectMapper();

  private final OutboxEventRepository outboxEventRepository;

  @Override
  public void append(
      UUID workspaceId,
      String aggregateType,
      String aggregateId,
      String eventType,
      Map<String, Object> payload) {
    outboxEventRepository.insertIgnoringConflict(
        ISSUED_BY_API_SERVER,
        workspaceId,
        aggregateType,
        aggregateId,
        eventType,
        serialize(payload),
        eventType + ":" + aggregateId);
  }

  private String serialize(Map<String, Object> payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("outbox payload 직렬화 실패: " + payload, e);
    }
  }
}
