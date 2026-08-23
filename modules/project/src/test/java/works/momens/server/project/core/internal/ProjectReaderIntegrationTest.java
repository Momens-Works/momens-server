package works.momens.server.project.core.internal;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectSeedSql;
import works.momens.server.project.core.ProjectReader;
import works.momens.server.project.core.ProjectSnapshot;
import works.momens.server.project.task.TaskReader;

/**
 * project 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 소프트 삭제 제외, workspace 스코프, 생성 시각 내림차순 정렬을 확인합니다. 접근 범위(멤버십)는
 * 호출하는 쪽이 확정해서 workspace id 목록으로 넘기는 계약이므로, 여기서는 id 목록을 직접 만들어 project 쪽 규칙만 검증합니다.
 *
 * <p>{@code TaskReader}는 mock을 사용합니다. 구현체가 task 하위 모듈의 package-private이라 이 슬라이스 테스트에서 구성할 수 없습니다(라벨
 * 발급 때문에 {@code TaskCreatorImplTest}와 같은 제약이 있습니다).
 *
 * <p>진행률 계산은 {@code ProjectReaderImplTest}에서 단위 테스트로 검증하고, 실제 태스크 조회까지 포함한 동작은 애플리케이션 통합 테스트에서
 * 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ProjectReaderImpl.class})
class ProjectReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @MockitoBean private TaskReader taskReader;

  @Autowired private ProjectReader projectReader;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void listByWorkspaceIdsReturnsLiveProjectsOfGivenWorkspacesNewestFirst() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "reader-owner@momens.works");
    UUID firstWorkspace = ProjectSeedSql.insertWorkspace(entityManager, "reader-first");
    UUID secondWorkspace = ProjectSeedSql.insertWorkspace(entityManager, "reader-second");
    UUID otherWorkspace = ProjectSeedSql.insertWorkspace(entityManager, "reader-others");

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
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "resolve-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "resolve");
    UUID live = saveProject(workspaceId, ownerId, "live").getId();
    UUID deleted = saveProject(workspaceId, ownerId, "gone").getId();
    softDelete(deleted);

    assertThat(projectReader.workspaceIdOf(live)).contains(workspaceId);
    assertThat(projectReader.workspaceIdOf(deleted)).isEmpty();
    assertThat(projectReader.workspaceIdOf(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findSnapshotMapsMobileReadFields() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "snapshot-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "snapshot");
    UUID projectId =
        projectRepository
            .saveAndFlush(
                Project.builder()
                    .workspaceId(workspaceId)
                    .name("Q2 Activation Readiness")
                    .ownerId(ownerId)
                    .targetDate(LocalDate.of(2026, 6, 30))
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
}
