package works.momens.server.web.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.web.WorkspaceAccessChecker;
import works.momens.server.workspace.ChangeMembershipRoleCommand;
import works.momens.server.workspace.RemoveMembershipCommand;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceMembershipDetail;
import works.momens.server.workspace.WorkspaceMembershipEditor;
import works.momens.server.workspace.WorkspaceMembershipReader;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;

/**
 * 워크스페이스 멤버 조합 서비스의 동작을 검증합니다.
 *
 * <p>workspace와 user 모듈의 public API는 각 모듈의 통합 테스트에서 검증하므로 이 테스트에서는 모두 mock으로 대체합니다. 이 서비스가 담당하는 정렬
 * 기준, 정보 조합 결과, 에러 판정 순서, workspace 모듈에 전달하는 요청 값을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceMemberServiceTest {

  @Mock private WorkspaceReader workspaceReader;
  @Mock private WorkspaceRoleReader workspaceRoleReader;
  @Mock private WorkspaceMembershipReader workspaceMembershipReader;
  @Mock private WorkspaceMembershipEditor workspaceMembershipEditor;
  @Mock private UserService userService;
  private WorkspaceMemberService workspaceMemberService;

  @BeforeEach
  void setUp() {
    workspaceMemberService =
        new WorkspaceMemberService(
            new WorkspaceAccessChecker(workspaceReader, workspaceRoleReader),
            workspaceMembershipReader,
            workspaceMembershipEditor,
            userService);
  }

  private static final UUID WORKSPACE_ID = UUID.randomUUID();
  private static final UUID CALLER_ID = UUID.randomUUID();
  private static final UUID TARGET_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

  @Test
  @DisplayName("이름 오름차순으로 정렬하고 이름이 같으면 사용자 ID로 정렬한다")
  void listSortsByNameThenUserId() {
    UUID sameNameFirst = UUID.fromString("00000000-0000-4000-8000-000000000001");
    UUID sameNameSecond = UUID.fromString("00000000-0000-4000-8000-000000000002");
    workspaceExists();
    when(workspaceMembershipReader.listDetailsByWorkspaceId(WORKSPACE_ID))
        .thenReturn(
            List.of(
                membership(sameNameSecond, "member"),
                membership(CALLER_ID, "owner"),
                membership(sameNameFirst, "admin")));
    when(userService.getProfiles(any()))
        .thenReturn(
            List.of(
                profile(sameNameSecond, "same-name-2@momens.works", "같은 이름"),
                profile(CALLER_ID, "later-name@momens.works", "뒤에 오는 이름"),
                profile(sameNameFirst, "same-name-1@momens.works", "같은 이름")));

    List<WorkspaceMemberView> members = workspaceMemberService.list(WORKSPACE_ID, CALLER_ID);

    assertThat(members)
        .extracting(WorkspaceMemberView::userId)
        .containsExactly(sameNameFirst, sameNameSecond, CALLER_ID);
  }

  @Test
  @DisplayName("사용자 정보와 멤버십 정보를 조합해 반환한다")
  void listMapsProfileAndMembershipFields() {
    workspaceExists();
    when(workspaceMembershipReader.listDetailsByWorkspaceId(WORKSPACE_ID))
        .thenReturn(List.of(membership(CALLER_ID, "admin")));
    when(userService.getProfiles(any()))
        .thenReturn(List.of(profile(CALLER_ID, "jinsu@momens.works", "신진수")));

    WorkspaceMemberView member = workspaceMemberService.list(WORKSPACE_ID, CALLER_ID).getFirst();

    assertThat(member.userId()).isEqualTo(CALLER_ID);
    assertThat(member.email()).isEqualTo("jinsu@momens.works");
    assertThat(member.name()).isEqualTo("신진수");
    assertThat(member.role()).isEqualTo("admin");
    assertThat(member.createdAt()).isEqualTo(NOW);
    assertThat(member.updatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("요청자가 멤버가 아니면 사용자 정보를 조회하지 않고 거부한다")
  void listRejectsCallerWhoIsNotMember() {
    workspaceExists();
    when(workspaceMembershipReader.listDetailsByWorkspaceId(WORKSPACE_ID))
        .thenReturn(List.of(membership(TARGET_ID, "owner")));

    assertThatThrownBy(() -> workspaceMemberService.list(WORKSPACE_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("워크스페이스가 없으면 멤버십을 조회하기 전에 거부한다")
  void listRejectsMissingWorkspaceBeforeReadingMemberships() {
    when(workspaceReader.findById(WORKSPACE_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> workspaceMemberService.list(WORKSPACE_ID, CALLER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
    verifyNoInteractions(workspaceMembershipReader);
  }

  @Test
  @DisplayName("변환한 역할을 workspace 모듈에 그대로 전달한다")
  void changeRolePassesResolvedRoleToEditor() {
    workspaceExists();
    callerHasRole(WorkspaceRole.ADMIN);

    workspaceMemberService.changeRole(WORKSPACE_ID, CALLER_ID, TARGET_ID, "admin");

    ArgumentCaptor<ChangeMembershipRoleCommand> captor =
        ArgumentCaptor.forClass(ChangeMembershipRoleCommand.class);
    verify(workspaceMembershipEditor).changeRole(captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new ChangeMembershipRoleCommand(WORKSPACE_ID, TARGET_ID, WorkspaceRole.ADMIN));
  }

  @Test
  @DisplayName("owner를 부여하려는 요청은 거부한다")
  void changeRoleRejectsOwnerValue() {
    workspaceExists();
    callerHasRole(WorkspaceRole.OWNER);

    assertThatThrownBy(
            () -> workspaceMemberService.changeRole(WORKSPACE_ID, CALLER_ID, TARGET_ID, "owner"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVALID_ROLE);
    verifyNoInteractions(workspaceMembershipEditor);
  }

  @Test
  @DisplayName("정의되지 않은 역할 값은 거부한다")
  void changeRoleRejectsUndefinedValue() {
    workspaceExists();
    callerHasRole(WorkspaceRole.ADMIN);

    assertThatThrownBy(
            () ->
                workspaceMemberService.changeRole(WORKSPACE_ID, CALLER_ID, TARGET_ID, "superadmin"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(WorkspaceErrorCode.WORKSPACE_INVALID_ROLE);
  }

  @Test
  @DisplayName("요청자의 역할이 member이면 역할을 변경하지 않는다")
  void changeRoleRejectsMemberRole() {
    workspaceExists();
    callerHasRole(WorkspaceRole.MEMBER);

    assertThatThrownBy(
            () -> workspaceMemberService.changeRole(WORKSPACE_ID, CALLER_ID, TARGET_ID, "admin"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
    verifyNoInteractions(workspaceMembershipEditor);
  }

  @Test
  @DisplayName("요청자 ID를 workspace 모듈에 그대로 전달한다")
  void removePassesCallerAsRequester() {
    workspaceExists();
    callerHasRole(WorkspaceRole.ADMIN);

    workspaceMemberService.remove(WORKSPACE_ID, CALLER_ID, TARGET_ID);

    verify(workspaceMembershipEditor)
        .remove(new RemoveMembershipCommand(WORKSPACE_ID, CALLER_ID, TARGET_ID));
  }

  @Test
  @DisplayName("요청자의 역할이 member이면 멤버를 제거하지 않는다")
  void removeRejectsMemberRole() {
    workspaceExists();
    callerHasRole(WorkspaceRole.MEMBER);

    assertThatThrownBy(() -> workspaceMemberService.remove(WORKSPACE_ID, CALLER_ID, TARGET_ID))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(CommonErrorCode.AUTH_FORBIDDEN);
    verifyNoInteractions(workspaceMembershipEditor);
  }

  private void workspaceExists() {
    when(workspaceReader.findById(WORKSPACE_ID))
        .thenReturn(
            Optional.of(new WorkspaceDetail(WORKSPACE_ID, "모먼스", "momens", null, NOW, NOW)));
  }

  private void callerHasRole(WorkspaceRole role) {
    when(workspaceRoleReader.roleOf(WORKSPACE_ID, CALLER_ID)).thenReturn(Optional.of(role));
  }

  private static WorkspaceMembershipDetail membership(UUID userId, String role) {
    return new WorkspaceMembershipDetail(userId, role, NOW, NOW);
  }

  private static UserProfile profile(UUID id, String email, String name) {
    return new UserProfile(id, email, name, null, null, NOW, NOW);
  }
}
