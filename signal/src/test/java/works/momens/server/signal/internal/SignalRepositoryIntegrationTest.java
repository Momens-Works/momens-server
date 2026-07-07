package works.momens.server.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
 * <p>실제 PostgreSQL(Testcontainers)에서 소프트 삭제 제외 id 조회와 컬럼 매핑을 확인합니다. signals는 worker가 쓰는 읽기 전용
 * 테이블이므로 fixture는 네이티브 SQL로 삽입합니다. 프로젝트 목록의 미처리 필터는 {@code signal_actions}에 의존하므로 후속 PR에서 검증합니다.
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
}
