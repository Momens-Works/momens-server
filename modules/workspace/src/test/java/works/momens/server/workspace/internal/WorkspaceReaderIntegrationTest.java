package works.momens.server.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.WorkspaceSeedSql;

/**
 * {@link WorkspaceReader} public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers)에서 정렬(생성 시각 내림차순), 멤버십 필터, {@code description} null 동작을
 * 고정합니다(docs/design/legacy-product-api-migration/slice-workspace-read.md 4.3).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceReaderImpl.class})
class WorkspaceReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceReader workspaceReader;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("멤버인 워크스페이스만 생성 시각 내림차순으로 조회한다")
  void listByMemberUserIdReturnsOnlyMemberWorkspacesSortedByCreatedAtDesc() {
    UUID user = WorkspaceSeedSql.insertUser(entityManager, "reader-it-user@momens.works");
    UUID outsider = WorkspaceSeedSql.insertUser(entityManager, "reader-it-outsider@momens.works");
    UUID first = WorkspaceSeedSql.insertWorkspace(entityManager, "reader-it-first");
    UUID second = WorkspaceSeedSql.insertWorkspace(entityManager, "reader-it-second");
    UUID others = WorkspaceSeedSql.insertWorkspace(entityManager, "reader-it-others");
    addMember(first, user, "owner");
    addMember(second, user, "member");
    addMember(others, outsider, "owner");
    // 같은 트랜잭션 안의 NOW() 기본값은 같은 시각을 반환할 수 있어(postgres 트랜잭션 스냅샷), 정렬 검증을 위해
    // created_at을 명시적으로 다른 값으로 고정한다.
    Instant now = Instant.now();
    updateCreatedAt(first, now.minusSeconds(60));
    updateCreatedAt(second, now);
    updateCreatedAt(others, now.minusSeconds(30));
    entityManager.flush();
    entityManager.clear();

    List<WorkspaceDetail> result = workspaceReader.listByMemberUserId(user);

    assertThat(result).extracting(WorkspaceDetail::id).containsExactly(second, first);
  }

  @Test
  @DisplayName("멤버십이 없으면 빈 목록을 반환한다")
  void listByMemberUserIdIsEmptyWhenUserHasNoMembership() {
    UUID user = WorkspaceSeedSql.insertUser(entityManager, "reader-it-lonely@momens.works");

    assertThat(workspaceReader.listByMemberUserId(user)).isEmpty();
  }

  @Test
  @DisplayName("description이 설정돼 있으면 그대로 조회한다")
  void findByIdReturnsDescriptionWhenSet() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "reader-it-desc");
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE workspaces SET description = ?1 WHERE id = ?2")
        .setParameter(1, "제품팀 워크스페이스")
        .setParameter(2, workspaceId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();

    Optional<WorkspaceDetail> result = workspaceReader.findById(workspaceId);

    assertThat(result).isPresent();
    assertThat(result.get().description()).isEqualTo("제품팀 워크스페이스");
  }

  @Test
  @DisplayName("description이 없으면 null로 조회한다")
  void findByIdReturnsNullDescriptionWhenNotSet() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "reader-it-no-desc");
    entityManager.flush();
    entityManager.clear();

    Optional<WorkspaceDetail> result = workspaceReader.findById(workspaceId);

    assertThat(result).isPresent();
    assertThat(result.get().description()).isNull();
  }

  @Test
  @DisplayName("워크스페이스가 없으면 빈 Optional을 반환한다")
  void findByIdIsEmptyWhenWorkspaceDoesNotExist() {
    assertThat(workspaceReader.findById(UUID.randomUUID())).isEmpty();
  }

  private void updateCreatedAt(UUID workspaceId, Instant createdAt) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE workspaces SET created_at = ?1 WHERE id = ?2")
        .setParameter(1, createdAt)
        .setParameter(2, workspaceId)
        .executeUpdate();
  }

  private void addMember(UUID workspaceId, UUID userId, String role) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO workspace_members (workspace_id, user_id, role) VALUES (?1, ?2, ?3)")
        .setParameter(1, workspaceId)
        .setParameter(2, userId)
        .setParameter(3, role)
        .executeUpdate();
  }
}
