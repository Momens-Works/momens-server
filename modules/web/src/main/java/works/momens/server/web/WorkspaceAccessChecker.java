package works.momens.server.web;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;

/**
 * 워크스페이스 존재 여부와 요청자의 역할이 필요한 수준 이상인지 확인합니다.
 *
 * <p>같은 검증이 세 곳에서 필요해지면 공통 위치로 옮기기로 한 PR #159 리뷰의 합의에 따라, source 연결 조회가 세 번째 사용처가 된 시점에 이 클래스로
 * 통합했습니다.
 *
 * <p><strong>워크스페이스 존재 여부를 역할보다 먼저 확인합니다.</strong> 순서를 바꾸면 존재하지 않는 워크스페이스에도 404가 아닌 403을 반환하게 됩니다.
 */
@Component
@RequiredArgsConstructor
public class WorkspaceAccessChecker {

  private final WorkspaceReader workspaceReader;
  private final WorkspaceRoleReader workspaceRoleReader;

  public void requireWorkspaceExists(UUID workspaceId) {
    if (workspaceReader.findById(workspaceId).isEmpty()) {
      throw new BusinessException(
          WorkspaceErrorCode.WORKSPACE_NOT_FOUND, Map.of("workspace_id", workspaceId.toString()));
    }
  }

  public void requireRoleAtLeast(UUID workspaceId, UUID userId, WorkspaceRole required) {
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
