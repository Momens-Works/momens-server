package works.momens.server.signal.query;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.signal.SignalDigestReader;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * 시그널 요약 문단 조회 서비스. 프로젝트가 속한 workspace를 해석하고 요청자의 멤버십을 검사한 뒤 문단을 반환합니다. 검사 방식은 {@code
 * SignalListServiceImpl}과 같습니다.
 */
@Service
@RequiredArgsConstructor
class SignalDigestReaderImpl implements SignalDigestReader {

  private final SignalDigestRepository signalDigestRepository;
  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findByCreatedRange(
      UUID projectId, UUID userId, Instant createdFrom, Instant createdToExclusive) {
    requireMember(projectId, userId);
    return signalDigestRepository
        .findByProjectIdAndCreatedRange(projectId, createdFrom, createdToExclusive, Limit.of(1))
        .stream()
        .findFirst()
        .map(SignalDigest::getSummary);
  }

  private void requireMember(UUID projectId, UUID userId) {
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
  }
}
