package works.momens.server.web.source.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import works.momens.server.source.SourceConnectionDetail;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceConnectionResponse(
    UUID id,
    UUID workspaceId,
    String sourceType,
    String status,
    String externalWorkspaceId,
    String externalWorkspaceName,
    UUID connectedByUserId,
    Instant connectedAt,
    Instant lastSyncedAt,
    Instant disabledAt,
    Instant resyncRequestedAt,
    long capturesReadCount,
    long candidatesExtractedCount,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {

  public static SourceConnectionResponse from(SourceConnectionDetail detail) {
    return new SourceConnectionResponse(
        detail.id(),
        detail.workspaceId(),
        detail.sourceType(),
        detail.status(),
        detail.externalWorkspaceId(),
        detail.externalWorkspaceName(),
        detail.connectedByUserId(),
        detail.connectedAt(),
        detail.lastSyncedAt(),
        detail.disabledAt(),
        detail.resyncRequestedAt(),
        detail.capturesReadCount(),
        detail.candidatesExtractedCount(),
        detail.metadata(),
        detail.createdAt(),
        detail.updatedAt());
  }
}
