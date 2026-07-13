package works.momens.server.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 발행 주체·멱등키 생성과 payload 직렬화(널 값 포함)를 DB 없이 검증한다. */
@ExtendWith(MockitoExtension.class)
class OutboxAppenderImplTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Captor private ArgumentCaptor<String> payloadCaptor;

  @Test
  void derivesApiServerIssuerAndDeterministicKeyAndSerializesNullValues() {
    OutboxAppenderImpl appender = new OutboxAppenderImpl(outboxEventRepository);
    UUID workspaceId = UUID.randomUUID();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("origin_type", "manual");
    payload.put("origin_signal_id", null);

    appender.append(workspaceId, "task", "task-1", "task.created", payload);

    verify(outboxEventRepository)
        .insertIgnoringConflict(
            org.mockito.ArgumentMatchers.eq("api-server"),
            org.mockito.ArgumentMatchers.eq(workspaceId),
            org.mockito.ArgumentMatchers.eq("task"),
            org.mockito.ArgumentMatchers.eq("task-1"),
            org.mockito.ArgumentMatchers.eq("task.created"),
            payloadCaptor.capture(),
            org.mockito.ArgumentMatchers.eq("task.created:task-1"));
    assertThat(payloadCaptor.getValue())
        .isEqualTo("{\"origin_type\":\"manual\",\"origin_signal_id\":null}");
  }
}
