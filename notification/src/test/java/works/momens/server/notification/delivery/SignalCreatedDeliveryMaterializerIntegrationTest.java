package works.momens.server.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import works.momens.server.outbox.OutboxEventReader;
import works.momens.server.outbox.OutboxEventView;
import works.momens.server.signal.SignalReader;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;

/**
 * signal.created materialization의 수신자 결정, 기기별 중복 방지(복합 PK), watermark 전진·시드를 실제 PostgreSQL로 검증합니다.
 * outbox·signal·workspace public API와 device nested 모듈의 설치 원장 계약은 이 경계 밖 소유라 mock으로 둡니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, SignalCreatedDeliveryMaterializer.class})
class SignalCreatedDeliveryMaterializerIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID SIGNAL_ID = UUID.randomUUID();
  private static final UUID MEMBER_A = UUID.randomUUID();
  private static final UUID MEMBER_B = UUID.randomUUID();
  private static final UUID INSTALLATION_A = UUID.randomUUID();
  private static final UUID INSTALLATION_B = UUID.randomUUID();
  private static final long EVENT_ID = 11L;

  @Autowired private SignalCreatedDeliveryMaterializer materializer;
  @Autowired private PushDeliveryRepository deliveryRepository;
  @Autowired private NotificationConsumerOffsetRepository offsetRepository;

  @MockitoBean private PushInstallationDirectory pushInstallationDirectory;
  @MockitoBean private OutboxEventReader outboxEventReader;
  @MockitoBean private SignalReader signalReader;
  @MockitoBean private WorkspaceAccess workspaceAccess;

  @BeforeEach
  void stubDefaults() {
    when(outboxEventReader.readAfter(anyLong(), any(), anyInt())).thenReturn(List.of());
    when(outboxEventReader.latestIdCreatedBefore(any())).thenReturn(0L);
    when(pushInstallationDirectory.findActiveAndroid(anyCollection())).thenReturn(List.of());
  }

  @Test
  @DisplayName("workspace 구성원의 활성 android 설치별 pending delivery를 만들고 watermark를 전진시킨다")
  void materializesDeliveriesForActiveInstallations() {
    stubSignalCreatedEvent();
    when(pushInstallationDirectory.findActiveAndroid(List.of(MEMBER_A, MEMBER_B)))
        .thenReturn(
            List.of(
                new InstallationSnapshot(INSTALLATION_A, MEMBER_A, "token-a", true),
                new InstallationSnapshot(INSTALLATION_B, MEMBER_B, "token-b", true)));

    boolean materialized = materializer.materialize();

    assertThat(materialized).isTrue();
    List<PushDelivery> deliveries = deliveryRepository.findAll();
    assertThat(deliveries)
        .extracting(PushDelivery::getInstallationId)
        .containsExactlyInAnyOrder(INSTALLATION_A, INSTALLATION_B);
    assertThat(deliveries)
        .allSatisfy(
            delivery -> {
              assertThat(delivery.isPending()).isTrue();
              assertThat(delivery.getAttemptCount()).isZero();
              assertThat(delivery.getOutboxEventId()).isEqualTo(EVENT_ID);
            });
    assertThat(offsetOf()).isEqualTo(EVENT_ID);
  }

  @Test
  @DisplayName("같은 event를 다시 소비해도 delivery가 중복 생성되지 않는다")
  void replayDoesNotDuplicateDeliveries() {
    stubSignalCreatedEvent();
    when(pushInstallationDirectory.findActiveAndroid(List.of(MEMBER_A, MEMBER_B)))
        .thenReturn(List.of(new InstallationSnapshot(INSTALLATION_A, MEMBER_A, "token-a", true)));

    materializer.materialize();
    materializer.materialize();

    assertThat(deliveryRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Signal이 이미 없는 event는 건너뛰고 watermark는 전진시킨다")
  void skipsMissingSignalButAdvancesWatermark() {
    when(outboxEventReader.readAfter(eq(0L), any(), anyInt()))
        .thenReturn(List.of(signalCreatedEvent()));
    when(signalReader.findLive(SIGNAL_ID)).thenReturn(Optional.empty());

    boolean materialized = materializer.materialize();

    assertThat(materialized).isFalse();
    assertThat(deliveryRepository.count()).isZero();
    assertThat(offsetOf()).isEqualTo(EVENT_ID);
  }

  @Test
  @DisplayName("signal.created가 아닌 event는 delivery 없이 watermark만 전진시킨다")
  void ignoresOtherEventTypes() {
    when(outboxEventReader.readAfter(eq(0L), any(), anyInt()))
        .thenReturn(
            List.of(
                new OutboxEventView(
                    EVENT_ID,
                    WORKSPACE_ID,
                    "signal",
                    SIGNAL_ID.toString(),
                    "signal.dismissed",
                    Instant.now())));

    assertThat(materializer.materialize()).isFalse();
    assertThat(deliveryRepository.count()).isZero();
    assertThat(offsetOf()).isEqualTo(EVENT_ID);
  }

  @Test
  @DisplayName("최초 실행은 watermark를 현재 outbox 끝으로 시드해 과거 event를 소급 발송하지 않는다")
  void firstRunSeedsWatermarkAtCurrentTail() {
    when(outboxEventReader.latestIdCreatedBefore(any())).thenReturn(42L);
    when(outboxEventReader.readAfter(eq(42L), any(), anyInt())).thenReturn(List.of());

    materializer.materialize();

    assertThat(offsetOf()).isEqualTo(42L);
  }

  private void stubSignalCreatedEvent() {
    when(outboxEventReader.readAfter(eq(0L), any(), anyInt()))
        .thenReturn(List.of(signalCreatedEvent()));
    when(signalReader.findLive(SIGNAL_ID))
        .thenReturn(
            Optional.of(
                new SignalReader.Snapshot(SIGNAL_ID, WORKSPACE_ID, PROJECT_ID, "결제 정책 결정 3일째 보류")));
    when(workspaceAccess.listMemberships(WORKSPACE_ID))
        .thenReturn(
            List.of(
                new WorkspaceMembership(MEMBER_A, "owner"),
                new WorkspaceMembership(MEMBER_B, "member")));
  }

  private static OutboxEventView signalCreatedEvent() {
    return new OutboxEventView(
        EVENT_ID, WORKSPACE_ID, "signal", SIGNAL_ID.toString(), "signal.created", Instant.now());
  }

  private long offsetOf() {
    return offsetRepository
        .findById(SignalCreatedDeliveryMaterializer.CONSUMER_NAME)
        .orElseThrow()
        .getLastOutboxId();
  }
}
