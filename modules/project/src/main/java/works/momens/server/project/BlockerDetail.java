package works.momens.server.project;

import java.time.Instant;
import java.util.UUID;

/** 웹 snapshot의 {@code blockers} 구획에 필요한 blocker 조회 결과. */
public record BlockerDetail(
    UUID id,
    UUID workspaceId,
    String description,
    String status,
    String blockedEntityType,
    UUID blockedEntityId,
    Instant createdAt,
    Instant updatedAt,
    Instant resolvedAt) {}
