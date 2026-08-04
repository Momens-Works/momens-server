package works.momens.server.minsu.internal.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import works.momens.server.minsu.Priority;
import works.momens.server.minsu.Role;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.internal.config.MinsuAsyncProperties;

/**
 * 시도 전체를 감싸는 wall-clock 상한과 포화 동작을 검증한다(MOM-0819, 설계 9.1절).
 *
 * <p>핵심은 <b>interrupt를 무시하는 호출</b>이다. SDK나 ADC 호출이 interrupt에 응답하지 않으면 {@code cancel(true)}는 효과가 없고
 * underlying thread는 계속 점유된다. 상한이 하는 일은 호출을 취소하는 것이 아니라 <b>호출자를 대기에서 푸는 것</b>이고, 그래야 원장에 결과를 남겨
 * 재시도로 넘길 수 있다. 아래 fake는 그 성질을 그대로 흉내 낸다.
 */
class AsyncTaskDraftExecutorTest {

  private static final Duration ATTEMPT_TIMEOUT = Duration.ofMillis(300);
  private static final int CONCURRENCY = 2;

  private final TaskDraftAttempt attempt = mock(TaskDraftAttempt.class);
  private final CountDownLatch release = new CountDownLatch(1);
  private AsyncTaskDraftExecutor executor;

  @AfterEach
  void releaseHungAttempts() {
    release.countDown();
    if (executor != null) {
      executor.destroy();
    }
  }

  @Test
  @DisplayName("결과를 입력과 같은 순서로 돌려준다")
  void preservesInputOrder() {
    executor = executor();
    when(attempt.run(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              SignalTaskDraftInput input = invocation.getArgument(0);
              return new TaskDraftAttempt.Result(
                  new TaskDraft(input.title(), Role.PM, Priority.MEDIUM),
                  GenerationOutcome.GENERATED);
            });

    List<AsyncGenerationResult> results = executor.executeAll(List.of(input("첫째"), input("둘째")));

    // 호출자는 결과를 claim 목록과 짝지어 원장에 기록한다. 순서가 어긋나면 다른 task의 결과가 기록된다.
    assertThat(results).extracting(result -> result.draft().title()).containsExactly("첫째", "둘째");
  }

  @Test
  @DisplayName("상한을 넘긴 시도는 결과를 버리고 timeout으로 판정한다")
  void abandonsAttemptPastWallClockLimit() {
    executor = executor();
    when(attempt.run(any(), any(), any())).thenAnswer(invocation -> blockIgnoringInterrupt());

    long startedAt = System.nanoTime();
    List<AsyncGenerationResult> results = executor.executeAll(List.of(input("멈춘 호출")));

    assertThat(results).hasSize(1);
    // 원장은 이를 retryable로 보고 백오프 뒤 다시 집는다(9.2절).
    assertThat(results.getFirst().outcome()).isEqualTo(GenerationOutcome.TIMEOUT);
    // 고정 fallback은 그대로 유효하다. 실패해도 tasks가 갖는 draft의 품질 하한은 내려가지 않는다(8.5절).
    assertThat(results.getFirst().draft().title()).isEqualTo("멈춘 호출");
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isLessThan(ATTEMPT_TIMEOUT.multipliedBy(4));
  }

  @Test
  @DisplayName("멈춘 호출은 슬롯을 계속 점유하고 그만큼 claim 가능 수가 줄어든다")
  void hungAttemptsConsumeSlotsUntilRestart() {
    executor = executor();
    when(attempt.run(any(), any(), any())).thenAnswer(invocation -> blockIgnoringInterrupt());
    assertThat(executor.availableSlots()).isEqualTo(CONCURRENCY);

    executor.executeAll(List.of(input("하나"), input("둘")));

    // 상한이 지나 호출자는 풀려났지만 interrupt를 무시하는 호출은 여전히 스레드를 잡고 있다. 설계가
    // 수용한 포화가 이 상태다(9.1절). 슬롯이 0이면 다음 주기는 아무것도 claim하지 않으므로, 실행하지도
    // 못할 작업이 시도 횟수만 소모하는 일은 생기지 않는다.
    assertThat(executor.availableSlots()).isZero();

    // 복구 수단은 Pod 재시작이다. 원장이 durable하므로 새 프로세스가 pending과 lease 만료분을 그대로
    // 이어받는다(TaskDraftGenerationLedgerIntegrationTest가 그 부분을 본다).
    AsyncTaskDraftExecutor restarted = executor();
    try {
      assertThat(restarted.availableSlots()).isEqualTo(CONCURRENCY);
    } finally {
      restarted.destroy();
    }
  }

  @Test
  @DisplayName("빈 슬롯을 넘지 않는 배치는 모두 즉시 시작해 같은 상한을 받는다")
  void everySubmittedAttemptStartsImmediately() {
    executor = executor();
    when(attempt.run(any(), any(), any())).thenAnswer(invocation -> blockIgnoringInterrupt());

    long startedAt = System.nanoTime();
    executor.executeAll(List.of(input("하나"), input("둘")));

    // 슬롯 수만큼만 제출하므로 두 시도가 나란히 시작한다. 순차로 시작하면 뒤 시도는 상한의 남은 조각만
    // 받고, 실행되지도 않은 채 시도 횟수만 소모하는 경우가 생긴다.
    assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
        .isLessThan(ATTEMPT_TIMEOUT.multipliedBy(2));
  }

  private AsyncTaskDraftExecutor executor() {
    return new AsyncTaskDraftExecutor(
        attempt,
        new MinsuAsyncProperties(
            true,
            true,
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            new MinsuAsyncProperties.Execution(
                Duration.ofMillis(100),
                ATTEMPT_TIMEOUT,
                Duration.ofSeconds(1),
                2,
                List.of(Duration.ofSeconds(1)),
                CONCURRENCY)));
  }

  /** interrupt에 응답하지 않는 호출. 멈춘 SDK·ADC 호출이 스레드를 놓지 않는 상황을 그대로 흉내 낸다. */
  private TaskDraftAttempt.Result blockIgnoringInterrupt() {
    boolean released = false;
    while (!released) {
      try {
        released = release.await(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // 그대로 삼킨다. 이것이 이 테스트가 재현하려는 성질이다.
      }
    }
    return new TaskDraftAttempt.Result(
        new TaskDraft("늦게 도착", Role.PM, Priority.MEDIUM), GenerationOutcome.GENERATED);
  }

  private static SignalTaskDraftInput input(String title) {
    return new SignalTaskDraftInput(title, "risk", "카드 결제 실패가 늘었다", null, List.of());
  }
}
