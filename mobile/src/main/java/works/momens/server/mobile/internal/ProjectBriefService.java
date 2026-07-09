package works.momens.server.mobile.internal;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 모바일 브리프 표면의 조합 서비스. project(스냅샷)와 workspace(멤버십) public API를 조합하고 도메인 정책을 소유하지 않습니다.
 *
 * <p>스냅샷이 workspace id를 들고 있으므로(태스크 상세와 같은 해석) 별도 workspace 해석 조회 없이 존재 판정 뒤 멤버십 검사로 바로 넘어갑니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectBriefService {

  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;

  @Transactional(readOnly = true)
  public ProjectSnapshot getBrief(UUID projectId, UUID userId) {
    ProjectSnapshot snapshot =
        projectReader
            .findSnapshot(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    if (!workspaceAccess.isMember(snapshot.workspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    return snapshot;
  }
}
