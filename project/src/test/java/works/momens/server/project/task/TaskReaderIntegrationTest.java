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
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskReader;

/**
 * task 조회 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 보드 조회(상태 필터, 소프트 삭제 제외, 생성 시각 내림차순 정렬, roles 결합)와 상세 조회(저장
 * 필드, checklist 순서, 빈 값)를 확인합니다. workspaces/users/projects는 다른 모듈 소유 테이블이라 FK 대상 행만 네이티브 SQL로 만듭니다.
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

  @Test
  void findDetailReturnsStoredFieldsAndChecklistInPositionOrder() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "gyuil@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "detail");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);
    UUID assigneeId = ProjectSeedSql.insertUser(entityManager, "jsshin8128@momens.works");

    UUID taskId =
        saveTask(workspaceId, projectId, "1차 와이어프레임", "todo", "urgent", Set.of("qa", "pm"));
    setDetailColumns(taskId, "이번 범위의 화면 흐름을 정리한다", assigneeId);
    // position 역순으로 넣어 조회 순서가 삽입 순서가 아니라 position 기준임을 확인한다.
    insertChecklistItem(taskId, "두 번째 완료기준", true, 1);
    insertChecklistItem(taskId, "첫 번째 완료기준", false, 0);

    TaskDetail detail = taskReader.findDetail(taskId).orElseThrow();

    assertThat(detail.projectId()).isEqualTo(projectId);
    assertThat(detail.workspaceId()).isEqualTo(workspaceId);
    assertThat(detail.title()).isEqualTo("1차 와이어프레임");
    assertThat(detail.status()).isEqualTo("todo");
    assertThat(detail.priority()).isEqualTo("urgent");
    assertThat(detail.roles()).containsExactly("pm", "qa");
    assertThat(detail.assigneeId()).isEqualTo(assigneeId);
    assertThat(detail.description()).isEqualTo("이번 범위의 화면 흐름을 정리한다");
    assertThat(detail.checklistItems())
        .extracting(TaskDetail.ChecklistItem::title)
        .containsExactly("첫 번째 완료기준", "두 번째 완료기준");
    assertThat(detail.checklistItems())
        .extracting(TaskDetail.ChecklistItem::completed)
        .containsExactly(false, true);
  }

  @Test
  void findDetailReturnsEmptyValuesWhenOptionalFieldsAreMissing() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "empty-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "empty");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);
    UUID taskId = saveTask(workspaceId, projectId, "빈 상세", "todo", "medium", Set.of("pm"));

    TaskDetail detail = taskReader.findDetail(taskId).orElseThrow();

    assertThat(detail.assigneeId()).isNull();
    assertThat(detail.description()).isNull();
    assertThat(detail.checklistItems()).isEmpty();
  }

  @Test
  void findDetailReturnsEmptyForSoftDeletedOrUnknownTask() {
    UUID ownerId = ProjectSeedSql.insertUser(entityManager, "deleted-owner@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "deleted");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);
    UUID deleted = saveTask(workspaceId, projectId, "삭제됨", "todo", "medium", Set.of());
    softDelete(deleted);

    assertThat(taskReader.findDetail(deleted)).isEmpty();
    assertThat(taskReader.findDetail(UUID.randomUUID())).isEmpty();
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

  private void setDetailColumns(UUID taskId, String description, UUID assigneeId) {
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE tasks SET description = ?1, assignee_id = ?2 WHERE id = ?3")
        .setParameter(1, description)
        .setParameter(2, assigneeId)
        .setParameter(3, taskId)
        .executeUpdate();
    entityManager.clear();
  }

  private void insertChecklistItem(UUID taskId, String title, boolean completed, int position) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO task_checklist_items (id, task_id, title, completed, position)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, taskId)
        .setParameter(3, title)
        .setParameter(4, completed)
        .setParameter(5, position)
        .executeUpdate();
    entityManager.clear();
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
