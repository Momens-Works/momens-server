package works.momens.server.context.internal;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.context.EntityRelationCommand;
import works.momens.server.context.EntityRelationWriter;

@Service
@RequiredArgsConstructor
class EntityRelationWriterImpl implements EntityRelationWriter {

  private final EntityManager entityManager;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID create(EntityRelationCommand command) {
    UUID relationId = UUID.randomUUID();
    entityManager
        .createNativeQuery(
            "INSERT INTO entity_relations "
                + "(id, workspace_id, from_entity_type, from_entity_id, relation_type, "
                + "to_entity_type, to_entity_id, created_at, updated_at) "
                + "VALUES (:id, :workspaceId, :fromType, :fromId, :relationType, "
                + ":toType, :toId, :createdAt, :updatedAt)")
        .setParameter("id", relationId)
        .setParameter("workspaceId", command.workspaceId())
        .setParameter("fromType", command.fromEntityType())
        .setParameter("fromId", command.fromEntityId())
        .setParameter("relationType", command.relationType())
        .setParameter("toType", command.toEntityType())
        .setParameter("toId", command.toEntityId())
        .setParameter("createdAt", Instant.now())
        .setParameter("updatedAt", Instant.now())
        .executeUpdate();
    return relationId;
  }
}
