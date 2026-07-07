package works.momens.server.project.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.BoardTask;
import works.momens.server.project.ProjectSeedSql;
import works.momens.server.project.TaskReader;

/**
 * task 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 상태 필터, 소프트 삭제 제외, 정렬(생성 시각 내림차순), roles 결합을 확인합니다.
 * workspaces/users/projects는 다른 모듈 소유 테이블이라 FK 대상 행만 네이티브 SQL로 만듭니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, TaskReaderImpl.class})
class TaskReaderIntegrationTest extends AbstractPostgresIntegrationTest {

  private static final List<String> BOARD_STATUSES = List.of("todo", "in_progress", "done");

  @Autowired private TaskReader taskReader;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void listTasksByStatusReturnsOnlyGivenStatuses() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "board-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "board");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);

    saveTask(workspaceId, projectId, "백로그", "backlog", "medium", Set.of());
    saveTask(workspaceId, projectId, "투두", "todo", "medium", Set.of());
    saveTask(workspaceId, projectId, "진행중", "in_progress", "medium", Set.of());
    saveTask(workspaceId, projectId, "완료", "done", "medium", Set.of());
    saveTask(workspaceId, projectId, "취소", "cancelled", "medium", Set.of());

    List<BoardTask> board = taskReader.listTasksByStatus(projectId, BOARD_STATUSES);

    assertThat(board)
        .extracting(BoardTask::status)
        .containsExactlyInAnyOrder("todo", "in_progress", "done");
    assertThat(board).extracting(BoardTask::title).doesNotContain("백로그", "취소");
  }

  @Test
  void listTasksByStatusExcludesSoftDeletedAndOtherProjects() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "filter-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "filter");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);
    UUID otherProjectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);

    saveTask(workspaceId, projectId, "살아있음", "todo", "medium", Set.of());
    UUID deleted = saveTask(workspaceId, projectId, "삭제됨", "todo", "medium", Set.of());
    softDelete(deleted);
    saveTask(workspaceId, otherProjectId, "다른 프로젝트", "todo", "medium", Set.of());

    List<BoardTask> board = taskReader.listTasksByStatus(projectId, BOARD_STATUSES);

    assertThat(board).extracting(BoardTask::title).containsExactly("살아있음");
  }

  @Test
  void listTasksByStatusReturnsSortedRolesAndNewestFirst() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "order-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "order");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);

    UUID earlier = saveTask(workspaceId, projectId, "먼저", "todo", "high", Set.of("qa", "pm"));
    UUID later = saveTask(workspaceId, projectId, "나중", "todo", "high", Set.of("android"));
    // 두 태스크의 created_at이 같으면 id 보조 정렬로 순서가 흔들리므로, 생성 시각을 다르게 고정해 정렬을 결정적으로 만든다.
    setCreatedAt(earlier, "2026-07-06T00:00:00Z");
    setCreatedAt(later, "2026-07-06T00:00:01Z");

    List<BoardTask> board = taskReader.listTasksByStatus(projectId, BOARD_STATUSES);

    assertThat(board).extracting(BoardTask::title).containsExactly("나중", "먼저");
    assertThat(board.get(1).roles()).containsExactly("pm", "qa");
  }

  private UUID saveTask(
      UUID workspaceId,
      UUID projectId,
      String title,
      String status,
      String priority,
      Set<String> roles) {
    return taskRepository
        .saveAndFlush(
            Task.builder()
                .workspaceId(workspaceId)
                .projectId(projectId)
                .title(title)
                .status(status)
                .priority(priority)
                .roles(roles)
                .build())
        .getId();
  }

  private void softDelete(UUID taskId) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE tasks SET deleted_at = NOW() WHERE id = ?1")
        .setParameter(1, taskId)
        .executeUpdate();
    entityManager.clear();
  }

  private void setCreatedAt(UUID taskId, String isoInstant) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE tasks SET created_at = CAST(?1 AS timestamptz) WHERE id = ?2")
        .setParameter(1, isoInstant)
        .setParameter(2, taskId)
        .executeUpdate();
    entityManager.clear();
  }
}
