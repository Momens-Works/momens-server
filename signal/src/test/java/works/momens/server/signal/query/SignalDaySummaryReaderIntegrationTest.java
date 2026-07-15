package works.momens.server.signal.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.signal.SignalDaySummaryReader;

/**
 * 프로젝트 하루 시그널 요약 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers)에서 workspace/project/날짜 스코프 조회, 값이 없을 때 빈 값, 소프트 삭제 제외를 확인합니다.
 * signal_day_summaries는 worker(또는 dev fixture)가 채우는 미러라 fixture는 네이티브 SQL로 삽입합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SignalDaySummaryReaderImpl.class)
@DisplayName("SignalDaySummaryReader 통합 테스트")
class SignalDaySummaryReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SignalDaySummaryReader signalDaySummaryReader;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("해당 날짜 요약이 있으면 반환한다")
  void findSummaryReturnsSummaryForDate() {
    UUID workspaceId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 16);
    insertSummary(workspaceId, projectId, date, "오늘 요약 문단", null);

    Optional<String> summary = signalDaySummaryReader.findSummary(workspaceId, projectId, date);

    assertThat(summary).contains("오늘 요약 문단");
  }

  @Test
  @DisplayName("해당 날짜 요약이 없으면 빈 값이다")
  void findSummaryIsEmptyWhenMissing() {
    Optional<String> summary =
        signalDaySummaryReader.findSummary(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 7, 16));

    assertThat(summary).isEmpty();
  }

  @Test
  @DisplayName("소프트 삭제된 요약은 없는 것으로 취급한다")
  void findSummaryExcludesSoftDeleted() {
    UUID workspaceId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 16);
    insertSummary(workspaceId, projectId, date, "삭제된 요약", Instant.now());

    Optional<String> summary = signalDaySummaryReader.findSummary(workspaceId, projectId, date);

    assertThat(summary).isEmpty();
  }

  @Test
  @DisplayName("다른 프로젝트나 날짜의 요약은 반환하지 않는다")
  void findSummaryScopesToProjectAndDate() {
    UUID workspaceId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 7, 16);
    insertSummary(workspaceId, projectId, date.minusDays(1), "어제 요약", null);
    insertSummary(workspaceId, UUID.randomUUID(), date, "다른 프로젝트 요약", null);

    Optional<String> summary = signalDaySummaryReader.findSummary(workspaceId, projectId, date);

    assertThat(summary).isEmpty();
  }

  private void insertSummary(
      UUID workspaceId, UUID projectId, LocalDate date, String summary, Instant deletedAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_day_summaries (id, workspace_id, project_id, summary_date,"
                + " summary, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, workspaceId)
        .setParameter(3, projectId)
        .setParameter(4, date)
        .setParameter(5, summary)
        .setParameter(6, deletedAt)
        .executeUpdate();
    entityManager.clear();
  }
}
