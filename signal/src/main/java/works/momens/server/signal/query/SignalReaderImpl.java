package works.momens.server.signal.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.signal.SignalReader;

/** action 모듈이 참조하는 최소 read 경계. Signal 존재·스코프·제목과 민수용 근거만 노출하고, 나머지는 이 nested 모듈 안에 둔다. */
@Service
@RequiredArgsConstructor
class SignalReaderImpl implements SignalReader {

  private final SignalRepository signalRepository;
  private final SignalEvidenceRepository signalEvidenceRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<Snapshot> findLive(UUID signalId) {
    return signalRepository
        .findByIdAndDeletedAtIsNull(signalId)
        .map(
            signal ->
                new Snapshot(
                    signal.getId(),
                    signal.getWorkspaceId(),
                    signal.getProjectId(),
                    signal.getTitle()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<EvidenceRow> readEvidence(UUID signalId) {
    return signalEvidenceRepository
        .findBySignalIdOrderBySortOrderAscSourceRefIdAsc(signalId)
        .stream()
        .map(link -> new EvidenceRow(link.getTarget(), link.getChange(), link.getImpact()))
        .toList();
  }
}
