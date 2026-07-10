package works.momens.server.mobile.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.signal.SignalListService;
import works.momens.server.signal.SignalSummary;
import works.momens.server.signal.SignalSummaryPage;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 브리프 조합 규칙 검증. 도메인 모듈 public API는 각자 통합 테스트에서 검증하므로 여기서는 모두 mock으로 두고 조합 규칙만 확인합니다. project 확인과
 * 멤버십 검사 순서, change를 제외한 개수 집계, 필터 키 검증, 페이지 기본값이 그 대상입니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectBriefServiceTest {

  @Mock private ProjectReader projectReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @Mock private SignalListService signalListService;
  @InjectMocks private ProjectBriefService projectBriefService;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final List<String> EXPOSED_TYPES = List.of("decision", "risk", "question");

  @Test
  void getBriefThrowsProjectNotFoundWhenProjectMissing() {
    when(projectReader.findSnapshot(PROJECT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectBriefService.getBrief(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ProjectErrorCode.PROJECT_NOT_FOUND);
  }

  @Test
  void getBriefThrowsForbiddenWhenCallerIsNotWorkspaceMember() {
    when(projectReader.findSnapshot(PROJECT_ID)).thenReturn(Optional.of(snapshot()));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(false);

    assertThatThrownBy(() -> projectBriefService.getBrief(PROJECT_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void getBriefCombinesSnapshotCountsAndFirstPage() {
    ProjectSnapshot snapshot = snapshot();
    UUID signalId = UUID.randomUUID();
    when(projectReader.findSnapshot(PROJECT_ID)).thenReturn(Optional.of(snapshot));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);
    // change는 노출하지 않으므로 all 개수에서 빠져야 한다.
    when(signalListService.countUnprocessedByType(PROJECT_ID, CALLER_ID))
        .thenReturn(Map.of("decision", 2L, "risk", 1L, "question", 2L, "change", 7L));
    when(signalListService.listUnprocessedPage(
            eq(PROJECT_ID), eq(CALLER_ID), eq(EXPOSED_TYPES), isNull(), eq(3)))
        .thenReturn(
            new SignalSummaryPage(
                List.of(
                    new SignalSummary(
                        signalId, PROJECT_ID, "decision", "소셜 로그인은 MVP 범위에서 제외", null, null)),
                "cursor-1"));

    MobileBrief brief = projectBriefService.getBrief(PROJECT_ID, CALLER_ID);

    assertThat(brief.project()).isEqualTo(snapshot);
    assertThat(brief.filters())
        .containsExactly(
            new MobileBrief.FilterCount(BriefSignalFilter.ALL, 5L),
            new MobileBrief.FilterCount(BriefSignalFilter.DECISIONS, 2L),
            new MobileBrief.FilterCount(BriefSignalFilter.RISKS, 1L),
            new MobileBrief.FilterCount(BriefSignalFilter.QUESTIONS, 2L));
    assertThat(brief.items())
        .containsExactly(new MobileBrief.SignalItem(signalId, "decision", "소셜 로그인은 MVP 범위에서 제외"));
    assertThat(brief.nextCursor()).isEqualTo("cursor-1");
  }

  @Test
  void getSignalSummaryPagePassesFilterTypesAndDefaultLimit() {
    UUID signalId = UUID.randomUUID();
    when(signalListService.listUnprocessedPage(
            eq(PROJECT_ID), eq(CALLER_ID), eq(List.of("decision")), eq("cursor-1"), eq(3)))
        .thenReturn(
            new SignalSummaryPage(
                List.of(new SignalSummary(signalId, PROJECT_ID, "decision", "결정", null, null)),
                null));

    MobileBriefSignalPage page =
        projectBriefService.getSignalSummaryPage(
            PROJECT_ID, CALLER_ID, "decisions", "cursor-1", null);

    assertThat(page.items())
        .containsExactly(new MobileBrief.SignalItem(signalId, "decision", "결정"));
    assertThat(page.nextCursor()).isNull();
  }

  @Test
  void getSignalSummaryPageThrowsValidationFailedForUnknownFilter() {
    // change(VOC)는 브리프 노출 필터가 아니므로 잘못된 값과 똑같이 거른다.
    assertThatThrownBy(
            () ->
                projectBriefService.getSignalSummaryPage(PROJECT_ID, CALLER_ID, "change", null, 3))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
  }

  @Test
  void getSignalSummaryPageThrowsValidationFailedForNegativeLimit() {
    assertThatThrownBy(
            () -> projectBriefService.getSignalSummaryPage(PROJECT_ID, CALLER_ID, "all", null, -1))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.COMMON_VALIDATION_FAILED);
  }

  private static ProjectSnapshot snapshot() {
    return new ProjectSnapshot(
        PROJECT_ID,
        WORKSPACE_ID,
        "Q2 Activation Readiness",
        LocalDate.of(2026, 6, 30),
        64,
        "목표일까지 Q2 Activation Readiness 범위의 회원 가입 MVP를 안정적으로 릴리즈한다.");
  }
}
