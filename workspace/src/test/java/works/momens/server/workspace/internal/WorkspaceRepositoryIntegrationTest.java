package works.momens.server.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;

/**
 * 영속성 베이스 패턴 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) + Flyway 마이그레이션 + {@code ddl-auto: validate} 위에서, 단일 UUID PK
 * 워크스페이스와 복합 PK 멤버십을 저장하고 감사 필드가 자동으로 채워지는지 확인합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class WorkspaceRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceRepository workspaceRepository;
  @Autowired private WorkspaceMemberRepository workspaceMemberRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void savesWorkspaceWithGeneratedIdAndAuditFields() {
    Workspace saved =
        workspaceRepository.save(Workspace.builder().name("모멘스").slug("momens").build());

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getId().version()).as("UUID v4 PK").isEqualTo(4);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void savesWorkspaceMemberWithCompositeKeyAndAuditFields() {
    Workspace workspace =
        workspaceRepository.save(Workspace.builder().name("모멘스").slug("momens-ops").build());
    UUID userId = insertUser("owner@momens.works");

    workspaceMemberRepository.save(
        WorkspaceMember.builder()
            .workspaceId(workspace.getId())
            .userId(userId)
            .role("owner")
            .build());
    entityManager.flush();
    entityManager.clear();

    WorkspaceMember found =
        workspaceMemberRepository
            .findById(new WorkspaceMemberId(workspace.getId(), userId))
            .orElseThrow();

    assertThat(found.getRole()).isEqualTo("owner");
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(workspaceMemberRepository.findByWorkspaceId(workspace.getId())).hasSize(1);
  }

  /** workspace_members.user_id FK를 만족시킬 사용자 행을 네이티브 SQL로 삽입합니다. */
  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery("INSERT INTO users (id, email, name) VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, email)
        .setParameter(3, "이름")
        .executeUpdate();
    return id;
  }
}
