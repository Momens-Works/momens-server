package works.momens.server.web.workspace;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.workspace.UpdateWorkspaceCommand;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceEditor;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceReader;
import works.momens.server.workspace.WorkspaceRole;
import works.momens.server.workspace.WorkspaceRoleReader;
import works.momens.server.workspace.WorkspaceSlugAvailability;
import works.momens.server.workspace.WorkspaceSlugReader;

/**
 * 워크스페이스 조회 조합 서비스. workspace public API 2개(WorkspaceReader, WorkspaceAccess)만 조합하고 도메인 정책을 소유하지
 * 않습니다.
 *
 * <p>{@link WorkspaceReader}는 Optional만 반환하므로, 없을 때 던질 에러 선택은 이 서비스가 합니다(project 모듈의 ProjectReader와
 * 같은 방식). 목록 조회는 멤버십 조인이 곧 필터라 별도 권한 검사를 하지 않습니다.
 */
@Service
@RequiredArgsConstructor
class WorkspaceService {

  private final WorkspaceReader workspaceReader;
  private final WorkspaceAccess workspaceAccess;
  private final WorkspaceSlugReader workspaceSlugReader;
  private final WorkspaceRoleReader workspaceRoleReader;
  private final WorkspaceEditor workspaceEditor;

  @Transactional(readOnly = true)
  public List<WorkspaceDetail> list(UUID userId) {
    return workspaceReader.listByMemberUserId(userId);
  }

  @Transactional(readOnly = true)
  public WorkspaceSlugAvailability slugAvailability(String rawSlug) {
    return workspaceSlugReader.availabilityOf(rawSlug);
  }

  @Transactional(readOnly = true)
  public WorkspaceDetail get(UUID workspaceId, UUID userId) {
    WorkspaceDetail detail =
        workspaceReader
            .findById(workspaceId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        WorkspaceErrorCode.WORKSPACE_NOT_FOUND,
                        Map.of("workspace_id", workspaceId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("workspace_id", workspaceId.toString()));
    }
    return detail;
  }

  /**
   * 워크스페이스를 수정합니다.
   *
   * <p>워크스페이스의 존재 여부를 먼저 확인한 뒤 역할을 검증합니다. 순서를 바꾸면 존재하지 않는 워크스페이스에도 404가 아닌 403을 반환하게 됩니다.
   */
  @Transactional
  public WorkspaceDetail update(
      UUID workspaceId, UUID userId, String name, String description, String slug) {
    requireWorkspaceExists(workspaceId);
    requireRoleAtLeast(workspaceId, userId, WorkspaceRole.ADMIN);
    return workspaceEditor.update(new UpdateWorkspaceCommand(workspaceId, name, description, slug));
  }

  /** 워크스페이스가 없으면 WORKSPACE_NOT_FOUND를 던집니다. */
  private void requireWorkspaceExists(UUID workspaceId) {
    if (workspaceReader.findById(workspaceId).isEmpty()) {
      throw new BusinessException(
          WorkspaceErrorCode.WORKSPACE_NOT_FOUND, Map.of("workspace_id", workspaceId.toString()));
    }
  }

  /**
   * 사용자의 역할이 요구 수준을 충족하지 않으면 AUTH_FORBIDDEN을 던집니다.
   *
   * <p>멤버가 아닌 경우와 멤버이지만 권한이 부족한 경우를 같은 에러 코드로 처리합니다. 필요한 역할은 details의 {@code required_role}로 전달하므로
   * 역할이 추가되더라도 에러 코드는 유지할 수 있습니다.
   */
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
