package works.momens.server.minsu.internal.ledger;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import works.momens.server.minsu.SignalTaskDraftInput;
import works.momens.server.minsu.TaskDraft;
import works.momens.server.minsu.TaskDraftAsyncIntent;
import works.momens.server.minsu.TaskDraftEnrollmentException;
import works.momens.server.minsu.internal.config.MinsuAsyncProperties;
import works.momens.server.minsu.internal.json.MinsuJson;

/**
 * 생성 원장 적재(docs/design/minsu-async-task-draft-design.md 5.3절).
 *
 * <p>호출자(convert)의 쓰기 트랜잭션 안에서 실행된다. 여기에 {@code @Transactional}을 두지 않는 것은 의도적이다. 원장 insert는 {@code
 * tasks}·{@code signal_actions}와 <b>같은</b> 트랜잭션이어야 하고, 별도 경계를 만들면 롤백이 갈려 fail-closed가 깨진다.
 */
@Component
public class TaskDraftGenerationEnroller {

  private final TaskDraftGenerationRepository repository;
  private final MinsuAsyncProperties asyncProperties;
  private final MinsuJson json;

  TaskDraftGenerationEnroller(
      TaskDraftGenerationRepository repository,
      MinsuAsyncProperties asyncProperties,
      MinsuJson json) {
    this.repository = repository;
    this.asyncProperties = asyncProperties;
    this.json = json;
  }

  /** draft 확보 시점에 입력 snapshot을 봉인한다. 해석은 {@link #enroll}만 한다. */
  public TaskDraftAsyncIntent intentFor(SignalTaskDraftInput input) {
    return new LedgerEnrollment(input);
  }

  /**
   * {@code pending} 원장을 적재한다.
   *
   * <p>{@code saveAndFlush}로 즉시 flush하는 이유는 실패를 <b>이 호출 안에서</b> 잡기 위해서다. 지연 flush에 맡기면 제약 위반이 커밋
   * 시점에 터져 호출자의 다른 제약 위반과 구분되지 않는다(5.3절이 구분하라고 한 두 실패 경로).
   */
  public void enroll(
      TaskDraftAsyncIntent intent, TaskDraft baseline, UUID taskId, UUID workspaceId) {
    if (!(intent instanceof LedgerEnrollment enrollment)) {
      throw new IllegalArgumentException(
          "Minsu가 발급하지 않은 asyncIntent입니다: " + intent.getClass().getName());
    }
    SignalTaskDraftInput snapshot = enrollment.snapshot();
    Instant enrolledAt = repository.currentDatabaseTime();
    Instant readDeadline = enrolledAt.plus(asyncProperties.generationDeadline());
    try {
      repository.saveAndFlush(
          TaskDraftGeneration.builder()
              .workspaceId(workspaceId)
              .taskId(taskId)
              .signalTitle(snapshot.title())
              .signalType(snapshot.type())
              .signalDescription(snapshot.description())
              .signalImpact(snapshot.impact())
              .signalEvidence(json.write(snapshot.evidence()))
              .baselineTitle(baseline.title())
              .baselineRole(baseline.role().value())
              .baselinePriority(baseline.priority().value())
              .readDeadlineAt(readDeadline)
              .applyCutoffAt(readDeadline.minus(asyncProperties.applyMargin()))
              // 적재 직후부터 처리 대상이다. 첫 시도의 대기는 백오프가 아니라 scheduler 주기다.
              .nextAttemptAt(enrolledAt)
              .build());
    } catch (DataAccessException e) {
      throw new TaskDraftEnrollmentException("Minsu 생성 원장 적재에 실패했습니다: taskId=" + taskId, e);
    }
  }
}
