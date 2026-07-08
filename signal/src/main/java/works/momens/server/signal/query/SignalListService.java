package works.momens.server.signal.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal 목록 조회 서비스. 프로젝트가 속한 workspace를 해석하고 요청자의 멤버십을 검사한 뒤, 아직 처리되지 않은 Signal만 반환한다.
 *
 * <p>Signal 처리 여부는 사용자별이 아니라 프로젝트 단위이므로(docs/design/mobile-mvp-server-requirements.md Signal 요구사항),
 * 멤버십 검사는 목록 조회와 별개로 단순 {@code isMember} 조회만으로 충분하다(목록처럼 멤버 스냅샷을 응답에 함께 반환하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class SignalListService {

  private final SignalRepository signalRepository;
  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;

  @Transactional(readOnly = true)
  public List<SignalSummary> listUnprocessed(UUID projectId, UUID userId) {
    UUID workspaceId =
        projectReader
            .workspaceIdOf(projectId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ProjectErrorCode.PROJECT_NOT_FOUND,
                        Map.of("project_id", projectId.toString())));
    if (!workspaceAccess.isMember(workspaceId, userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("project_id", projectId.toString()));
    }
    return signalRepository.findUnprocessedByProjectId(projectId).stream()
        .map(
            signal ->
                new SignalSummary(
                    signal.getId(),
                    signal.getProjectId(),
                    signal.getType(),
                    signal.getTitle(),
                    signal.getImpact(),
                    signal.getMinsuSuggestion()))
        .toList();
  }
}
