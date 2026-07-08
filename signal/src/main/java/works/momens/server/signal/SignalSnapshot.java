package works.momens.server.signal;

import java.util.UUID;

/** action 처리에 필요한 Signal 최소 스냅샷. */
public record SignalSnapshot(UUID id, UUID workspaceId, UUID projectId, String title) {}
