package works.momens.server.project.task;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** 웹 Product API task update 응답 투영입니다. */
public record TaskUpdateDetail(
    UUID id,
    UUID workspaceId,
    UUID projectId,
    UUID taskId,
    UUID authorId,
    String body,
    String kind,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt) {}
