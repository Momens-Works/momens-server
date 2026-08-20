package works.momens.server.web.workspace;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.user.UserProfile;
import works.momens.server.user.UserService;
import works.momens.server.workspace.ChangeMembershipRoleCommand;
import works.momens.server.workspace.RemoveMembershipCommand;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceMembershipDetail;
import works.momens.server.workspace.WorkspaceMembershipEditor;
import works.momens.server.workspace.WorkspaceMembershipReader;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;

/**
 * 워크스페이스 멤버 조회와 변경을 조합하는 서비스. workspace와 user 모듈의 public API만 조합하며 도메인 정책은 소유하지 않습니다.
 *
 * <p>워크스페이스 존재 여부와 요청자의 역할은 이 서비스에서 확인하고, owner 보호와 자기 제거 금지 등 멤버십 도메인 규칙은 workspace 모듈에서 확인합니다.
 * 워크스페이스 존재 여부를 역할보다 먼저 확인하는 이유는 순서를 바꾸면 존재하지 않는 워크스페이스에도 404가 아닌 403을 반환하기 때문입니다.
 *
 * <p>{@code requireWorkspaceExists}와 {@code requireRoleAtLeast}는 {@link WorkspaceService}에도 같은 형태로
 * 존재합니다. 세 번째 중복이 발생하는 시점에 공통 위치로 옮기기로 했습니다(PR #159 리뷰).
 */
@Service
@RequiredArgsConstructor
class WorkspaceMemberService {

  private final WorkspaceReader workspaceReader;
  private final WorkspaceRoleReader workspaceRoleReader;
  private final WorkspaceMembershipReader workspaceMembershipReader;
  private final WorkspaceMembershipEditor workspaceMembershipEditor;
  private final UserService userService;

  /**
   * 워크스페이스의 멤버를 이름 오름차순으로 조회합니다. 이름이 같으면 사용자 ID를 기준으로 보조 정렬합니다.
   *
   * <p>멤버십은 한 번만 조회해 요청자의 멤버 여부를 확인하고 응답 목록을 만드는 데 함께 사용합니다. 별도로 조회하면 READ COMMITTED 격리 수준에서는 각 SQL
   * 문이 시작될 때마다 최신 커밋을 보므로, 권한 확인과 목록 조회 사이에 멤버십이 회수된 사용자에게 목록이 반환될 수 있습니다.
   *
   * <p>정렬 기준인 사용자 이름은 user 모듈에서 가져오므로 SQL에서 정렬하지 않고 두 모듈의 정보를 조합한 뒤 정렬합니다. workspace 모듈은 {@code
   * users} 테이블을 직접 조인하지 않습니다.
   */
  @Transactional(readOnly = true)
  public List<WorkspaceMemberView> list(UUID workspaceId, UUID userId) {
    requireWorkspaceExists(workspaceId);
    List<WorkspaceMembershipDetail> memberships =
        workspaceMembershipReader.listDetailsByWorkspaceId(workspaceId);
    boolean callerIsMember =
        memberships.stream().anyMatch(membership -> membership.userId().equals(userId));
    if (!callerIsMember) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", workspaceId.toString()));
    }
    Map<UUID, UserProfile> profiles =
        userService
            .getProfiles(memberships.stream().map(WorkspaceMembershipDetail::userId).toList())
            .stream()
            .collect(Collectors.toMap(UserProfile::id, profile -> profile));
    return memberships.stream()
        .filter(membership -> profiles.containsKey(membership.userId()))
        .map(
            membership -> {
              UserProfile profile = profiles.get(membership.userId());
              return new WorkspaceMemberView(
                  profile.id(),
                  profile.email(),
                  profile.name(),
                  membership.role(),
                  membership.createdAt(),
                  membership.updatedAt());
            })
        .sorted(
            Comparator.comparing(WorkspaceMemberView::name)
                .thenComparing(WorkspaceMemberView::userId))
        .toList();
  }

  /** 멤버의 역할을 변경합니다. 부여할 수 있는 역할인지는 enum이 판정하며, 판정 결과가 없으면 {@code WORKSPACE_INVALID_ROLE}을 던집니다. */
  @Transactional
  public void changeRole(UUID workspaceId, UUID userId, UUID targetUserId, String rawRole) {
    requireWorkspaceExists(workspaceId);
    requireRoleAtLeast(workspaceId, userId, WorkspaceRole.ADMIN);
    WorkspaceRole role =
        WorkspaceRole.assignableFrom(rawRole)
            .orElseThrow(
                () ->
                    new BusinessException(
                        WorkspaceErrorCode.WORKSPACE_INVALID_ROLE,
                        Map.of("role", String.valueOf(rawRole))));
    workspaceMembershipEditor.changeRole(
        new ChangeMembershipRoleCommand(workspaceId, targetUserId, role));
  }

  /** 멤버를 제거합니다. 요청자 ID를 그대로 전달해 workspace 모듈에서 자기 제거 요청인지 판정하도록 합니다. */
  @Transactional
  public void remove(UUID workspaceId, UUID userId, UUID targetUserId) {
    requireWorkspaceExists(workspaceId);
    requireRoleAtLeast(workspaceId, userId, WorkspaceRole.ADMIN);
    workspaceMembershipEditor.remove(
        new RemoveMembershipCommand(workspaceId, userId, targetUserId));
  }

  private void requireWorkspaceExists(UUID workspaceId) {
    if (workspaceReader.findById(workspaceId).isEmpty()) {
      throw new BusinessException(
          WorkspaceErrorCode.WORKSPACE_NOT_FOUND, Map.of("workspace_id", workspaceId.toString()));
    }
  }

  private void requireRoleAtLeast(UUID workspaceId, UUID userId, WorkspaceRole required) {
    boolean allowed =
        workspaceRoleReader
            .roleOf(workspaceId, userId)
            .filter(role -> role.isAtLeast(required))
            .isPresent();
    if (!allowed) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN,
          Map.of("workspace_id", workspaceId.toString(), "required_role", required.value()));
    }
  }
}
