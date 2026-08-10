package works.momens.server.minsu.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
