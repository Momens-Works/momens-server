package works.momens.server.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * outbox consumer 조회 public API(docs/design/signal-push-demo-design.md 10절, ADR-0009).
 *
 * <p>{@code id}는 BIGSERIAL이라 id 발급 순서와 commit 순서가 다를 수 있다. 나중 id의 event가 먼저 commit되면 watermark가 아직
 * commit되지 않은 앞 id를 지나쳐 그 event를 영구히 건너뛴다. 이를 막기 위해 consumer는 {@code createdBefore}(안전 지연을 지난 시각)보다
 * 오래된 event만 읽는다. 소비 상태(watermark)는 consumer가 자기 모듈에서 관리하고, 이 모듈은 조회만 제공한다.
 */
public interface OutboxEventReader {

  /** watermark({@code afterId}) 이후이면서 {@code createdBefore}보다 먼저 만들어진 event를 id 오름차순으로 읽는다. */
  List<OutboxEventView> readAfter(long afterId, Instant createdBefore, int limit);

  /** {@code createdBefore}보다 먼저 만들어진 event의 최대 id. 없으면 0(consumer 최초 watermark 시드용). */
  long latestIdCreatedBefore(Instant createdBefore);

  /** id 한 건 조회. 발송 시점에 event의 {@code aggregateId}로 본문을 hydrate할 때 사용한다. */
  Optional<OutboxEventView> findById(long id);
}
