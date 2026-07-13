/**
 * Outbox 발행 로그.
 *
 * <p>도메인 write 트랜잭션 안에서 확정 액션 결과를 append-only {@code outbox_events} row로 남기는 경계다(ADR-0008,
 * ADR-0010). 이벤트를 발생시킨 주체(issued_by)가 자기 트랜잭션에서 INSERT하며, api-server의 사용자 액션·task 생성 이벤트와 worker의
 * {@code signal.created}가 같은 envelope를 공유한다. 외부에는 모듈 root의 {@link
 * works.momens.server.outbox.OutboxAppender}로만 공개하고, 엔티티·리포지토리·구현체는 {@code internal} 패키지에 은닉한다.
 *
 * <p>{@code status}/{@code updated_at} 컬럼은 없다 — consumer(worker·retrieval)가 소비 상태를 자체 관리한다.
 * 멱등키({@code "{event_type}:{aggregate_id}"})의 {@code UNIQUE} 제약과 {@code INSERT ... ON CONFLICT DO
 * NOTHING}이 중복 이벤트를 막는 두 번째 층이다(SD-3).
 */
package works.momens.server.outbox;
