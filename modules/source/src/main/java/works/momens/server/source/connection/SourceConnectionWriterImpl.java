package works.momens.server.source.connection;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.api.BusinessException;
import works.momens.server.source.SourceConnectionWriter;
import works.momens.server.source.SourceErrorCode;

@Service
@RequiredArgsConstructor
class SourceConnectionWriterImpl implements SourceConnectionWriter {

  private final SourceConnectionRepository sourceConnectionRepository;

  @Override
  @Transactional
  public void requestResync(UUID connectionId, UUID workspaceId) {
    if (sourceConnectionRepository.requestResync(connectionId, workspaceId, Instant.now()) == 0) {
      throw new BusinessException(
          SourceErrorCode.SOURCE_CONNECTION_NOT_FOUND,
          Map.of("source_connection_id", connectionId.toString()));
    }
  }
}
