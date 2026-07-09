package works.momens.server.mobile.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 브리프 조합 규칙 검증. 도메인 모듈 public API는 각자 통합 테스트에서 검증하므로 여기서는 모두 mock으로 두고 조합 규칙(존재 판정과 멤버십 검사 순서)만
 * 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class ProjectBriefServiceTest {

  @Mock private ProjectReader projectReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @InjectMocks private ProjectBriefService projectBriefService;

  private static final UUID PROJECT_ID = UUID.randomUUID();
  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();

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
  void getBriefReturnsProjectSnapshotForMember() {
    ProjectSnapshot snapshot = snapshot();
    when(projectReader.findSnapshot(PROJECT_ID)).thenReturn(Optional.of(snapshot));
    when(workspaceAccess.isMember(WORKSPACE_ID, CALLER_ID)).thenReturn(true);

    assertThat(projectBriefService.getBrief(PROJECT_ID, CALLER_ID)).isEqualTo(snapshot);
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
