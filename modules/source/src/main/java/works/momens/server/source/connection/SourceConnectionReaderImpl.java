package works.momens.server.source.connection;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.source.SourceConnectionDetail;
import works.momens.server.source.SourceConnectionReader;

@Component
@RequiredArgsConstructor
class SourceConnectionReaderImpl implements SourceConnectionReader {

  private final SourceConnectionRepository sourceConnectionRepository;

  @Override
  @Transactional(readOnly = true)
  public List<SourceConnectionDetail> listDetailsByWorkspaceId(UUID workspaceId) {
    return sourceConnectionRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
        .map(SourceConnectionReaderImpl::toDetail)
        .toList();
  }

  private static SourceConnectionDetail toDetail(SourceConnection connection) {
    return new SourceConnectionDetail(
        connection.getId(),
        connection.getWorkspaceId(),
        connection.getSourceType(),
        connection.getStatus(),
        connection.getExternalWorkspaceId(),
        connection.getExternalWorkspaceName(),
        connection.getConnectedByUserId(),
        connection.getConnectedAt(),
        connection.getLastSyncedAt(),
        connection.getDisabledAt(),
        connection.getResyncRequestedAt(),
        connection.getCapturesReadCount(),
        connection.getCandidatesExtractedCount(),
        connection.getMetadata(),
        connection.getCreatedAt(),
        connection.getUpdatedAt());
  }
}
