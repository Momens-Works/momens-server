package works.momens.server.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.common.test.AbstractPostgresIntegrationTest;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.signal.SignalErrorCode;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal 상세 조립 검증. 접근 검사와 source 근거 hydrate(각각 mock), 근거 정렬·summary 폴백·상대 시각 라벨·원본 누락 제외를 실제
 * PostgreSQL(Testcontainers) + 고정 Clock으로 확인합니다. Clock은 빈으로 노출하지 않으므로 서비스를 package-private 생성자로 직접
 * 조립합니다(실 repository는 @DataJpaTest에서 주입).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SignalDetailServiceTest extends AbstractPostgresIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-07-07T00:00:00Z");
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();

  @Autowired private SignalRepository signalRepository;
  @Autowired private SignalEvidenceRepository signalEvidenceRepository;
  @Autowired private TestEntityManager entityManager;

  private final ProjectReader projectReader = mock(ProjectReader.class);
  private final WorkspaceAccess workspaceAccess = mock(WorkspaceAccess.class);
  private final SourceRefReader sourceRefReader = mock(SourceRefReader.class);
  private SignalDetailService signalDetailService;

  @BeforeEach
  void setUp() {
    signalDetailService =
        new SignalDetailService(
            signalRepository,
            signalEvidenceRepository,
            projectReader,
            workspaceAccess,
            sourceRefReader,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("Signal이 없으면 SIGNAL_NOT_FOUND를 던진다")
  void throwsSignalNotFoundWhenMissing() {
    assertThatThrownBy(() -> signalDetailService.get(UUID.randomUUID(), CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(SignalErrorCode.SIGNAL_NOT_FOUND);
  }

  @Test
  @DisplayName("workspace 멤버가 아니면 AUTH_FORBIDDEN을 던진다")
  void throwsForbiddenWhenNotMember() {
    UUID signalId = insertSignal();
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> signalDetailService.get(signalId, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  @DisplayName("근거를 sort_order 순으로 조립하고 summary 폴백·상대 라벨을 채우며 원본 없는 근거는 제외한다")
  void assemblesEvidenceInSortOrderWithFallbackAndSkipsMissing() {
    UUID signalId = insertSignal();
    UUID ref0 = UUID.randomUUID();
    UUID ref1 = UUID.randomUUID();
    UUID missing = UUID.randomUUID();
    insertEvidence(signalId, ref1, 1);
    insertEvidence(signalId, ref0, 0);
    insertEvidence(signalId, missing, 2);

    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(projectReader.findSnapshot(PROJECT_ID))
        .thenReturn(
            Optional.of(new ProjectSnapshot(PROJECT_ID, WORKSPACE_ID, "Q2", null, 0, null)));
    // snippet이 없는 ref0은 text로 폴백, occurred_at 없는 ref0은 라벨 null. missing은 반환하지 않아 제외된다.
    when(sourceRefReader.findByIds(any(), any()))
        .thenReturn(
            List.of(
                new SourceRefView(ref0, "slack", "스레드", null, "본문0", "https://s/0", null),
                new SourceRefView(
                    ref1,
                    "figma",
                    "화면",
                    "요약1",
                    "본문1",
                    "https://f/1",
                    Instant.parse("2026-07-06T00:00:00Z"))));

    SignalDetail detail = signalDetailService.get(signalId, CALLER_ID);

    assertThat(detail.projectName()).isEqualTo("Q2");
    assertThat(detail.evidence()).extracting(SignalDetail.Evidence::id).containsExactly(ref0, ref1);
    assertThat(detail.evidence().get(0).summary()).isEqualTo("본문0");
    assertThat(detail.evidence().get(0).relativeTimeLabel()).isNull();
    assertThat(detail.evidence().get(1).summary()).isEqualTo("요약1");
    assertThat(detail.evidence().get(1).relativeTimeLabel()).isEqualTo("1일 전");
  }

  @Test
  @DisplayName("sort_order가 같으면 source_ref_id 오름차순으로 순서를 고정한다")
  void tieBreaksBySourceRefIdWhenSortOrderEqual() {
    UUID signalId = insertSignal();
    UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
    insertEvidence(signalId, high, 0);
    insertEvidence(signalId, low, 0);

    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    when(projectReader.findSnapshot(PROJECT_ID))
        .thenReturn(
            Optional.of(new ProjectSnapshot(PROJECT_ID, WORKSPACE_ID, "Q2", null, 0, null)));
    when(sourceRefReader.findByIds(any(), any()))
        .thenReturn(
            List.of(
                new SourceRefView(high, "slack", "높음", "요약", "본문", "https://s/h", null),
                new SourceRefView(low, "figma", "낮음", "요약", "본문", "https://s/l", null)));

    SignalDetail detail = signalDetailService.get(signalId, CALLER_ID);

    assertThat(detail.evidence()).extracting(SignalDetail.Evidence::id).containsExactly(low, high);
  }

  private UUID insertSignal() {
    UUID id = UUID.randomUUID();
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signals (id, workspace_id, project_id, type, title, description)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6)")
        .setParameter(1, id)
        .setParameter(2, WORKSPACE_ID)
        .setParameter(3, PROJECT_ID)
        .setParameter(4, "risk")
        .setParameter(5, "제목")
        .setParameter(6, "본문")
        .executeUpdate();
    entityManager.clear();
    return id;
  }

  private void insertEvidence(UUID signalId, UUID sourceRefId, int sortOrder) {
    entityManager
        .getEntityManager()
        .createNativeQuery(
            "INSERT INTO signal_evidence (workspace_id, signal_id, source_ref_id, sort_order)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, WORKSPACE_ID)
        .setParameter(2, signalId)
        .setParameter(3, sourceRefId)
        .setParameter(4, sortOrder)
        .executeUpdate();
    entityManager.clear();
  }
}
