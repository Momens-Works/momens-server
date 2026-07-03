package works.momens.server.project.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.workspace.UserWorkspaceMembership;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceMembership;

/**
 * project 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 소프트 삭제 제외, workspace 스코프, 생성 시각 내림차순 정렬을 확인합니다. 멤버십 조회는
 * workspace 모듈이 자기 통합 테스트에서 검증하므로, 여기서는 {@link WorkspaceAccess}를 스텁으로 두고 project 쪽 규칙만 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, ProjectReaderImpl.class})
class ProjectReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  @TestConfiguration
  static class StubWorkspaceAccessConfig {
    @Bean
    StubWorkspaceAccess workspaceAccess() {
      return new StubWorkspaceAccess();
    }
  }

  /** 멤버십 조회를 대신하는 스텁. 테스트가 grant로 넣은 workspace 멤버십만 돌려줍니다. */
  static class StubWorkspaceAccess implements WorkspaceAccess {
    private final Map<UUID, List<UserWorkspaceMembership>> membershipsByUser = new HashMap<>();

    void grant(UUID userId, UUID workspaceId) {
      membershipsByUser
          .computeIfAbsent(userId, ignored -> new ArrayList<>())
          .add(new UserWorkspaceMembership(workspaceId, "member"));
    }

    @Override
    public boolean isMember(UUID workspaceId, UUID userId) {
      return membershipsByUser.getOrDefault(userId, List.of()).stream()
          .anyMatch(membership -> membership.workspaceId().equals(workspaceId));
    }

    @Override
    public List<WorkspaceMembership> listMemberships(UUID workspaceId) {
      throw new UnsupportedOperationException("project 조회 테스트에서 사용하지 않습니다");
    }

    @Override
    public List<UserWorkspaceMembership> listUserMemberships(UUID userId) {
      return membershipsByUser.getOrDefault(userId, List.of());
    }
  }

  @Autowired private ProjectReader projectReader;
  @Autowired private ProjectRepository projectRepository;
  @Autowired private StubWorkspaceAccess workspaceAccess;
  @Autowired private TestEntityManager entityManager;

  @Test
  void listAccessibleReturnsLiveProjectsOfUserWorkspacesNewestFirst() {
    UUID ownerId = insertUser("reader-owner@momens.works");
    UUID firstWorkspace = insertWorkspace("reader-first");
    UUID secondWorkspace = insertWorkspace("reader-second");
    UUID otherWorkspace = insertWorkspace("reader-others");
    UUID userId = UUID.randomUUID();
    workspaceAccess.grant(userId, firstWorkspace);
    workspaceAccess.grant(userId, secondWorkspace);

    UUID older = saveProject(firstWorkspace, ownerId, "older").getId();
    UUID newer = saveProject(secondWorkspace, ownerId, "newer").getId();
    UUID deleted = saveProject(firstWorkspace, ownerId, "deleted").getId();
    softDelete(deleted);
    saveProject(otherWorkspace, ownerId, "not-mine");

    List<ProjectSnapshot> snapshots = projectReader.listAccessible(userId);

    assertThat(snapshots).extracting(ProjectSnapshot::id).containsExactly(newer, older);
  }

  @Test
  void listAccessibleIsEmptyForUserWithoutWorkspaces() {
    assertThat(projectReader.listAccessible(UUID.randomUUID())).isEmpty();
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
