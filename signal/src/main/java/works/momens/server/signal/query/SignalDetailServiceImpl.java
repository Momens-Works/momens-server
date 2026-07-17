package works.momens.server.signal.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.minsu.Minsu;
import works.momens.server.minsu.MinsuSignalContext;
import works.momens.server.signal.SignalDetail;
import works.momens.server.signal.SignalDetailService;
import works.momens.server.signal.SignalErrorCode;
import works.momens.server.source.SourceRefReader;
import works.momens.server.source.SourceRefView;
import works.momens.server.workspace.WorkspaceAccess;

/**
 * Signal 상세 조회 서비스.
 *
 * <p>미처리 Signal을 로드해(처리됐거나 소프트 삭제됐으면 SIGNAL_NOT_FOUND(404)) workspace 멤버십으로 접근을 검사하고(멤버 아니면
 * AUTH_FORBIDDEN(403)) 근거를 조립합니다. 처리된 Signal을 다시 보는 inbox는 MVP 이후라 미처리만 대상으로 합니다
 * (docs/spec/mobile-api.md 시그널 상세 절). 근거는 signal_evidence의 sort_order 순으로 읽어
 * target·change·impact(대상· 변화·영향, ADR-0011)를 담고, source_ref_id로 source 모듈을 hydrate해(ADR-0008 read
 * 경계) source·occurred_at· source_url을 채웁니다. 원본이 없는 근거는 건너뜁니다.
 */
@Service
@RequiredArgsConstructor
class SignalDetailServiceImpl implements SignalDetailService {

  private final SignalRepository signalRepository;
  private final SignalEvidenceRepository signalEvidenceRepository;
  private final WorkspaceAccess workspaceAccess;
  private final SourceRefReader sourceRefReader;
  private final Minsu minsu;

  @Override
  @Transactional(readOnly = true)
  public SignalDetail getDetail(UUID signalId, UUID userId) {
    Signal signal =
        signalRepository
            .findUnprocessedById(signalId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        SignalErrorCode.SIGNAL_NOT_FOUND,
                        Map.of("signal_id", signalId.toString())));
    if (!workspaceAccess.isMember(signal.getWorkspaceId(), userId)) {
      throw new BusinessException(
          CommonErrorCode.AUTH_FORBIDDEN, Map.of("signal_id", signalId.toString()));
    }
    List<SignalDetail.Evidence> evidence = hydrateEvidence(signal.getWorkspaceId(), signal.getId());
    return new SignalDetail(
        signal.getId(),
        signal.getType(),
        signal.getTitle(),
        signal.getImpact(),
        resolveSuggestion(signal, evidence),
        evidence);
  }

  /**
   * minsu_suggestion은 민수 산출물이라(ADR-0011) backing에 이미 있으면 그대로 쓰고, 없으면 민수가 Signal 제목·근거로 생성한다
   * (MOM-0692). 민수는 하드 의존이라 생성 실패 시 상세 조회도 실패한다.
   */
  private String resolveSuggestion(Signal signal, List<SignalDetail.Evidence> evidence) {
    String stored = signal.getMinsuSuggestion();
    if (stored != null && !stored.isBlank()) {
      return stored;
    }
    return minsu.suggest(
        new MinsuSignalContext(
            signal.getTitle(),
            evidence.stream()
                .map(e -> new MinsuSignalContext.Evidence(e.target(), e.change(), e.impact()))
                .toList()));
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
        .filter(link -> refs.containsKey(link.getSourceRefId()))
        .map(link -> toEvidence(link, refs.get(link.getSourceRefId())))
        .toList();
  }

  private static SignalDetail.Evidence toEvidence(SignalEvidence link, SourceRefView ref) {
    return new SignalDetail.Evidence(
        ref.id(),
        ref.sourceType(),
        ref.sourceCreatedAt(),
        link.getTarget(),
        link.getChange(),
        link.getImpact(),
        ref.sourceUrl());
  }
}
