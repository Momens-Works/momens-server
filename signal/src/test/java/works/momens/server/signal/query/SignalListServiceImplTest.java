package works.momens.server.signal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.SignalSummary;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal 목록 서비스 검증.
 *
 * <p>접근 검사(project·workspace public API는 mock)와 미처리 필터·매핑(실제 PostgreSQL Testcontainers)을 함께 확인합니다.
 * Signal 엔티티는 {@code @Immutable}이라 fixture는 네이티브 SQL로 삽입합니다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SignalListServiceImpl.class)
class SignalListServiceImplTest extends AbstractPostgresIntegrationTest {

  @Autowired private SignalListService signalListService;
  @Autowired private TestEntityManager entityManager;
  @MockitoBean private ProjectReader projectReader;
  @MockitoBean private WorkspaceAccess workspaceAccess;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();

  @Test
  @DisplayName("project가 없으면 PROJECT_NOT_FOUND를 던진다")
  void throwsProjectNotFoundWhenProjectMissing() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> signalListService.listUnprocessed(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
  }

  @Test
  @DisplayName("workspace 멤버가 아니면 AUTH_FORBIDDEN을 던진다")
  void throwsForbiddenWhenCallerIsNotMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> signalListService.listUnprocessed(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  @DisplayName("멤버에게 미처리 Signal만 요약으로 반환한다")
  void returnsUnprocessedSummariesForMember() {
    when(projectReader.workspaceIdOf(PROJECT_ID)).thenReturn(Optional.of(WORKSPACE_ID));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    UUID unprocessed =
        insertSignal("risk", "이탈 가능성", "완료율에 영향", "점검 제안", Instant.parse("2026-07-01T00:00:00Z"));
    UUID processed =
        insertSignal("decision", "처리됨", null, null, Instant.parse("2026-07-02T00:00:00Z"));
    insertAction(processed);

    List<SignalSummary> signals = signalListService.listUnprocessed(PROJECT_ID, CALLER_ID);

    assertThat(signals)
        .containsExactly(
            new SignalSummary(unprocessed, PROJECT_ID, "risk", "이탈 가능성", "완료율에 영향", "점검 제안"));
  }

  private UUID insertSignal(
      String type, String title, String impact, String minsuSuggestion, Instant createdAt) {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signals (id, workspace_id, project_id, type, title, description, impact,"
                + " minsu_suggestion, created_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)")
        .setParameter(1, id)
        .setParameter(2, WORKSPACE_ID)
        .setParameter(3, PROJECT_ID)
        .setParameter(4, type)
        .setParameter(5, title)
        .setParameter(6, "본문")
        .setParameter(7, impact)
        .setParameter(8, minsuSuggestion)
        .setParameter(9, createdAt)
        .executeUpdate();
    entityManager.clear();
    return id;
  }

  private void insertAction(UUID signalId) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_actions (id, workspace_id, signal_id, action_type,"
                + " processed_by_user_id) VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, UUID.randomUUID())
        .setParameter(2, WORKSPACE_ID)
        .setParameter(3, signalId)
        .setParameter(4, "dismiss")
        .setParameter(5, UUID.randomUUID())
        .executeUpdate();
    entityManager.clear();
  }
}
