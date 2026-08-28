package works.momens.server.source;

import java.time.Instant;
import java.util.UUID;

/** 레거시 task context source_ref wire shape의 저장값 투영입니다. */
public record LegacySourceRefDetail(
    UUID id,
    UUID workspaceId,
    String sourceType,
    String sourceObjectType,
    String sourceObjectId,
    String sourceUrl,
    String title,
    String snippet,
    String authorName,
    String authorEmail,
    Instant sourceCreatedAt,
    String visibility,
    String permissionKey,
    UUID verifiedByUserId,
    Instant verifiedAt,
    Instant createdAt,
    Instant updatedAt) {}
