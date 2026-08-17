package works.momens.server.web.workspace;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.workspace.WorkspaceAccess;
import works.momens.server.workspace.WorkspaceDetail;
import works.momens.server.workspace.WorkspaceErrorCode;
import works.momens.server.workspace.WorkspaceReader;

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

  @Transactional(readOnly = true)
  public List<WorkspaceDetail> list(UUID userId) {
    return workspaceReader.listByMemberUserId(userId);
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
}
