package works.momens.server.workspace;

import java.util.Optional;
import java.util.UUID;

/**
 * workspace 멤버십 역할을 조회하는 public API.
 *
 * <p>{@link WorkspaceAccess#isMember}가 멤버 여부만 반환하는 것과 달리, 이 API는 사용자의 역할을 함께 반환합니다. admin 이상의 권한이
 * 필요한 endpoint에서 권한을 판정할 때 사용합니다.
 */
public interface WorkspaceRoleReader {

  /** workspaceId에 속한 userId의 역할을 조회합니다. 멤버가 아니면 빈 Optional을 반환합니다. */
  Optional<WorkspaceRole> roleOf(UUID workspaceId, UUID userId);
}
