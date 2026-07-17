package works.momens.server.notification.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.notification.device.PushInstallationDirectory;
import works.momens.server.notification.device.PushInstallationDirectory.InstallationSnapshot;
import works.momens.server.notification.fcm.FcmClient.FcmSendResult;

/**
 * delivery 클레임(백오프 선반영·소유자 재확인)과 결과 기록(sent·invalid token·재시도 한도)을 실제 PostgreSQL로
 * 검증합니다(docs/design/signal-push-demo-design.md 10.2·10.3절). 설치 원장은 device nested 모듈 소유라 계약 ({@link
 * PushInstallationDirectory}) mock으로 둡니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, PushDeliveryLedger.class})
class PushDeliveryLedgerIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final long EVENT_ID = 7L;
  private static final UUID USER_ID = UUID.randomUUID();
  private static final UUID INSTALLATION_ID = UUID.randomUUID();

  @Autowired private PushDeliveryLedger ledger;
  @Autowired private PushDeliveryRepository deliveryRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private PushInstallationDirectory pushInstallationDirectory;

  @BeforeEach
  void stubActiveInstallation() {
    when(pushInstallationDirectory.findByIds(anyCollection()))
        .thenReturn(List.of(new InstallationSnapshot(INSTALLATION_ID, USER_ID, "token-1", true)));
  }

  @Test
  @DisplayName("클레임은 시도 횟수와 다음 백오프 시각을 선반영하고 현재 token을 돌려준다")
  void claimAdvancesAttemptAndBackoff() {
    materializePending();

    List<ClaimedPushDelivery> claims = ledger.claimDue(10);

    assertThat(claims).hasSize(1);
    assertThat(claims.getFirst().fcmRegistrationToken()).isEqualTo("token-1");
    PushDelivery delivery = reload();
    assertThat(delivery.getAttemptCount()).isEqualTo(1);
    assertThat(delivery.isPending()).isTrue();
    // 다음 백오프 시각이 선반영돼 즉시 재클레임되지 않는다.
    assertThat(ledger.claimDue(10)).isEmpty();
  }

  @Test
  @DisplayName("설치가 비활성이거나 다른 사용자에게 이전됐으면 cancelled 처리하고 클레임하지 않는다")
  void claimCancelsMismatchedInstallation() {
    materializePending();
    when(pushInstallationDirectory.findByIds(anyCollection()))
        .thenReturn(
            List.of(new InstallationSnapshot(INSTALLATION_ID, UUID.randomUUID(), "token-1", true)));

    assertThat(ledger.claimDue(10)).isEmpty();
    PushDelivery delivery = reload();
    assertThat(delivery.getStatus()).isEqualTo("cancelled");
    assertThat(delivery.getFailureCategory())
        .isEqualTo(PushDeliveryLedger.CATEGORY_INSTALLATION_MISMATCH);
  }

  @Test
  @DisplayName("전송 성공 기록은 sent와 sent_at을 남긴다")
  void recordSentMarksDelivery() {
    materializePending();
    List<ClaimedPushDelivery> claims = ledger.claimDue(10);

    ledger.record(claims, List.of(FcmSendResult.SENT));

    PushDelivery delivery = reload();
    assertThat(delivery.getStatus()).isEqualTo("sent");
    assertThat(delivery.getSentAt()).isNotNull();
  }

  @Test
  @DisplayName("무효 token은 재시도 없이 failed 처리하고 설치를 비활성화한다")
  void recordInvalidTokenDeactivatesInstallation() {
    materializePending();
    List<ClaimedPushDelivery> claims = ledger.claimDue(10);

    ledger.record(claims, List.of(FcmSendResult.INVALID_TOKEN));

    PushDelivery delivery = reload();
    assertThat(delivery.getStatus()).isEqualTo("failed");
    assertThat(delivery.getFailureCategory()).isEqualTo(PushDeliveryLedger.CATEGORY_INVALID_TOKEN);
    verify(pushInstallationDirectory).deactivate(INSTALLATION_ID);
  }

  @Test
  @DisplayName("일시 실패는 pending을 유지하고 최대 4회 시도 후 failed로 종결한다")
  void transientFailureRetriesUntilExhausted() {
    materializePending();

    for (int attempt = 1; attempt <= 4; attempt++) {
      backdateNextAttempt();
      List<ClaimedPushDelivery> claims = ledger.claimDue(10);
      assertThat(claims).hasSize(1);
      ledger.record(claims, List.of(FcmSendResult.TRANSIENT_FAILURE));
      entityManager.flush();
      entityManager.clear();

      PushDelivery delivery = reload();
      assertThat(delivery.getAttemptCount()).isEqualTo(attempt);
      if (attempt < 4) {
        assertThat(delivery.isPending()).isTrue();
      } else {
        assertThat(delivery.getStatus()).isEqualTo("failed");
        assertThat(delivery.getFailureCategory())
            .isEqualTo(PushDeliveryLedger.CATEGORY_RETRY_EXHAUSTED);
      }
    }
  }

  @Test
  @DisplayName("클레임 후 결과 기록 전에 종료된 한도 초과 행은 다음 클레임이 failed로 종결한다")
  void claimFinalizesExhaustedLeftover() {
    materializePending();
    entityManager
        .createNativeQuery(
            "UPDATE push_deliveries SET attempt_count = 4, next_attempt_at = NOW() - interval '1 minute'")
        .executeUpdate();
    entityManager.clear();

    assertThat(ledger.claimDue(10)).isEmpty();
    PushDelivery delivery = reload();
    assertThat(delivery.getStatus()).isEqualTo("failed");
    assertThat(delivery.getFailureCategory())
        .isEqualTo(PushDeliveryLedger.CATEGORY_RETRY_EXHAUSTED);
  }

  @Test
  @DisplayName("event 본문을 hydrate할 수 없는 delivery는 cancelled로 종결한다")
  void cancelAllMarksCancelled() {
    materializePending();
    List<ClaimedPushDelivery> claims = ledger.claimDue(10);

    ledger.cancelAll(claims, "event_unavailable");

    assertThat(reload().getStatus()).isEqualTo("cancelled");
  }

  private void materializePending() {
    deliveryRepository.insertPendingIgnoringConflict(EVENT_ID, INSTALLATION_ID, USER_ID);
    entityManager.clear();
  }

  private void backdateNextAttempt() {
    entityManager
        .createNativeQuery(
            "UPDATE push_deliveries SET next_attempt_at = NOW() - interval '1 minute'")
        .executeUpdate();
    entityManager.clear();
  }

  private PushDelivery reload() {
    entityManager.flush();
    entityManager.clear();
    return deliveryRepository.findById(new PushDeliveryId(EVENT_ID, INSTALLATION_ID)).orElseThrow();
  }
}
