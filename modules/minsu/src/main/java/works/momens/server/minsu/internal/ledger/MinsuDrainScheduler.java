package works.momens.server.minsu.internal.ledger;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.internal.config.MinsuConfigStatus;
import works.momens.server.minsu.internal.generation.AsyncGenerationResult;
import works.momens.server.minsu.internal.generation.AsyncTaskDraftExecutor;

/**
 * 생성 원장 drain(docs/design/minsu-async-task-draft-design.md 8.5·11.2·11.3절).
 *
 * <p>drain 축이 꺼져 있으면 이 빈이 등록되지 않아 폴링 자체가 없다. 스케줄링 인프라는 조립 모듈 {@code app}이 항상 켜므로(MOM-0816) 이
 * scheduler는 {@code notification}의 push 설정과 무관하게 자신의 설정 축만으로 동작 여부가 정해진다.
 *
 * <p>주기가 1초인 이유는 그것이 곧 {@code generating} 구간의 하한이기 때문이다. 사용자가 convert를 누른 직후 적재된 원장을 늦게 집을수록 앱이
 * {@code generating}을 보는 시간이 길어진다. 빈 폴링은 {@code (next_attempt_at) WHERE status = 'pending'} 부분 인덱스
 * 덕에 싸다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "momens.minsu.task-draft.async.drain", havingValue = "true")
class MinsuDrainScheduler {

  private final MinsuConfigStatus configStatus;
  private final TaskDraftGenerationLedger ledger;
  private final AsyncTaskDraftExecutor executor;

  MinsuDrainScheduler(
      MinsuConfigStatus configStatus,
      TaskDraftGenerationLedger ledger,
      AsyncTaskDraftExecutor executor) {
    this.configStatus = configStatus;
    this.ledger = ledger;
    this.executor = executor;
  }

  @Scheduled(fixedDelayString = "1s")
  void drain() {
    try {
      runPass();
    } catch (RuntimeException e) {
      // claim은 커밋됐고 결과 기록만 실패했을 수 있다. 그 원장은 lease 만료 후 회수된다(8.5절).
      log.error("Minsu draft drain 주기 실패", e);
    }
  }

  private void runPass() {
    if (!configStatus.enabled() || !configStatus.valid()) {
      // provider가 비활성이거나 설정이 무효면 claim하지 않는다(11.2절). 원장은 pending 그대로 두고
      // completion_reason을 기록하지 않는다. 설정을 고치면 그대로 이어서 처리할 수 있는 작업을 종료로
      // 기록하면 되살릴 방법이 없기 때문이다. 그 전에 read_deadline_at이 지나면 읽기 투영이 닫는다.
      return;
    }
    int slots = executor.availableSlots();
    if (slots == 0) {
      // 멈춘 호출이 슬롯을 모두 점유한 상태다(9.1절 포화). 여기서 claim하면 실행되지 못한 작업이 시도
      // 횟수만 소모한다.
      return;
    }
    List<ClaimedGeneration> claims = ledger.claimDue(slots);
    if (claims.isEmpty()) {
      return;
    }
    List<AsyncGenerationResult> results =
        executor.executeAll(claims.stream().map(ClaimedGeneration::input).toList());
    for (int i = 0; i < claims.size(); i++) {
      ledger.record(claims.get(i), results.get(i));
    }
  }
}
