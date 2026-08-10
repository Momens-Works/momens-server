package works.momens.server.minsu.internal.ledger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * 원장의 사건 지표(docs/design/minsu-async-task-draft-design.md 9.3절).
 *
 * <p>상태 gauge는 {@link MinsuLedgerMetrics}가 주기 스냅샷으로 낸다. 여기는 사건이 일어난 그 자리에서 올리는 counter·timer만 모은다.
 * 원장 전이가 여러 파일에 흩어져 있어 {@code MeterRegistry}를 직접 쓰게 두면 이름과 태그 어휘가 갈리기 쉽다.
 *
 * <p>counter는 값 집합이 enum이라 조합이 부팅 시점에 정해지므로 생성자에서 0으로 선등록한다(지표 규약). timer·summary는 분포라 선등록 대상이 아니다.
 */
@Component
class MinsuLedgerObservability {

  private static final String COMPLETIONS = "momens.minsu.ledger.completions";
  private static final String ATTEMPTS = "momens.minsu.ledger.attempts";
  private static final String ENROLLMENTS = "momens.minsu.ledger.enrollments";
  private static final String REASON = "reason";

  private final MeterRegistry meterRegistry;

  MinsuLedgerObservability(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    for (CompletionReason reason : CompletionReason.values()) {
      completions(reason);
    }
    enrollments(true);
    enrollments(false);
    reclaims();
    staleResults();
    deadlineProjections();
  }

  /**
   * 원장이 종료로 닫힐 때 사유와 그때까지의 시도 횟수를 함께 남긴다.
   *
   * <p>시도 횟수는 claim 시점에 증가하므로(7.1절) <b>provider 호출 수가 아니라 claim 수</b>다. 실행되지 못하고 소모된 시도가 포함되며, 그
   * 차이는 hung attempt gauge와 같이 봐야 드러난다.
   */
  void recordCompletion(CompletionReason reason, int attemptCount) {
    completions(reason).increment();
    DistributionSummary.builder(ATTEMPTS)
        .baseUnit("attempts")
        .tag(REASON, reason.value())
        .register(meterRegistry)
        .record(attemptCount);
  }

  /** lease 만료로 회수한 수. stale 결과 수와 함께 오르면 lease가 짧다는 신호다(티켓 완료 조건). */
  void recordReclaims(int count) {
    if (count > 0) {
      reclaims().increment(count);
    }
  }

  /** claim token 불일치로 버린 결과 수. 위 회수 수와 짝을 이룬다(8.2절). */
  void recordStaleResult() {
    staleResults().increment();
  }

  /**
   * 재시도 시각이 지난 뒤 실제로 claim되기까지의 지연.
   *
   * <p>설계 9.3절이 적은 "claim 이후 모델 호출 시작까지의 queue delay"는 지금 구조에서 항상 0에 가깝다. scheduler가 빈 슬롯 수만큼만
   * claim하고 그만큼만 제출하므로 제출된 시도는 즉시 시작한다. 실제 지연은 그 앞에 있다. {@code fixedDelay}가 배치의 결과 기록까지 기다리므로 직전
   * 배치의 가장 느린 시도만큼 다음 claim이 밀린다({@link MinsuDrainScheduler} 참고). 그래서 재는 구간을 이쪽으로 옮겼다.
   */
  void recordClaimWait(Duration wait) {
    timer("momens.minsu.ledger.claim.wait.duration").record(clampToZero(wait));
  }

  /**
   * 적재부터 {@code tasks} 반영까지의 end-to-end 지연. 성공 반영에서만 기록한다.
   *
   * <p>시작 시각인 {@code created_at}은 JPA Auditing이 애플리케이션 시계로 쓰고 끝 시각은 DB 시계라, 편차에 따라 음수가 나올 수 있어 0에서
   * 자른다({@link TaskDraftGenerationRepository#snapshotUnfinished()}의 나이와 같은 사정이다).
   */
  void recordGenerationDuration(Duration duration) {
    timer("momens.minsu.ledger.generation.duration").record(clampToZero(duration));
  }

  /**
   * 원장 적재의 성패. 실패율은 fail-closed 선택의 대가를 보는 값이다(5.3절).
   *
   * <p>counter는 롤백되지 않으므로 커밋 실패 시 성공이 과다 계상될 수 있다. 다만 적재 실패는 convert 트랜잭션 전체를 롤백시키고 그 경로가 여기서 잡히므로
   * 실용상 문제가 되지 않는다.
   */
  void recordEnrollment(boolean success) {
    enrollments(success).increment();
  }

  /**
   * 읽기 투영이 deadline으로 {@code ready}를 돌려준 수. <b>0이 정상이며 경보 대상이다</b>(MOM-0832).
   *
   * <p>사용자가 조회해야 올라가므로 정지를 늦게 안다. 그래서 스냅샷의 {@code read.deadline.exceeded} gauge가 따로 있다. 둘은 같은 사건을
   * 다른 시점에서 보는 값이다.
   */
  void recordDeadlineProjection() {
    deadlineProjections().increment();
  }

  private Counter completions(CompletionReason reason) {
    return meterRegistry.counter(COMPLETIONS, REASON, reason.value());
  }

  private Counter enrollments(boolean success) {
    return meterRegistry.counter(ENROLLMENTS, "outcome", success ? "success" : "failure");
  }

  private Counter reclaims() {
    return meterRegistry.counter("momens.minsu.ledger.reclaims");
  }

  private Counter staleResults() {
    return meterRegistry.counter("momens.minsu.ledger.stale.results");
  }

  private Counter deadlineProjections() {
    return meterRegistry.counter("momens.minsu.ledger.deadline.projections");
  }

  private Timer timer(String name) {
    return meterRegistry.timer(name);
  }

  private static Duration clampToZero(Duration duration) {
    return duration.isNegative() ? Duration.ZERO : duration;
  }
}
