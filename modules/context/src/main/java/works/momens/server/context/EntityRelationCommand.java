package works.momens.server.context;

import java.util.UUID;

/** entity_relations에 기록할 연결 명령입니다. 호출자는 자기 트랜잭션 안에서 사용합니다. */
public record EntityRelationCommand(
    UUID workspaceId,
    String fromEntityType,
    UUID fromEntityId,
    String relationType,
    String toEntityType,
    UUID toEntityId) {}
