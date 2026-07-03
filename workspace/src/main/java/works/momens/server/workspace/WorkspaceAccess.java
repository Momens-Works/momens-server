package works.momens.server.workspace;

import java.util.List;
import java.util.UUID;

/**
 * workspace 모듈의 멤버십 조회 public API.
 *
 * <p>{@code project}/{@code task} 같은 다른 모듈이 workspace 내부 repository를 직접 참조하지 않고도 접근 권한을 확인할 수 있도록
 * 합니다 (docs/rules/code-conventions.md 보호 API). 이 API는 workspaceId를 받아 멤버십만 확인하고 조회하며, project나
 * task가 어느 workspace에 속하는지는 이 API의 책임이 아닙니다. 멤버십 저장소는 모듈 {@code internal}에 숨깁니다.
 */
public interface WorkspaceAccess {

  /** userId가 workspaceId의 멤버인지 확인합니다. 권한이 없을 때 반환할 에러는 호출하는 쪽에서 결정합니다. */
  boolean isMember(UUID workspaceId, UUID userId);

  /** workspaceId에 속한 멤버십을 모두 조회합니다. 프로젝트 멤버 목록 조회에 사용합니다(MOM-61). */
  List<WorkspaceMembership> listMemberships(UUID workspaceId);

  /**
   * userId가 가진 workspace 멤버십을 role과 함께 모두 조회합니다. 접근 가능한 project 목록 조회(MOM-59)와 모바일 bootstrap의 role
   * 매핑(MOM-60)에 사용합니다.
   */
  List<UserWorkspaceMembership> listUserMemberships(UUID userId);
}
