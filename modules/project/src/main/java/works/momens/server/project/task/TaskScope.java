package works.momens.server.project.task;

import java.util.UUID;

/** 태스크가 속한 workspace와 project입니다. */
public record TaskScope(UUID workspaceId, UUID projectId) {}
