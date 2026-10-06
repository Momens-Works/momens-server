package works.momens.server.project.blocker;

import java.time.Instant;
import java.util.UUID;

/** blocker 생성 결과와 웹 snapshot의 {@code blockers} 구획에 사용하는 저장 필드. */
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
