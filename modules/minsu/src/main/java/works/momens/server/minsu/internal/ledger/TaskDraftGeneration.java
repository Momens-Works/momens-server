package works.momens.server.minsu.internal.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import works.momens.server.common.persistence.BaseEntity;

/**
 * Minsu task draft 비동기 생성 원장(docs/design/minsu-async-task-draft-design.md 6절).
 *
 * <p>convert 트랜잭션이 {@code pending}으로 적재하고 scheduler가 claim해 처리한다. 이 클래스는 원장의 상태와 불변식만 담고, claim·반영
 * 로직은 후속 티켓(MOM-0819·0820)이 붙인다.
 *
 * <p>baseline은 값으로 소유한다. convert가 {@code tasks}에 실제로 쓴 title·role·priority를 적재 시점에 복사해 두고, 반영 시점에
 * 고정 fallback 규칙을 다시 계산해 비교하지 않는다. 재계산 방식은 규칙이 바뀌는 순간 진행 중이던 작업 전부를 {@code user_edited}로 오분류한다.
 *
 * <p>입력 snapshot은 evidence만 jsonb로 담는다. 원장이 evidence의 구조를 해석할 일이 없고, 해석하는 쪽은 프롬프트를 만드는 시점에 다시
 * 역직렬화한다({@code outbox_events.payload}와 같은 방식).
 */
@Getter
@Entity
@Table(name = "minsu_task_draft_generations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TaskDraftGeneration extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "task_id", nullable = false, columnDefinition = "uuid")
  private UUID taskId;

  @Column(name = "signal_title", nullable = false)
  private String signalTitle;

  @Column(name = "signal_type", nullable = false)
  private String signalType;

  @Column(name = "signal_description", nullable = false)
  private String signalDescription;

  @Column(name = "signal_impact")
  private String signalImpact;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "signal_evidence", nullable = false, columnDefinition = "jsonb")
  private String signalEvidence;

  @Column(name = "baseline_title", nullable = false)
  private String baselineTitle;

  @Column(name = "baseline_role")
  private String baselineRole;

  @Column(name = "baseline_priority", nullable = false)
  private String baselinePriority;

  @Column(name = "read_deadline_at", nullable = false)
  private Instant readDeadlineAt;

  @Column(name = "apply_cutoff_at", nullable = false)
  private Instant applyCutoffAt;

  @Column(nullable = false)
  private String status;

  @Column(name = "completion_reason")
  private String completionReason;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "claim_token", columnDefinition = "uuid")
  private UUID claimToken;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  /** convert 트랜잭션이 적재하는 유일한 생성 경로다. 적재 직후 상태는 항상 {@code pending}이고 시도 횟수는 0이다. */
  @Builder
  TaskDraftGeneration(
      UUID workspaceId,
      UUID taskId,
      String signalTitle,
      String signalType,
      String signalDescription,
      String signalImpact,
      String signalEvidence,
      String baselineTitle,
      String baselineRole,
      String baselinePriority,
      Instant readDeadlineAt,
      Instant applyCutoffAt,
      Instant nextAttemptAt) {
    this.workspaceId = workspaceId;
    this.taskId = taskId;
    this.signalTitle = signalTitle;
    this.signalType = signalType;
    this.signalDescription = signalDescription;
    this.signalImpact = signalImpact;
    this.signalEvidence = signalEvidence;
    this.baselineTitle = baselineTitle;
    this.baselineRole = baselineRole;
    this.baselinePriority = baselinePriority;
    this.readDeadlineAt = readDeadlineAt;
    this.applyCutoffAt = applyCutoffAt;
    this.nextAttemptAt = nextAttemptAt;
    this.status = GenerationStatus.PENDING.value();
  }
}
