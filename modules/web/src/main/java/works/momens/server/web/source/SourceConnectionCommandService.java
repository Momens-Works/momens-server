package works.momens.server.web.source;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import works.momens.server.common.api.BusinessException;
import works.momens.server.source.SourceConnectionReader;
import works.momens.server.source.SourceConnectionWriter;
import works.momens.server.source.SourceErrorCode;
import works.momens.server.web.WorkspaceAccessChecker;
import works.momens.server.workspace.membership.WorkspaceRole;

@Service
@RequiredArgsConstructor
class SourceConnectionCommandService {

  private final SourceConnectionReader sourceConnectionReader;
  private final SourceConnectionWriter sourceConnectionWriter;
  private final WorkspaceAccessChecker workspaceAccessChecker;

  public void requestResync(UUID connectionId, UUID userId) {
    UUID workspaceId =
        sourceConnectionReader
            .findWorkspaceId(connectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        SourceErrorCode.SOURCE_CONNECTION_NOT_FOUND,
                        Map.of("source_connection_id", connectionId.toString())));
    workspaceAccessChecker.requireRoleAtLeast(workspaceId, userId, WorkspaceRole.ADMIN);
    sourceConnectionWriter.requestResync(connectionId, workspaceId);
  }
}
