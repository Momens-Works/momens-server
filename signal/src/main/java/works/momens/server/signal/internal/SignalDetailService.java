package works.momens.server.signal.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.common.api.CommonErrorCode;
import works.momens.server.project.ProjectReader;
import works.momens.server.project.ProjectSnapshot;
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
public class SignalDetailService {

  private final SignalRepository signalRepository;
  private final SignalEvidenceRepository signalEvidenceRepository;
  private final ProjectReader projectReader;
  private final WorkspaceAccess workspaceAccess;
  private final SourceRefReader sourceRefReader;
  private final Clock clock;

  // 상대 시각 라벨용 Clock은 시스템 UTC 고정입니다. 별도 Clock 빈으로 두면 auth의 Clock 빈과 타입이 충돌하므로
  // 빈으로 노출하지 않고, 테스트만 아래 package-private 생성자로 고정 Clock을 주입합니다.
  @Autowired
  public SignalDetailService(
      SignalRepository signalRepository,
      SignalEvidenceRepository signalEvidenceRepository,
      ProjectReader projectReader,
      WorkspaceAccess workspaceAccess,
      SourceRefReader sourceRefReader) {
    this(
        signalRepository,
        signalEvidenceRepository,
        projectReader,
        workspaceAccess,
        sourceRefReader,
        Clock.systemUTC());
  }

  SignalDetailService(
      SignalRepository signalRepository,
      SignalEvidenceRepository signalEvidenceRepository,
      ProjectReader projectReader,
      WorkspaceAccess workspaceAccess,
      SourceRefReader sourceRefReader,
      Clock clock) {
    this.signalRepository = signalRepository;
    this.signalEvidenceRepository = signalEvidenceRepository;
    this.projectReader = projectReader;
    this.workspaceAccess = workspaceAccess;
    this.sourceRefReader = sourceRefReader;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SignalDetail get(UUID signalId, UUID userId) {
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
    Instant now = clock.instant();
    return links.stream()
        .map(link -> refs.get(link.getSourceRefId()))
        .filter(Objects::nonNull)
        .map(ref -> toEvidence(ref, now))
        .toList();
  }

  private static SignalDetail.Evidence toEvidence(SourceRefView ref, Instant now) {
    String summary = ref.snippet() != null ? ref.snippet() : ref.text();
    return new SignalDetail.Evidence(
        ref.id(),
        ref.sourceType(),
        ref.title(),
        ref.sourceCreatedAt(),
        RelativeTimeLabel.of(now, ref.sourceCreatedAt()),
        summary,
        ref.sourceUrl());
  }
}
