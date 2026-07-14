package works.momens.server.project.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectSeedSql;
import works.momens.server.project.TaskDetail;
import works.momens.server.project.TaskEditor;
import works.momens.server.project.TaskReader;
import works.momens.server.project.UpdateTaskCommand;
import works.momens.server.project.UpdateTaskCommand.ChecklistItemEdit;

/**
 * task 수정 public API 검증.
 *
 * <p>실제 PostgreSQL(Testcontainers) 환경에서 본문 수정, 담당자 비우기, 완료기준 전체 교체(완료 상태 배치 갱신, 순서, 빠진 항목 삭제, 없는 id
 * 거부), 즉시 토글, 없는 대상 처리까지 확인합니다. workspaces/users/projects는 다른 모듈 소유 테이블이라 FK 대상 행만 네이티브 SQL로 만듭니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, TaskEditorImpl.class, TaskReaderImpl.class})
class TaskEditorIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TaskEditor taskEditor;
  @Autowired private TaskReader taskReader;
  @Autowired private TaskRepository taskRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  void updateReplacesEditableFields() {
    Fixture fixture = newTask();
    UUID assigneeId = ProjectSeedSql.insertUser(entityManager, "assignee@momens.works");

    TaskDetail updated =
        taskEditor.update(
            command(
                fixture.taskId(),
                "제목 수정",
                "backend",
                assigneeId,
                "high",
                "in_progress",
                "수정한 목적",
                List.of()));

    assertThat(updated.title()).isEqualTo("제목 수정");
    assertThat(updated.role()).isEqualTo("backend");
    assertThat(updated.priority()).isEqualTo("high");
    assertThat(updated.status()).isEqualTo("in_progress");
    assertThat(updated.description()).isEqualTo("수정한 목적");
    assertThat(updated.assigneeId()).isEqualTo(assigneeId);
  }

  @Test
  void updateClearsAssigneeWhenAssigneeIdIsNull() {
    Fixture fixture = newTask();
    UUID assigneeId = ProjectSeedSql.insertUser(entityManager, "clear@momens.works");
    taskEditor.update(
        command(fixture.taskId(), "제목", "pm", assigneeId, "medium", "todo", null, List.of()));

    TaskDetail cleared =
        taskEditor.update(
            command(fixture.taskId(), "제목", "pm", null, "medium", "todo", null, List.of()));

    assertThat(cleared.assigneeId()).isNull();
  }

  @Test
  void updateReplacesChecklistWithCompletedAndOrder() {
    Fixture fixture = newTask();
    TaskDetail seeded =
        taskEditor.update(
            command(
                fixture.taskId(),
                "제목",
                "pm",
                null,
                "medium",
                "todo",
                null,
                List.of(
                    new ChecklistItemEdit(null, "A", false),
                    new ChecklistItemEdit(null, "B", false),
                    new ChecklistItemEdit(null, "C", false))));
    UUID idA = seeded.checklistItems().get(0).id();
    UUID idB = seeded.checklistItems().get(1).id();

    // A는 그대로 두고, B는 제목과 완료 상태를 함께 바꾸고, C는 목록에서 빼고, D를 완료 상태로 새로 추가한다.
    taskEditor.update(
        command(
            fixture.taskId(),
            "제목",
            "pm",
            null,
            "medium",
            "todo",
            null,
            List.of(
                new ChecklistItemEdit(idA, "A", false),
                new ChecklistItemEdit(idB, "B 수정", true),
                new ChecklistItemEdit(null, "D", true))));
    entityManager.flush();
    entityManager.clear();

    TaskDetail reloaded = taskReader.findDetail(fixture.taskId()).orElseThrow();
    assertThat(reloaded.checklistItems())
        .extracting(TaskDetail.ChecklistItem::title)
        .containsExactly("A", "B 수정", "D");
    // 완료 상태는 배치가 보낸 값을 따른다. 기존 항목 B와 새로 추가한 D 모두 완료로 저장된다.
    assertThat(reloaded.checklistItems())
        .extracting(TaskDetail.ChecklistItem::completed)
        .containsExactly(false, true, true);
    assertThat(reloaded.checklistItems().get(0).id()).isEqualTo(idA);
    assertThat(reloaded.checklistItems().get(1).id()).isEqualTo(idB);
  }

  @Test
  void updateRejectsUnknownChecklistItemId() {
    Fixture fixture = newTask();

    assertThatThrownBy(
            () ->
                taskEditor.update(
                    command(
                        fixture.taskId(),
                        "제목",
                        "pm",
                        null,
                        "medium",
                        "todo",
                        null,
                        List.of(new ChecklistItemEdit(UUID.randomUUID(), "없는 항목", false)))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND);
  }

  @Test
  void updateRejectsDuplicateChecklistItemId() {
    Fixture fixture = newTask();
    TaskDetail seeded =
        taskEditor.update(
            command(
                fixture.taskId(),
                "제목",
                "pm",
                null,
                "medium",
                "todo",
                null,
                List.of(new ChecklistItemEdit(null, "A", false))));
    UUID idA = seeded.checklistItems().get(0).id();

    assertThatThrownBy(
            () ->
                taskEditor.update(
                    command(
                        fixture.taskId(),
                        "제목",
                        "pm",
                        null,
                        "medium",
                        "todo",
                        null,
                        List.of(
                            new ChecklistItemEdit(idA, "A", false),
                            new ChecklistItemEdit(idA, "A 중복", true)))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
  }

  @Test
  void toggleChecklistItemChangesCompleted() {
    Fixture fixture = newTask();
    TaskDetail seeded =
        taskEditor.update(
            command(
                fixture.taskId(),
                "제목",
                "pm",
                null,
                "medium",
                "todo",
                null,
                List.of(new ChecklistItemEdit(null, "완료기준", false))));
    UUID itemId = seeded.checklistItems().get(0).id();

    TaskDetail toggled = taskEditor.toggleChecklistItem(fixture.taskId(), itemId, true);

    assertThat(toggled.checklistItems().get(0).completed()).isTrue();
  }

  @Test
  void toggleChecklistItemRejectsUnknownItem() {
    Fixture fixture = newTask();

    assertThatThrownBy(
            () -> taskEditor.toggleChecklistItem(fixture.taskId(), UUID.randomUUID(), true))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_CHECKLIST_ITEM_NOT_FOUND);
  }

  @Test
  void updateRejectsUnknownTask() {
    assertThatThrownBy(
            () ->
                taskEditor.update(
                    command(
                        UUID.randomUUID(), "제목", "pm", null, "medium", "todo", null, List.of())))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ProjectErrorCode.TASK_NOT_FOUND);
  }

  @Test
  void workspaceIdOfReturnsWorkspaceAndEmptyForSoftDeleted() {
    Fixture fixture = newTask();

    assertThat(taskReader.workspaceIdOf(fixture.taskId())).contains(fixture.workspaceId());

    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE tasks SET deleted_at = NOW() WHERE id = ?1")
        .setParameter(1, fixture.taskId())
        .executeUpdate();
    entityManager.clear();

    assertThat(taskReader.workspaceIdOf(fixture.taskId())).isEmpty();
    assertThat(taskReader.workspaceIdOf(UUID.randomUUID())).isEmpty();
  }

  private Fixture newTask() {
    UUID ownerId =
        ProjectSeedSql.insertUser(entityManager, "owner-" + UUID.randomUUID() + "@momens.works");
    UUID workspaceId = ProjectSeedSql.insertWorkspace(entityManager, "edit");
    UUID projectId = ProjectSeedSql.insertProject(entityManager, workspaceId, ownerId);
    UUID taskId =
        taskRepository
            .saveAndFlush(
                Task.builder()
                    .workspaceId(workspaceId)
                    .projectId(projectId)
                    .title("초기 제목")
                    .status("todo")
                    .priority("medium")
                    .role("pm")
                    .build())
            .getId();
    return new Fixture(workspaceId, taskId);
  }

  private static UpdateTaskCommand command(
      UUID taskId,
      String title,
      String role,
      UUID assigneeId,
      String priority,
      String status,
      String purpose,
      List<ChecklistItemEdit> checklistItems) {
    return new UpdateTaskCommand(
        taskId, title, role, assigneeId, priority, status, purpose, checklistItems);
  }

  private record Fixture(UUID workspaceId, UUID taskId) {}
}
