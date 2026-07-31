package works.momens.server.signal.action;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import works.momens.server.common.persistence.BaseEntity;

/**
 * 사용자 Signal action 처리 원장(ledger).
 *
 * <p>{@code signal_id}에 {@code UNIQUE} 제약이 있어 Signal 한 건당 action은 하나만 기록된다(멱등성의 단일 출처, SD-4).
 * {@code resultTaskId}는 convert-to-task일 때만 값이 있고, dismiss는 {@code null}이다. 레거시 공유 스키마 반영은
 * B1(momens-api 마이그레이션)에서 확정하고, 이 엔티티는 local/test 미러({@code V20260707110000})를 그대로 매핑한다.
 */
@Getter
@Entity
@Table(name = "signal_actions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class SignalAction extends BaseEntity {

  @Column(name = "workspace_id", nullable = false, columnDefinition = "uuid")
  private UUID workspaceId;

  @Column(name = "signal_id", nullable = false, columnDefinition = "uuid")
  private UUID signalId;

  @Column(name = "action_type", nullable = false)
  private String actionType;

  @Column(name = "result_task_id", columnDefinition = "uuid")
  private UUID resultTaskId;

  @Column(name = "processed_by_user_id", nullable = false, columnDefinition = "uuid")
  private UUID processedByUserId;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @Builder
  private SignalAction(
      UUID workspaceId,
      UUID signalId,
      String actionType,
      UUID resultTaskId,
      UUID processedByUserId) {
    this.workspaceId = workspaceId;
    this.signalId = signalId;
    this.actionType = actionType;
    this.resultTaskId = resultTaskId;
    this.processedByUserId = processedByUserId;
    this.processedAt = Instant.now();
  }
}
