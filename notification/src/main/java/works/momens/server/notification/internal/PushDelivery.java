package works.momens.server.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 기기별 push 발송 상태(docs/design/signal-push-demo-design.md 10.2절).
 *
 * <p>{@code (outbox_event_id, installation_id)} 복합 PK가 동일 event의 기기별 중복 materialization을 막으므로 공유
 * 베이스 엔티티(UUID PK)를 상속하지 않는다. 행 생성은 native {@code ON CONFLICT DO NOTHING} insert가 담당하고, JPA는 클레임·상태
 * 전이(update)만 다룬다. token은 복제하지 않고 installation을 참조한다.
 */
@Getter
@Entity
@Table(name = "push_deliveries")
@IdClass(PushDeliveryId.class)
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDelivery {

  static final int MAX_ATTEMPTS = 4;

  @Id
  @Column(name = "outbox_event_id")
  private long outboxEventId;

  @Id
  @Column(name = "installation_id", columnDefinition = "uuid")
  private UUID installationId;

  @Column(name = "target_user_id", nullable = false, columnDefinition = "uuid")
  private UUID targetUserId;

  @Column(nullable = false)
  private String status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "failure_category")
  private String failureCategory;

  @Column(name = "sent_at")
  private Instant sentAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  boolean isPending() {
    return DeliveryStatus.PENDING.value().equals(status);
  }

  boolean isExhausted() {
    return attemptCount >= MAX_ATTEMPTS;
  }

  /** 전송 시도를 클레임한다. 시도 횟수를 올리고 다음 백오프 시각을 먼저 기록해, 전송 중 종료돼도 그 시각에 재시도된다(10.3절). */
  void claimAttempt(Instant nextAttemptAt) {
    this.attemptCount++;
    this.nextAttemptAt = nextAttemptAt;
  }

  void markSent() {
    this.status = DeliveryStatus.SENT.value();
    this.sentAt = Instant.now();
    this.failureCategory = null;
  }

  void markFailed(String failureCategory) {
    this.status = DeliveryStatus.FAILED.value();
    this.failureCategory = failureCategory;
  }

  /** 계정 간 오발송 방지 등 전송 대상에서 제외한다(10.2절). */
  void cancel(String failureCategory) {
    this.status = DeliveryStatus.CANCELLED.value();
    this.failureCategory = failureCategory;
  }
}
