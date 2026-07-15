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
 * <p>실제 PostgreSQL(Testcontainers)에서 생성 시각 범위 필터(끝 배타), 프로젝트 스코프, 최신 우선, 동률 시 id 순서를 확인합니다.
 * signal_digests는 민수가 쓰는 테이블이라 fixture는 네이티브 SQL로 삽입합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SignalDigestRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final Instant FROM = Instant.parse("2026-07-09T15:00:00Z");
  private static final Instant TO_EXCLUSIVE = Instant.parse("2026-07-10T15:00:00Z");

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
  @DisplayName("문단이 없으면 빈 목록이다")
  void returnsEmptyWhenNoDigest() {
    assertThat(find(UUID.randomUUID())).isEmpty();
  }

  private List<SignalDigest> find(UUID projectId) {
    return signalDigestRepository.findByProjectIdAndCreatedRange(
        projectId, FROM, TO_EXCLUSIVE, Limit.of(1));
  }

  private void insertDigest(UUID projectId, String summary, Instant createdAt) {
    insertDigest(UUID.randomUUID(), projectId, summary, createdAt);
  }

  private void insertDigest(UUID id, UUID projectId, String summary, Instant createdAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_digests (id, project_id, summary, created_at)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, projectId)
        .setParameter(3, summary)
        .setParameter(4, createdAt)
        .executeUpdate();
    entityManager.clear();
  }
}
