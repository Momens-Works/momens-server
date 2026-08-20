package works.momens.server.source;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 워크스페이스에 연결된 외부 source 한 건을 나타냅니다.
 *
 * <p>{@code source_connections}에 저장된 값을 포함하며, 레거시의 연결 목록 응답과 동일한 필드로 구성됩니다.
 */
public record SourceConnectionDetail(
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
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {}
