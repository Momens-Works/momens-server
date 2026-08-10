package works.momens.server.minsu.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 원장 사건 지표의 이름·태그와 선등록을 고정한다(MOM-0821, 설계 9.3절). */
class MinsuLedgerObservabilityTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final MinsuLedgerObservability observability =
      new MinsuLedgerObservability(meterRegistry);

  @Test
  @DisplayName("사건이 없어도 모든 종료 사유가 0으로 등록돼 있다")
  void preRegistersEveryCompletionReason() {
    assertThat(meterRegistry.find("momens.minsu.ledger.completions").counters())
        .hasSize(CompletionReason.values().length);
    assertAll(
        Stream.of(CompletionReason.values())
            .map(
                reason ->
                    () ->
                        assertThat(
                                meterRegistry
                                    .get("momens.minsu.ledger.completions")
                                    .tag("reason", reason.value())
                                    .counter()
                                    .count())
                            .isZero()));
  }

  @Test
  @DisplayName("적재 성패와 나머지 counter도 0으로 등록돼 있다")
  void preRegistersRemainingCounters() {
    assertAll(
        () -> assertThat(enrollments("success")).isZero(),
        () -> assertThat(enrollments("failure")).isZero(),
        () -> assertThat(counter("momens.minsu.ledger.reclaims")).isZero(),
        () -> assertThat(counter("momens.minsu.ledger.stale.results")).isZero(),
        () -> assertThat(counter("momens.minsu.ledger.deadline.projections")).isZero());
  }

  @Test
  @DisplayName("종료를 사유별 counter와 시도 횟수 분포로 함께 남긴다")
  void recordsCompletionWithAttemptCount() {
    observability.recordCompletion(CompletionReason.RETRY_EXHAUSTED, 4);

    assertAll(
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.ledger.completions")
                        .tag("reason", "retry_exhausted")
                        .counter()
                        .count())
                .isEqualTo(1),
        // 재시도 상한을 조정하려면 사유별 시도 분포가 필요하다. 전체 평균으로는 어떤 사유가 상한을
        // 소모하는지 갈라지지 않는다.
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.ledger.attempts")
                        .tag("reason", "retry_exhausted")
                        .summary()
                        .totalAmount())
                .isEqualTo(4));
  }

  @Test
  @DisplayName("회수가 없으면 counter를 올리지 않는다")
  void skipsReclaimCounterWhenNothingReclaimed() {
    observability.recordReclaims(0);
    observability.recordReclaims(2);

    assertThat(counter("momens.minsu.ledger.reclaims")).isEqualTo(2);
  }

  @Test
  @DisplayName("시계 차이로 음수가 된 구간은 0으로 기록한다")
  void clampsNegativeDurations() {
    observability.recordClaimWait(Duration.ofSeconds(-3));
    observability.recordGenerationDuration(Duration.ofSeconds(-3));

    assertAll(
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.ledger.claim.wait.duration")
                        .timer()
                        .totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isZero(),
        () ->
            assertThat(
                    meterRegistry
                        .get("momens.minsu.ledger.generation.duration")
                        .timer()
                        .totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isZero());
  }

  @Test
  @DisplayName("트랜잭션 안에서는 커밋 전까지 영속 상태 지표를 올리지 않는다")
  void defersStateAssertingMetricsUntilCommit() {
    // counter는 롤백되지 않는다. 트랜잭션 안에서 바로 올리면 그 트랜잭션이 뒤집혔을 때 원장은
    // processing으로 돌아가는데 종료 counter만 남고, 그 행이 회수돼 다시 닫히면 두 번 세어진다.
    TransactionSynchronizationManager.initSynchronization();
    try {
      observability.recordCompletion(CompletionReason.GENERATED, 1);
      observability.recordEnrollment(true);
      observability.recordReclaims(1);

      assertAll(
          () ->
              assertThat(
                      meterRegistry
                          .get("momens.minsu.ledger.completions")
                          .tag("reason", "generated")
                          .counter()
                          .count())
                  .isZero(),
          () -> assertThat(enrollments("success")).isZero(),
          () -> assertThat(counter("momens.minsu.ledger.reclaims")).isZero());
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("상태를 바꾸지 않는 관측과 적재 실패는 트랜잭션 안에서도 즉시 올린다")
  void recordsObservationsImmediately() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      // 앞의 둘은 읽기만 하고 끝나 미룰 이유가 없다. 적재 실패는 그 트랜잭션이 어차피 커밋되지
      // 않으므로 미루면 기록이 사라진다.
      observability.recordStaleResult();
      observability.recordDeadlineProjection();
      observability.recordEnrollment(false);

      assertAll(
          () -> assertThat(counter("momens.minsu.ledger.stale.results")).isEqualTo(1),
          () -> assertThat(counter("momens.minsu.ledger.deadline.projections")).isEqualTo(1),
          () -> assertThat(enrollments("failure")).isEqualTo(1));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private double counter(String name) {
    return meterRegistry.get(name).counter().count();
  }

  private double enrollments(String outcome) {
    return meterRegistry
        .get("momens.minsu.ledger.enrollments")
        .tag("outcome", outcome)
        .counter()
        .count();
  }
}
