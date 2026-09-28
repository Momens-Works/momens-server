package works.momens.server.mcp.grant;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Active connection details for user-facing grant management. */
public record McpGrantConnection(
    UUID id,
    String clientId,
    UUID userId,
    UUID workspaceId,
    List<String> scopes,
    Instant lastUsedAt,
    Instant createdAt) {}
