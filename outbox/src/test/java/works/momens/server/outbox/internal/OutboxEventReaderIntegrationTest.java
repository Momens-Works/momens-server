package works.momens.server.outbox.internal;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.outbox.OutboxEventView;

/**
 * consumer 조회의 watermark(id) 필터, 안전 지연(createdBefore) 필터, id 오름차순을 실제 PostgreSQL로
 * 검증합니다(docs/design/signal-push-demo-design.md 10.1절).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OutboxEventReaderImpl.class)
class OutboxEventReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @Autowired private OutboxEventReaderImpl reader;
  @Autowired private OutboxEventRepository repository;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("watermark 이후이면서 안전 지연을 지난 event만 id 오름차순으로 읽는다")
  void readsAfterWatermarkAndBeforeSafetyLag() {
    Instant now = Instant.now();
    long oldEvent = insertEventCreatedAt("signal.created", "a", now.minus(10, ChronoUnit.SECONDS));
    long dueEvent = insertEventCreatedAt("signal.created", "b", now.minus(5, ChronoUnit.SECONDS));
    long freshEvent = insertEventCreatedAt("signal.created", "c", now);

    List<OutboxEventView> events = reader.readAfter(oldEvent, now.minus(2, ChronoUnit.SECONDS), 10);

    assertThat(events).extracting(OutboxEventView::id).containsExactly(dueEvent);
    assertThat(events.getFirst().workspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(events.getFirst().aggregateType()).isEqualTo("signal");
    assertThat(events.getFirst().eventType()).isEqualTo("signal.created");
    assertThat(reader.readAfter(0, now.plus(1, ChronoUnit.MINUTES), 10))
        .extracting(OutboxEventView::id)
        .containsExactly(oldEvent, dueEvent, freshEvent);
  }

  @Test
  @DisplayName("최초 watermark 시드는 안전 지연을 지난 event의 최대 id이고, 없으면 0이다")
  void latestIdSeedsWatermark() {
    Instant now = Instant.now();
    assertThat(reader.latestIdCreatedBefore(now)).isZero();

    insertEventCreatedAt("signal.created", "a", now.minus(10, ChronoUnit.SECONDS));
    long latest = insertEventCreatedAt("signal.created", "b", now.minus(5, ChronoUnit.SECONDS));
    insertEventCreatedAt("signal.created", "c", now.plus(5, ChronoUnit.SECONDS));

    assertThat(reader.latestIdCreatedBefore(now)).isEqualTo(latest);
  }

  @Test
  @DisplayName("id 한 건 조회는 aggregateId를 포함한 view를 돌려준다")
  void findsById() {
    long id = insertEventCreatedAt("signal.created", "agg-1", Instant.now());

    assertThat(reader.findById(id))
        .hasValueSatisfying(event -> assertThat(event.aggregateId()).isEqualTo("agg-1"));
    assertThat(reader.findById(id + 999)).isEmpty();
  }

  private long insertEventCreatedAt(String eventType, String aggregateId, Instant createdAt) {
    repository.insertIgnoringConflict(
        "api-server",
        WORKSPACE_ID,
        "signal",
        aggregateId,
        eventType,
        "{}",
        eventType + ":" + aggregateId);
    entityManager
        .createNativeQuery(
            "UPDATE outbox_events SET created_at = :createdAt WHERE idempotency_key = :key")
        .setParameter("createdAt", createdAt)
        .setParameter("key", eventType + ":" + aggregateId)
        .executeUpdate();
    return ((Number)
            entityManager
                .createNativeQuery("SELECT id FROM outbox_events WHERE idempotency_key = :key")
                .setParameter("key", eventType + ":" + aggregateId)
                .getSingleResult())
        .longValue();
  }
}
