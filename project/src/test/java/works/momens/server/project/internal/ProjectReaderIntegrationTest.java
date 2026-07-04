package works.momens.server.project.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;

/**
 * project 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 소프트 삭제 제외, workspace 스코프, 생성 시각 내림차순 정렬을 확인합니다. 접근 범위(멤버십)는
 * 호출하는 쪽이 확정해서 workspace id 목록으로 넘기는 계약이므로, 여기서는 id 목록을 직접 만들어 project 쪽 규칙만 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ProjectReaderImpl.class})
class ProjectReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private ProjectReader projectReader;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void listByWorkspaceIdsReturnsLiveProjectsOfGivenWorkspacesNewestFirst() {
    UUID ownerId = insertUser("reader-owner@momens.works");
    UUID firstWorkspace = insertWorkspace("reader-first");
    UUID secondWorkspace = insertWorkspace("reader-second");
    UUID otherWorkspace = insertWorkspace("reader-others");

    UUID older = saveProject(firstWorkspace, ownerId, "older").getId();
    UUID newer = saveProject(secondWorkspace, ownerId, "newer").getId();
    UUID deleted = saveProject(firstWorkspace, ownerId, "deleted").getId();
    softDelete(deleted);
    saveProject(otherWorkspace, ownerId, "not-mine");

    List<ProjectSnapshot> snapshots =
        projectReader.listByWorkspaceIds(List.of(firstWorkspace, secondWorkspace));

    assertThat(snapshots).extracting(ProjectSnapshot::id).containsExactly(newer, older);
  }

  @Test
  void listByWorkspaceIdsIsEmptyForEmptyWorkspaceIds() {
    assertThat(projectReader.listByWorkspaceIds(List.of())).isEmpty();
  }

  @Test
  void workspaceIdOfResolvesLiveProjectOnly() {
    UUID ownerId = insertUser("resolve-owner@momens.works");
    UUID workspaceId = insertWorkspace("resolve");
    UUID live = saveProject(workspaceId, ownerId, "live").getId();
    UUID deleted = saveProject(workspaceId, ownerId, "gone").getId();
    softDelete(deleted);

    assertThat(projectReader.workspaceIdOf(live)).contains(workspaceId);
    assertThat(projectReader.workspaceIdOf(deleted)).isEmpty();
    assertThat(projectReader.workspaceIdOf(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findSnapshotMapsMobileReadFields() {
    UUID ownerId = insertUser("snapshot-owner@momens.works");
    UUID workspaceId = insertWorkspace("snapshot");
    UUID projectId =
        projectRepository
            .saveAndFlush(
                Project.builder()
                    .workspaceId(workspaceId)
                    .name("Q2 Activation Readiness")
                    .ownerId(ownerId)
                    .targetDate(LocalDate.of(2026, 6, 30))
                    .progress(64)
                    .summary("요약")
                    .build())
            .getId();

    assertThat(projectReader.findSnapshot(projectId))
        .contains(
            new ProjectSnapshot(
                projectId,
                workspaceId,
                "Q2 Activation Readiness",
                LocalDate.of(2026, 6, 30),
                64,
                "요약"));
  }

  private Project saveProject(UUID workspaceId, UUID ownerId, String name) {
    return projectRepository.saveAndFlush(
        Project.builder().workspaceId(workspaceId).name(name).ownerId(ownerId).build());
  }

  /** 소프트 삭제 API는 아직 없으므로(read 기반 슬라이스) 삭제 상태를 네이티브 SQL로 만듭니다. */
  private void softDelete(UUID projectId) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE projects SET deleted_at = NOW() WHERE id = ?1")
        .setParameter(1, projectId)
        .executeUpdate();
    entityManager.clear();
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
