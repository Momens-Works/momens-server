package works.momens.server.signal.query;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
import works.momens.server.signal.SignalDetail;
import works.momens.server.signal.SignalDetailService;
import works.momens.server.signal.SignalErrorCode;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal 상세 조회 서비스.
 *
 * <p>Signal을 로드해 workspace 멤버십으로 접근을 검사하고(없으면 SIGNAL_NOT_FOUND(404), 멤버 아니면 AUTH_FORBIDDEN(403)),
 * project 이름과 근거를 조립합니다. 근거는 signal_evidence의 sort_order 순으로 source_ref_id를 얻어 source 모듈로 상세를
 * hydrate하고(ADR-0008 read 경계), 원본이 없는 근거는 건너뜁니다. summary는 snippet이 없으면 text로 폴백합니다.
 */
@Service
@RequiredArgsConstructor
class SignalDetailServiceImpl implements SignalDetailService {

  private final SignalRepository signalRepository;
  private final SignalEvidenceRepository signalEvidenceRepository;
  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final SourceRefReader sourceRefReader;

  @Override
  @Transactional(readOnly = true)
  public SignalDetail getDetail(UUID signalId, UUID userId) {
    Signal signal =
        signalRepository
            .findByIdAndDeletedAtIsNull(signalId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        SignalErrorCode.SIGNAL_NOT_FOUND,
                        Map.of("signal_id", signalId.toString())));
    if (!workspaceAccess.isMember(signal.getWorkspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("signal_id", signalId.toString()));
    }
    String projectName =
        projectReader.findSnapshot(signal.getProjectId()).map(ProjectSnapshot::name).orElse(null);
    return new SignalDetail(
        signal.getId(),
        signal.getProjectId(),
        projectName,
        signal.getType(),
        signal.getTitle(),
        signal.getDescription(),
        signal.getImpact(),
        signal.getMinsuSuggestion(),
        hydrateEvidence(signal.getWorkspaceId(), signal.getId()));
  }

  private List<SignalDetail.Evidence> hydrateEvidence(UUID workspaceId, UUID signalId) {
    List<SignalEvidence> links =
        signalEvidenceRepository.findBySignalIdOrderBySortOrderAscSourceRefIdAsc(signalId);
    Map<UUID, SourceRefView> refs =
        sourceRefReader
            .findByIds(workspaceId, links.stream().map(SignalEvidence::getSourceRefId).toList())
            .stream()
            .collect(Collectors.toMap(SourceRefView::id, Function.identity()));
    return links.stream()
        .map(link -> refs.get(link.getSourceRefId()))
        .filter(Objects::nonNull)
        .map(SignalDetailServiceImpl::toEvidence)
        .toList();
  }

  private static SignalDetail.Evidence toEvidence(SourceRefView ref) {
    String summary = ref.snippet() != null ? ref.snippet() : ref.text();
    return new SignalDetail.Evidence(
        ref.id(), ref.sourceType(), ref.title(), ref.sourceCreatedAt(), summary, ref.sourceUrl());
  }
}
