package works.momens.server.minsu.internal.ledger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.internal.config.MinsuAsyncProperties;
import works.momens.server.minsu.internal.config.MinsuAsyncProperties.Execution;
import works.momens.server.minsu.internal.generation.AsyncGenerationResult;
import works.momens.server.minsu.internal.json.MinsuJson;

/**
 * claim과 결과 기록 트랜잭션(docs/design/minsu-async-task-draft-design.md 7.1·8.2·8.5절).
 *
 * <p>claim 트랜잭션이 시도 횟수와 소유권을 먼저 커밋하고, provider 호출은 트랜잭션 <b>밖</b>에서 이뤄진다. 결과 기록은 다시 행을 잠그고 claim
 * token을 재검증한 뒤에만 반영한다. 서버가 기록 전에 종료되면 lease 만료 후 회수되며 이는 at-least-once 특성 안에 있다.
 *
 * <p><b>성공 결과는 아직 {@code tasks}에 반영되지 않는다.</b> 8.3절이 요구하는 CAS 반영과 terminal 전이의 원자성은 MOM-0820이
 * {@link #record}의 성공 분기 안에 CAS와 event append를 넣어 완성한다. 그때까지 이 클래스는 원장만 닫으므로, 세 설정 축이 모두 기본 비활성인
 * 상태를 유지해야 한다. drain을 먼저 켜면 모델 결과가 반영되지 않은 채 원장만 종료된다.
 */
@Slf4j
@Component
class TaskDraftGenerationLedger {

  private final TaskDraftGenerationRepository repository;
  private final MinsuAsyncProperties asyncProperties;
  private final MinsuJson json;

  TaskDraftGenerationLedger(
      TaskDraftGenerationRepository repository,
      MinsuAsyncProperties asyncProperties,
      MinsuJson json) {
    this.repository = repository;
    this.asyncProperties = asyncProperties;
    this.json = json;
  }

  /**
   * 처리 대상 원장을 claim한다. lease가 만료된 {@code processing}을 <b>먼저</b> 회수하고, 남은 자리를 재시도 시각이 지난 {@code
   * pending}으로 채운다.
   *
   * <p>두 경로를 한 쿼리로 합치지 않은 이유는 각각 다른 부분 인덱스를 타기 때문이다. {@code OR}로 묶으면 둘 다 놓친다.
   *
   * <p><b>회수를 먼저 보는 이유는 기아를 막기 위해서다.</b> 신규를 먼저 보면 매 주기 {@code pending}이 슬롯을 모두 채우는 동안 만료 행이 한 번도
   * 집히지 않고, 그대로 {@code apply_cutoff_at}을 지나면 두 쿼리 모두에서 빠져 영영 회수되지 않는다. "lease 만료 후 다음 주기가 회수한다"는
   * 8.5절의 복구 보장이 부하에 따라 깨지는 것이다.
   *
   * <p>반대 방향의 기아는 생기지 않는다. 만료 집합은 유한하고 자기 자신을 줄인다. 회수된 행은 {@code processing}이 되어 그 집합에서 빠지고, 회수마다
   * 시도 횟수가 올라 결국 상한에서 종료된다. 새로 들어오는 것은 장애로 lease가 만료된 행뿐이다. 반면 {@code pending}은 사용자 요청으로 계속 유입되는 무한
   * 스트림이라, 우선권을 주면 밀리는 쪽이 영원히 밀린다.
   */
  @Transactional
  public List<ClaimedGeneration> claimDue(int limit) {
    Execution execution = asyncProperties.execution();
    List<TaskDraftGeneration> due = new ArrayList<>(repository.lockExpiredProcessing(limit));
    if (due.size() < limit) {
      due.addAll(repository.lockDuePending(limit - due.size()));
    }
    if (due.isEmpty()) {
      return List.of();
    }
    Instant leaseExpiresAt = repository.currentDatabaseTime().plus(execution.lease());
    List<ClaimedGeneration> claimed = new ArrayList<>();
    for (TaskDraftGeneration generation : due) {
      if (generation.isExhausted(execution.maxAttempts())) {
        // 마지막 시도를 claim한 뒤 결과를 기록하기 전에 종료된 잔여 행이다. 다시 집으면 상한을 넘겨
        // 실행하게 되므로 여기서 종결한다.
        generation.complete(CompletionReason.RETRY_EXHAUSTED);
        continue;
      }
      UUID claimToken = UUID.randomUUID();
      generation.claim(claimToken, leaseExpiresAt);
      claimed.add(
          new ClaimedGeneration(
              generation.getId(), generation.getTaskId(), claimToken, snapshotOf(generation)));
    }
    return claimed;
  }

  /**
   * 실행 결과를 원장에 기록한다(9.2절 표).
   *
   * <p>claim token이 다르면 그 결과는 stale이므로 버린다(8.2절). lease가 만료돼 다른 worker가 재claim한 뒤 이전 실행이 뒤늦게 돌아오는
   * 경우이고, at-least-once와 만료 lease를 택한 이상 정상 동작이다.
   */
  @Transactional
  public void record(ClaimedGeneration claim, AsyncGenerationResult result) {
    TaskDraftGeneration generation = repository.lockById(claim.id()).orElseThrow();
    if (!generation.isClaimedBy(claim.claimToken())) {
      log.warn("만료된 minsu claim 결과 무시 taskId={} outcome={}", claim.taskId(), result.outcome());
      return;
    }
    Instant now = repository.currentDatabaseTime();
    if (generation.isPastApplyCutoff(now)) {
      // 반영 창이 닫힌 뒤의 결과는 쓰지 않는다(8.6절). 읽기 투영이 이미 ready로 알렸을 수 있어, 여기서
      // 반영하면 ready로 알린 title이 뒤늦게 바뀐다.
      generation.complete(CompletionReason.DEADLINE_EXCEEDED);
      return;
    }
    switch (result.outcome()) {
      // MOM-0820이 이 분기 안에 tasks CAS 반영과 task.draft_generated append를 넣는다(8.3절).
      // 셋이 한 트랜잭션이어야 반영은 됐는데 원장이 processing으로 남는 상태가 생기지 않는다.
      case GENERATED, GENERATED_TITLE_FALLBACK, GENERATED_TRUNCATED ->
          generation.complete(CompletionReason.GENERATED);
      // 입력이 그대로이므로 재시도해도 같다.
      case INSUFFICIENT_CONTEXT -> generation.complete(CompletionReason.INSUFFICIENT_CONTEXT);
      // 설정 수정은 배포 사건이라 재시도로 기다리지 않는다. claim 자체가 설정 유효를 전제하므로
      // (11.2절) 이 값은 claim 이후 설정이 무효해진 경합에서만 도달한다.
      case INVALID_CONFIG -> generation.complete(CompletionReason.INVALID_CONFIG);
      // structured output 위반은 결정적 오류가 아니다. 같은 입력으로 반복 실패하면 프롬프트·스키마
      // 문제이고 그것은 outcome별 retry_exhausted 비율로 구분한다.
      case TIMEOUT, PROVIDER_ERROR, INVALID_RESPONSE, INVALID_OUTPUT ->
          scheduleRetryOrExhaust(generation, now);
      // 이 둘은 요청 경로의 값이라 claim된 실행에서는 나올 수 없다. 나왔다면 실행 경로가 요청 경로의
      // 분기를 잘못 타고 있는 것이므로 조용히 닫지 않는다.
      case DISABLED, DEFERRED ->
          throw new IllegalStateException("비동기 실행이 요청 경로 outcome을 돌려줬습니다: " + result.outcome());
    }
  }

  private void scheduleRetryOrExhaust(TaskDraftGeneration generation, Instant now) {
    Execution execution = asyncProperties.execution();
    if (generation.isExhausted(execution.maxAttempts())) {
      generation.complete(CompletionReason.RETRY_EXHAUSTED);
      return;
    }
    // 백오프는 결과를 기록한 시점부터 잰다. claim 시점부터 재면 실행에 걸린 시간만큼 대기가 짧아진다.
    generation.scheduleRetry(now.plus(execution.backoffFor(generation.getAttemptCount())));
  }

  private SignalTaskDraftInput snapshotOf(TaskDraftGeneration generation) {
    return new SignalTaskDraftInput(
        generation.getSignalTitle(),
        generation.getSignalType(),
        generation.getSignalDescription(),
        generation.getSignalImpact(),
        evidenceOf(generation.getSignalEvidence()));
  }

  private List<SignalTaskDraftInput.Evidence> evidenceOf(String evidenceJson) {
    // 역직렬화 실패는 빈 근거로 취급한다. evidence는 프롬프트의 보조 재료라 없어도 생성이 성립하고,
    // 여기서 예외를 던지면 claim은 커밋된 채 실행만 실패해 lease 만료까지 기다리게 된다.
    return json.read(evidenceJson, SignalTaskDraftInput.Evidence[].class)
        .map(Arrays::asList)
        .orElseGet(
            () -> {
              log.warn("minsu 원장 evidence 역직렬화 실패, 빈 근거로 실행합니다");
              return List.of();
            });
  }
}
