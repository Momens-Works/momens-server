package works.momens.server.signal.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * Signal 읽기 모델 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers)에서 소프트 삭제 제외 id 조회, 컬럼 매핑, 미처리(signal_actions 없음) 필터를 확인합니다.
 * signals·signal_actions는 각각 worker·api-server가 쓰는 테이블이므로 fixture는 네이티브 SQL로 삽입합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SignalRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private SignalRepository signalRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("미삭제 Signal만 id로 조회된다")
  void findByIdResolvesLiveSignalOnly() {
    UUID projectId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
    UUID live = insertSignal(projectId, "risk", "live", null, createdAt);
    UUID deleted = insertSignal(projectId, "risk", "gone", Instant.now(), createdAt);

    assertThat(signalRepository.findByIdAndDeletedAtIsNull(live)).isPresent();
    assertThat(signalRepository.findByIdAndDeletedAtIsNull(deleted)).isEmpty();
    assertThat(signalRepository.findByIdAndDeletedAtIsNull(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("Signal 컬럼을 엔티티로 매핑한다")
  void mapsSignalColumns() {
    UUID projectId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-06-28T00:48:00Z");
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signals (id, workspace_id, project_id, type, title, description, impact,"
                + " minsu_suggestion, occurred_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)")
        .setParameter(1, id)
        .setParameter(2, workspaceId)
        .setParameter(3, projectId)
        .setParameter(4, "risk")
        .setParameter(5, "Android 13+ 권한 요청 플로우에서 이탈 가능성 발견")
        .setParameter(6, "권한 요청 타이밍이 늦어 이탈 가능성이 있습니다.")
        .setParameter(7, "MVP 완료율에 영향을 줄 수 있습니다.")
        .setParameter(8, "내용이 들어갈 공간입니다")
        .setParameter(9, occurredAt)
        .executeUpdate();
    entityManager.clear();

    Signal signal = signalRepository.findByIdAndDeletedAtIsNull(id).orElseThrow();

    assertThat(signal.getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(signal.getProjectId()).isEqualTo(projectId);
    assertThat(signal.getType()).isEqualTo("risk");
    assertThat(signal.getTitle()).isEqualTo("Android 13+ 권한 요청 플로우에서 이탈 가능성 발견");
    assertThat(signal.getDescription()).isEqualTo("권한 요청 타이밍이 늦어 이탈 가능성이 있습니다.");
    assertThat(signal.getImpact()).isEqualTo("MVP 완료율에 영향을 줄 수 있습니다.");
    assertThat(signal.getMinsuSuggestion()).isEqualTo("내용이 들어갈 공간입니다");
    assertThat(signal.getOccurredAt()).isEqualTo(occurredAt);
  }

  @Test
  @DisplayName("signal_actions가 있는 Signal과 소프트 삭제된 Signal은 미처리 목록에서 빠진다")
  void findUnprocessedExcludesSignalsWithActionOrDeleted() {
    UUID projectId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
    UUID unprocessed = insertSignal(projectId, "risk", "unprocessed", null, createdAt);
    UUID processed =
        insertSignal(projectId, "decision", "processed", null, createdAt.minusSeconds(60));
    UUID deleted = insertSignal(projectId, "risk", "gone", Instant.now(), createdAt);
    insertSignal(UUID.randomUUID(), "risk", "otherProject", null, createdAt);
    insertAction(processed);

    List<Signal> result = signalRepository.findUnprocessedByProjectId(projectId);

    assertThat(result).extracting(Signal::getId).containsExactly(unprocessed);
    assertThat(result).extracting(Signal::getId).doesNotContain(processed, deleted);
  }

  @Test
  @DisplayName("생성 시각이 같으면 id 내림차순으로 순서를 고정한다")
  void findUnprocessedTieBreaksByIdDescendingOnEqualCreatedAt() {
    UUID projectId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
    insertSignalWithId(second, projectId, "risk", "second", createdAt);
    insertSignalWithId(first, projectId, "risk", "first", createdAt);

    List<Signal> result = signalRepository.findUnprocessedByProjectId(projectId);

    assertThat(result).extracting(Signal::getId).containsExactly(second, first);
  }

  private void insertAction(UUID signalId) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_actions (id, workspace_id, signal_id, action_type,"
                + " processed_by_user_id) VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, UUID.randomUUID())
        .setParameter(3, signalId)
        .setParameter(4, "dismiss")
        .setParameter(5, UUID.randomUUID())
        .executeUpdate();
    entityManager.clear();
  }

  private UUID insertSignal(
      UUID projectId, String type, String title, Instant deletedAt, Instant createdAt) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signals (id, workspace_id, project_id, type, title, description,"
                + " created_at, deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)")
        .setParameter(1, id)
        .setParameter(2, UUID.randomUUID())
        .setParameter(3, projectId)
        .setParameter(4, type)
        .setParameter(5, title)
        .setParameter(6, "본문")
        .setParameter(7, createdAt)
        .setParameter(8, deletedAt)
        .executeUpdate();
    entityManager.clear();
    return id;
  }

  private void insertSignalWithId(
      UUID id, UUID projectId, String type, String title, Instant createdAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signals (id, workspace_id, project_id, type, title, description,"
                + " created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)")
        .setParameter(1, id)
        .setParameter(2, UUID.randomUUID())
        .setParameter(3, projectId)
        .setParameter(4, type)
        .setParameter(5, title)
        .setParameter(6, "본문")
        .setParameter(7, createdAt)
        .executeUpdate();
    entityManager.clear();
  }
}
