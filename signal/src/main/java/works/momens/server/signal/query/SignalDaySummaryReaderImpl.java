package works.momens.server.signal.query;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.signal.SignalDaySummaryReader;

@Service
@RequiredArgsConstructor
class SignalDaySummaryReaderImpl implements SignalDaySummaryReader {

  private final SignalDaySummaryRepository signalDaySummaryRepository;

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findSummary(UUID workspaceId, UUID projectId, LocalDate summaryDate) {
    return signalDaySummaryRepository
        .findSummary(workspaceId, projectId, summaryDate)
        .map(SignalDaySummary::getSummary);
  }
}
