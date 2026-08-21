package works.momens.server.context.internal;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.context.EntityRelationCommand;
import works.momens.server.context.EntityRelationWriter;

/**
 * 레거시 {@code relation/repository.go}의 {@code LinkWithType}을 옮긴 구현입니다.
 *
 * <p>가장 최근 행 하나를 보고 세 갈래로 갈립니다. 활성이면 no-op, 소프트 삭제면 되살리기, 없으면 INSERT입니다. 레거시가 같은 순서 ({@code ORDER
 * BY created_at DESC LIMIT 1})로 판단하므로 중복 행이 이미 쌓인 워크스페이스에서도 두 서버의 결과가 같습니다.
 */
@Service
@RequiredArgsConstructor
class EntityRelationWriterImpl implements EntityRelationWriter {

  private final EntityManager entityManager;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID link(EntityRelationCommand command) {
    ExistingRelation existing = findLatest(command);
    if (existing != null && existing.deletedAt() == null) {
      return existing.id();
    }
    if (existing != null) {
      entityManager
          .createNativeQuery(
              "UPDATE entity_relations SET deleted_at = NULL, updated_at = :now WHERE id = :id")
          .setParameter("now", Instant.now())
          .setParameter("id", existing.id())
          .executeUpdate();
      return existing.id();
    }

    UUID relationId = UUID.randomUUID();
    Instant now = Instant.now();
    entityManager
        .createNativeQuery(
            """
            INSERT INTO entity_relations (
                id, workspace_id, from_entity_type, from_entity_id, relation_type,
                to_entity_type, to_entity_id, created_at, updated_at)
            VALUES (:id, :workspaceId, :fromType, :fromId, :relationType,
                    :toType, :toId, :now, :now)
            """)
        .setParameter("id", relationId)
        .setParameter("workspaceId", command.workspaceId())
        .setParameter("fromType", command.fromEntityType())
        .setParameter("fromId", command.fromEntityId())
        .setParameter("relationType", command.relationType())
        .setParameter("toType", command.toEntityType())
        .setParameter("toId", command.toEntityId())
        .setParameter("now", now)
        .executeUpdate();
    return relationId;
  }

  private ExistingRelation findLatest(EntityRelationCommand command) {
    List<?> rows =
        entityManager
            .createNativeQuery(
                """
            SELECT id, deleted_at FROM entity_relations
            WHERE workspace_id = :workspaceId AND from_entity_type = :fromType
              AND from_entity_id = :fromId AND relation_type = :relationType
              AND to_entity_type = :toType AND to_entity_id = :toId
            ORDER BY created_at DESC LIMIT 1
            """)
            .setParameter("workspaceId", command.workspaceId())
            .setParameter("fromType", command.fromEntityType())
            .setParameter("fromId", command.fromEntityId())
            .setParameter("relationType", command.relationType())
            .setParameter("toType", command.toEntityType())
            .setParameter("toId", command.toEntityId())
            .getResultList();
    if (rows.isEmpty()) {
      return null;
    }
    Object[] row = (Object[]) rows.getFirst();
    return new ExistingRelation((UUID) row[0], row[1]);
  }

  /** {@code deletedAt}은 null 여부만 보므로 JDBC가 돌려주는 타입 그대로 담습니다. */
  private record ExistingRelation(UUID id, Object deletedAt) {}
}
