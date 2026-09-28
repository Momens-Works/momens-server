package works.momens.server.mcp.grant;

import java.time.Instant;
import java.util.UUID;

/** Public write API for MCP grants. */
public interface McpGrantWriter {

  McpGrantDetail create(CreateMcpGrantCommand command);

  /** Reapproval atomically revokes an active grant and its tokens, then starts a new lifecycle. */
  McpGrantDetail replace(CreateMcpGrantCommand command);

  void revoke(UUID grantId, Instant revokedAt);

  /** Records successful verification without changing revoked grants or moving time backwards. */
  boolean recordUsage(UUID grantId, Instant usedAt);
}
