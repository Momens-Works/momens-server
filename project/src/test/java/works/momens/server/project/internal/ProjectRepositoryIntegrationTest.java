package works.momens.server.project.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
 * project 영속성 기반 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 Flyway 마이그레이션과 엔티티 매핑이 맞는지, 감사 필드와 레거시 기본값(status active)이
 * 채워지는지 확인합니다. workspaces와 users는 다른 모듈 소유 테이블이라 네이티브 SQL로 FK 대상 행만 만듭니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ProjectRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ProjectRepository projectRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void savesAndReadsProjectWithMappedColumns() {
    UUID ownerId = insertUser("owner@momens.works");
    UUID workspaceId = insertWorkspace("momens-project");

    Project saved =
        projectRepository.saveAndFlush(
            Project.builder()
                .workspaceId(workspaceId)
                .name("Q2 Activation Readiness")
                .description("모바일 MVP 준비")
                .ownerId(ownerId)
                .targetDate(LocalDate.of(2026, 6, 30))
                .progress(64)
                .summary("요약")
                .build());
    entityManager.clear();

    Project found = projectRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getWorkspaceId()).isEqualTo(workspaceId);
    assertThat(found.getName()).isEqualTo("Q2 Activation Readiness");
    assertThat(found.getDescription()).isEqualTo("모바일 MVP 준비");
    assertThat(found.getStatus()).isEqualTo("active");
    assertThat(found.getOwnerId()).isEqualTo(ownerId);
    assertThat(found.getTargetDate()).isEqualTo(LocalDate.of(2026, 6, 30));
    assertThat(found.getProgress()).isEqualTo(64);
    assertThat(found.getSummary()).isEqualTo("요약");
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(found.getDeletedAt()).isNull();
  }

  /** projects.owner_id FK를 만족하도록 사용자 데이터를 네이티브 SQL로 삽입합니다. */
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

  /** projects.workspace_id FK를 만족하도록 워크스페이스 데이터를 네이티브 SQL로 삽입합니다. */
  private UUID insertWorkspace(String slug) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery("INSERT INTO workspaces (id, name, slug) VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, "모멘스")
        .setParameter(3, slug)
        .executeUpdate();
    return id;
  }
}
