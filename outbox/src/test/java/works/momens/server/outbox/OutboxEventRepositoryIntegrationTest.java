package works.momens.server.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/** outbox_events 컬럼 매핑과 멱등키 {@code ON CONFLICT DO NOTHING}(SD-3)을 실제 PostgreSQL로 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Test
  @DisplayName("append한 값을 그대로 조회한다")
  void appendsAndReadsBack() {
    outboxEventRepository.insertIgnoringConflict(
        "api-server",
        WORKSPACE_ID,
        "task",
        "task-1",
        "task.created",
        "{\"origin_type\":\"signal\",\"origin_signal_id\":\"sig-1\"}",
        "task.created:task-1");

    List<OutboxEvent> events = outboxEventRepository.findAll();

    assertThat(events).hasSize(1);
    OutboxEvent event = events.getFirst();
    assertThat(event.getIssuedBy()).isEqualTo("api-server");
    assertThat(event.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(event.getAggregateType()).isEqualTo("task");
    assertThat(event.getAggregateId()).isEqualTo("task-1");
    assertThat(event.getEventType()).isEqualTo("task.created");
    // jsonb는 저장 시 정규화(키 사이 공백)하므로 공백에 의존하지 않고 키·값 존재만 확인한다.
    assertThat(event.getPayload()).contains("\"origin_type\"").contains("\"signal\"");
    assertThat(event.getIdempotencyKey()).isEqualTo("task.created:task-1");
    assertThat(event.getId()).isNotNull();
    assertThat(event.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("같은 멱등키 두 번째 append는 무시되어 row가 하나만 남는다")
  void ignoresDuplicateIdempotencyKey() {
    outboxEventRepository.insertIgnoringConflict(
        "api-server",
        WORKSPACE_ID,
        "signal",
        "sig-1",
        "signal.dismissed",
        "{}",
        "signal.dismissed:sig-1");
    outboxEventRepository.insertIgnoringConflict(
        "api-server",
        WORKSPACE_ID,
        "signal",
        "sig-1",
        "signal.dismissed",
        "{}",
        "signal.dismissed:sig-1");

    assertThat(outboxEventRepository.count()).isEqualTo(1);
  }
}
