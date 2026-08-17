package works.momens.server.web.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceReader;

/**
 * 워크스페이스 조회 조합 규칙 검증. workspace public API는 각자 통합 테스트에서 검증하므로 여기서는 모두 mock으로 두고 에러 선택 규칙(404 vs 403
 * 우선순위)만 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

  @Mock private WorkspaceReader workspaceReader;
  @Mock private WorkspaceAccess workspaceAccess;
  @InjectMocks private WorkspaceService workspaceService;

  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  void listReturnsReaderResultAsIs() {
    WorkspaceDetail detail = detail();
    when(workspaceReader.listByMemberUserId(USER_ID)).thenReturn(List.of(detail));

    assertThat(workspaceService.list(USER_ID)).containsExactly(detail);
  }

  @Test
  void getThrowsWorkspaceNotFoundWhenMissing() {
    when(workspaceReader.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> workspaceService.get(WORKSPACE_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
  }

  @Test
  void getThrowsForbiddenWhenCallerIsNotMember() {
    when(workspaceReader.findById(WORKSPACE_ID)).thenReturn(Optional.of(detail()));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(false);

    assertThatThrownBy(() -> workspaceService.get(WORKSPACE_ID, USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
  }

  @Test
  void getReturnsDetailWhenCallerIsMember() {
    WorkspaceDetail detail = detail();
    when(workspaceReader.findById(WORKSPACE_ID)).thenReturn(Optional.of(detail));
    when(workspaceAccess.isMember(WORKSPACE_ID, USER_ID)).thenReturn(true);

    assertThat(workspaceService.get(WORKSPACE_ID, USER_ID)).isEqualTo(detail);
  }

  private static WorkspaceDetail detail() {
    return new WorkspaceDetail(
        WORKSPACE_ID, "Momens", "momens", "제품팀 워크스페이스", Instant.now(), Instant.now());
  }
}
