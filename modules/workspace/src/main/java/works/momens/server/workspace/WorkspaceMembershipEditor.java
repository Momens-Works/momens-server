package works.momens.server.workspace;

/**
 * workspace 멤버십 변경 public API.
 *
 * <p>멤버십을 변경할 수 있는지에 관한 도메인 규칙은 이 모듈이 소유합니다. 요청자의 역할이 충분한지는 호출하는 쪽에서 먼저 확인합니다.
 */
public interface WorkspaceMembershipEditor {

  /**
   * 멤버의 역할을 변경합니다.
   *
   * <p>대상이 멤버가 아니면 {@code WORKSPACE_MEMBER_NOT_FOUND}, 대상이 owner이면 {@code
   * WORKSPACE_OWNER_PROTECTED}를 던집니다. 요청자가 owner이더라도 다른 owner의 역할은 변경할 수 없습니다.
   */
  void changeRole(ChangeMembershipRoleCommand command);

  /**
   * 멤버를 워크스페이스에서 제거합니다.
   *
   * <p>요청자와 대상이 같으면 {@code WORKSPACE_SELF_REMOVAL_NOT_ALLOWED}를 던집니다. 자기 자신인지 먼저 확인한 뒤 대상 멤버를
   * 조회하므로, 존재하지 않는 사용자 ID에 요청자 자신의 ID를 전달해도 자기 제거 요청으로 판정합니다. 레거시와 동일한 확인 순서입니다.
   */
  void remove(RemoveMembershipCommand command);
}
