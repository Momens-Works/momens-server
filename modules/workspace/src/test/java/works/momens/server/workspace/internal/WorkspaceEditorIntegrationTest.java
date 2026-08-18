package works.momens.server.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.persistence.JpaAuditingConfig;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.workspace.UpdateWorkspaceCommand;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceEditor;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceSeedSql;

/**
 * 워크스페이스 수정이 레거시 {@code workspace.Update}와 동일하게 동작하는지 실제 PostgreSQL을 사용해 검증합니다.
 *
 * <p>빈 값을 기존 값 유지로 처리하는 규칙, slug 검증 시점, 상황별 에러 구분을 검증합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, WorkspaceEditorImpl.class})
class WorkspaceEditorIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private WorkspaceEditor workspaceEditor;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("이름, 설명, slug를 모두 변경한다")
  void updatesNameAndDescriptionAndSlug() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-all");
    entityManager.flush();
    entityManager.clear();

    WorkspaceDetail detail =
        workspaceEditor.update(
            new UpdateWorkspaceCommand(workspaceId, "새 이름", "새 설명", "editor-it-renamed"));

    assertThat(detail.name()).isEqualTo("새 이름");
    assertThat(detail.description()).isEqualTo("새 설명");
    assertThat(detail.slug()).isEqualTo("editor-it-renamed");
  }

  @Test
  @DisplayName("빈 문자열로 전달한 필드는 기존 값을 유지한다")
  void emptyFieldsKeepStoredValues() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-keep");
    entityManager
        .getEntityManager()
        .createNativeQuery("UPDATE workspaces SET description = ?1 WHERE id = ?2")
        .setParameter(1, "기존 설명")
        .setParameter(2, workspaceId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();

    WorkspaceDetail detail =
        workspaceEditor.update(new UpdateWorkspaceCommand(workspaceId, "", "", ""));

    assertThat(detail.name()).isEqualTo("모멘스");
    assertThat(detail.description()).isEqualTo("기존 설명");
    assertThat(detail.slug()).isEqualTo("editor-it-keep");
  }

  @Test
  @DisplayName("전달하지 않은 필드는 기존 값을 유지한다")
  void nullFieldsKeepStoredValues() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-null");
    entityManager.flush();
    entityManager.clear();

    WorkspaceDetail detail =
        workspaceEditor.update(new UpdateWorkspaceCommand(workspaceId, null, null, null));

    assertThat(detail.name()).isEqualTo("모멘스");
    assertThat(detail.slug()).isEqualTo("editor-it-null");
  }

  @Test
  @DisplayName("현재 값과 같은 slug는 중복으로 판정하지 않는다")
  void sameSlugIsNotTreatedAsDuplicate() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-same");
    entityManager.flush();
    entityManager.clear();

    WorkspaceDetail detail =
        workspaceEditor.update(
            new UpdateWorkspaceCommand(workspaceId, null, null, " editor-it-same "));

    assertThat(detail.slug()).isEqualTo("editor-it-same");
  }

  @Test
  @DisplayName("형식에 맞지 않는 slug는 WORKSPACE_INVALID_SLUG를 던진다")
  void rejectsInvalidSlug() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-invalid");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(
            () ->
                workspaceEditor.update(
                    new UpdateWorkspaceCommand(workspaceId, null, null, "Invalid-Slug")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVALID_SLUG);
  }

  @Test
  @DisplayName("예약어로 지정된 slug는 WORKSPACE_RESERVED_SLUG를 던진다")
  void rejectsReservedSlug() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-reserved");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(
            () ->
                workspaceEditor.update(
                    new UpdateWorkspaceCommand(workspaceId, null, null, "settings")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_RESERVED_SLUG);
  }

  @Test
  @DisplayName("다른 워크스페이스가 사용 중인 slug는 WORKSPACE_SLUG_ALREADY_EXISTS를 던진다")
  void rejectsSlugTakenByAnotherWorkspace() {
    UUID workspaceId = WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-mine");
    WorkspaceSeedSql.insertWorkspace(entityManager, "editor-it-theirs");
    entityManager.flush();
    entityManager.clear();

    assertThatThrownBy(
            () ->
                workspaceEditor.update(
                    new UpdateWorkspaceCommand(workspaceId, null, null, "editor-it-theirs")))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_SLUG_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("존재하지 않는 워크스페이스는 WORKSPACE_NOT_FOUND를 던진다")
  void throwsWorkspaceNotFoundWhenMissing() {
    assertThatThrownBy(
            () ->
                workspaceEditor.update(
                    new UpdateWorkspaceCommand(UUID.randomUUID(), "새 이름", null, null)))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
  }
}
