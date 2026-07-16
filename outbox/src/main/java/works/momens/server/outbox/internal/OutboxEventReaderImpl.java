package works.momens.server.outbox.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.outbox.OutboxEventReader;
import works.momens.server.outbox.OutboxEventView;

/** consumer 조회 구현. 발행 로그를 읽기만 하고 소비 상태는 쓰지 않는다(ADR-0009). */
@Component
@RequiredArgsConstructor
class OutboxEventReaderImpl implements OutboxEventReader {

  private final OutboxEventRepository outboxEventRepository;

  @Override
  @Transactional(readOnly = true)
  public List<OutboxEventView> readAfter(long afterId, Instant createdBefore, int limit) {
    return outboxEventRepository
        .findByIdGreaterThanAndCreatedAtLessThanEqualOrderByIdAsc(
            afterId, createdBefore, Limit.of(limit))
        .stream()
        .map(OutboxEventReaderImpl::toView)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long latestIdCreatedBefore(Instant createdBefore) {
    return outboxEventRepository.findMaxIdCreatedBefore(createdBefore);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OutboxEventView> findById(long id) {
    return outboxEventRepository.findById(id).map(OutboxEventReaderImpl::toView);
  }

  private static OutboxEventView toView(OutboxEvent event) {
    return new OutboxEventView(
        event.getId(),
        event.getWorkspaceId(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getEventType(),
        event.getCreatedAt());
  }
}
