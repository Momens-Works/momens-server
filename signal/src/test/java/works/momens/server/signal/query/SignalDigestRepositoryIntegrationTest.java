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
import org.springframework.data.domain.Limit;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 시그널 요약 문단 읽기 모델 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers)에서 생성 시각 범위 필터(끝 배타), 워크스페이스·프로젝트 스코프, 소프트 삭제 제외, 최신 우선, 동률 시 id
 * 순서를 확인합니다. signal_digests는 민수가 쓰는 테이블이라 fixture는 네이티브 SQL로 삽입합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SignalDigestRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final Instant FROM = Instant.parse("2026-07-09T15:00:00Z");
  private static final Instant TO_EXCLUSIVE = Instant.parse("2026-07-10T15:00:00Z");
  private static final UUID WORKSPACE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

  @Autowired private SignalDigestRepository signalDigestRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("범위 밖 문단은 조회되지 않는다")
  void excludesDigestsOutsideTheRange() {
    UUID projectId = UUID.randomUUID();
    insertDigest(projectId, "어제 문단", FROM.minusSeconds(1));
    // 끝은 포함하지 않으므로 경계값 자체도 빠진다.
    insertDigest(projectId, "내일 문단", TO_EXCLUSIVE);
    insertDigest(projectId, "오늘 문단", FROM);

    assertThat(find(projectId)).extracting(SignalDigest::getSummary).containsExactly("오늘 문단");
  }

  @Test
  @DisplayName("다른 프로젝트 문단은 조회되지 않는다")
  void excludesOtherProjects() {
    UUID projectId = UUID.randomUUID();
    insertDigest(projectId, "내 문단", FROM);
    insertDigest(UUID.randomUUID(), "남의 문단", FROM);

    assertThat(find(projectId)).extracting(SignalDigest::getSummary).containsExactly("내 문단");
  }

  @Test
  @DisplayName("범위 안에 여러 건이면 가장 최근 문단이 이긴다")
  void latestDigestWinsWithinTheRange() {
    UUID projectId = UUID.randomUUID();
    insertDigest(projectId, "먼저 만든 문단", FROM);
    insertDigest(projectId, "다시 만든 문단", FROM.plusSeconds(3600));

    assertThat(find(projectId)).extracting(SignalDigest::getSummary).containsExactly("다시 만든 문단");
  }

  @Test
  @DisplayName("생성 시각이 같으면 id 내림차순으로 순서를 고정한다")
  void ordersByIdWhenCreatedAtTies() {
    UUID projectId = UUID.randomUUID();
    // 민수가 같은 시각에 두 건을 만들어도 조회 결과가 흔들리지 않아야 한다(signals 목록과 같은 기준).
    UUID larger = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
    UUID smaller = UUID.fromString("00000000-0000-4000-8000-000000000001");
    insertDigest(smaller, projectId, "작은 id", FROM);
    insertDigest(larger, projectId, "큰 id", FROM);

    assertThat(find(projectId)).extracting(SignalDigest::getSummary).containsExactly("큰 id");
  }

  @Test
  @DisplayName("다른 워크스페이스 문단은 조회되지 않는다")
  void excludesOtherWorkspaces() {
    UUID projectId = UUID.randomUUID();
    // 멤버십 검사와 별개로 쿼리 스코프가 교차 워크스페이스 노출을 막는지 확인한다(signals 조회와 같은 방어).
    insertDigest(UUID.randomUUID(), UUID.randomUUID(), projectId, "다른 워크스페이스 문단", FROM, null);

    assertThat(find(projectId)).isEmpty();
  }

  @Test
  @DisplayName("소프트 삭제된 문단은 없는 것으로 취급한다")
  void excludesSoftDeletedDigests() {
    UUID projectId = UUID.randomUUID();
    // 생산자가 문단을 철회하는 유일한 경로가 deleted_at이므로, 조회가 이를 존중해야 철회가 화면에 반영된다.
    insertDigest(
        UUID.randomUUID(), WORKSPACE_ID, projectId, "철회된 문단", FROM.plusSeconds(7200), FROM);
    insertDigest(projectId, "남아 있는 문단", FROM);

    assertThat(find(projectId)).extracting(SignalDigest::getSummary).containsExactly("남아 있는 문단");
  }

  @Test
  @DisplayName("문단이 없으면 빈 목록이다")
  void returnsEmptyWhenNoDigest() {
    assertThat(find(UUID.randomUUID())).isEmpty();
  }

  private List<SignalDigest> find(UUID projectId) {
    return signalDigestRepository.findByProjectIdAndCreatedRange(
        WORKSPACE_ID, projectId, FROM, TO_EXCLUSIVE, Limit.of(1));
  }

  private void insertDigest(UUID projectId, String summary, Instant createdAt) {
    insertDigest(UUID.randomUUID(), WORKSPACE_ID, projectId, summary, createdAt, null);
  }

  private void insertDigest(UUID id, UUID projectId, String summary, Instant createdAt) {
    insertDigest(id, WORKSPACE_ID, projectId, summary, createdAt, null);
  }

  private void insertDigest(
      UUID id,
      UUID workspaceId,
      UUID projectId,
      String summary,
      Instant createdAt,
      Instant deletedAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_digests (id, workspace_id, project_id, summary, created_at,"
                + " deleted_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
        .setParameter(1, id)
        .setParameter(2, workspaceId)
        .setParameter(3, projectId)
        .setParameter(4, summary)
        .setParameter(5, createdAt)
        .setParameter(6, deletedAt)
        .executeUpdate();
    entityManager.clear();
  }
}
