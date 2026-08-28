package works.momens.server.context;

import java.util.UUID;

public record EntityRelationCommand(
    UUID workspaceId,
    EntityType fromEntityType,
    UUID fromEntityId,
    RelationType relationType,
    EntityType toEntityType,
    UUID toEntityId) {}
