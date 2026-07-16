package works.momens.server.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * consumer가 읽는 outbox event 한 건의 조회 결과.
 *
 * <p>payload는 ID 중심의 작은 데이터라(ADR-0008) consumer가 필요한 본문은 {@code aggregateId}로 DB에서 hydrate한다. 지금의
 * 유일한 소비자(notification의 {@code signal.created})는 payload를 쓰지 않으므로 담지 않는다.
 */
public record OutboxEventView(
    long id,
    UUID workspaceId,
    String aggregateType,
    String aggregateId,
    String eventType,
    Instant createdAt) {}
