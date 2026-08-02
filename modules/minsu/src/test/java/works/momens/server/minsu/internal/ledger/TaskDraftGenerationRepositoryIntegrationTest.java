package works.momens.server.minsu.internal.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 생성 원장의 컬럼 매핑과 DB 제약을 실제 PostgreSQL로 검증한다(MOM-0817).
 *
 * <p>상태 전이 로직은 후속 티켓이 붙이므로, 여기서는 전이가 지켜야 할 불변식을 native update로 직접 위반시켜 DB가 막는지 확인한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TaskDraftGenerationRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final UUID WORKSPACE_ID = UUID.randomUUID();

  @Autowired private TaskDraftGenerationRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("적재한 snapshot과 baseline을 그대로 조회한다")
  void savesAndReadsBack() {
    UUID taskId = UUID.randomUUID();
    Instant deadline = Instant.now().plus(10, ChronoUnit.MINUTES);

    repository.save(
        generation(taskId)
            .signalTitle("결제 실패율이 올라감")
            .signalDescription("2026-08-01부터 카드 결제 실패가 늘었다")
            .signalImpact("결제 전환율 하락")
            .signalEvidence("[{\"target\":\"결제\",\"change\":\"실패율 3%\",\"impact\":\"전환 하락\"}]")
            .readDeadlineAt(deadline)
            .applyCutoffAt(deadline)
            .build());
    entityManager.flush();
    entityManager.clear();

    TaskDraftGeneration saved = repository.findByTaskId(taskId).orElseThrow();

    assertThat(saved.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
    assertThat(saved.getSignalTitle()).isEqualTo("결제 실패율이 올라감");
    assertThat(saved.getSignalImpact()).isEqualTo("결제 전환율 하락");
    assertThat(saved.getSignalEvidence()).contains("\"target\"").contains("결제");
    assertThat(saved.getBaselineTitle()).isEqualTo("결제 실패율 대응");
    assertThat(saved.getBaselineRole()).isEqualTo("backend");
    assertThat(saved.getBaselinePriority()).isEqualTo("medium");
    assertThat(saved.getStatus()).isEqualTo("pending");
    assertThat(saved.getCompletionReason()).isNull();
    assertThat(saved.getAttemptCount()).isZero();
    assertThat(saved.getClaimToken()).isNull();
    assertThat(saved.getLeaseExpiresAt()).isNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("같은 task에 두 번째 원장을 적재하지 못한다")
  void rejectsSecondGenerationForSameTask() {
    UUID taskId = UUID.randomUUID();
    repository.save(generation(taskId).build());
    entityManager.flush();

    assertThatThrownBy(() -> repository.saveAndFlush(generation(taskId).build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("종료 상태와 종료 사유는 함께만 존재한다")
  void rejectsStatusAndReasonMismatch() {
    UUID id = persistPending();

    assertThatThrownBy(() -> update(id, "status = 'completed'"))
        .rootCause()
        .hasMessageContaining("minsu_task_draft_generations_reason_check");
    assertThatThrownBy(() -> update(id, "completion_reason = 'generated'"))
        .rootCause()
        .hasMessageContaining("minsu_task_draft_generations_reason_check");
  }

  @Test
  @DisplayName("claim token과 lease는 processing에서만 존재한다")
  void rejectsClaimWithoutProcessing() {
    UUID id = persistPending();

    assertThatThrownBy(() -> update(id, "status = 'processing'"))
        .rootCause()
        .hasMessageContaining("minsu_task_draft_generations_claim_check");
    assertThatThrownBy(
            () -> update(id, "claim_token = gen_random_uuid(), lease_expires_at = NOW()"))
        .rootCause()
        .hasMessageContaining("minsu_task_draft_generations_claim_check");
  }

  private UUID persistPending() {
    TaskDraftGeneration saved = repository.save(generation(UUID.randomUUID()).build());
    entityManager.flush();
    entityManager.clear();
    return saved.getId();
  }

  private void update(UUID id, String assignment) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "UPDATE minsu_task_draft_generations SET " + assignment + " WHERE id = :id")
        .setParameter("id", id)
        .executeUpdate();
  }

  private TaskDraftGeneration.TaskDraftGenerationBuilder generation(UUID taskId) {
    Instant deadline = Instant.now().plus(10, ChronoUnit.MINUTES);
    return TaskDraftGeneration.builder()
        .workspaceId(WORKSPACE_ID)
        .taskId(taskId)
        .signalType("risk")
        .signalEvidence("[]")
        .baselineTitle("결제 실패율 대응")
        .baselineRole("backend")
        .baselinePriority("medium")
        .readDeadlineAt(deadline)
        .applyCutoffAt(deadline)
        .nextAttemptAt(Instant.now());
  }
}
