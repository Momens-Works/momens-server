package works.momens.server.context;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import works.momens.server.common.config.DevOnly;

/**
 * dev 시연용 entity_relations writer.
 *
 * <p>Signal evidence의 source_ref를 새 태스크의 관련자료로 연결하는 dev 한정 예외다. 구현은 {@code @DevOnly}로 게이트되어 prod에는
 * 등록되지 않고 호출자 트랜잭션에 합류한다.
 */
@DevOnly
@Component
@RequiredArgsConstructor
public class DevEntityRelationWriter {

  private final JdbcClient jdbcClient;

  @Transactional(propagation = Propagation.MANDATORY)
  public void linkTaskMaterials(UUID workspaceId, UUID taskId, List<UUID> sourceRefIds) {
    OffsetDateTime linkedAt = OffsetDateTime.now(ZoneOffset.UTC);
    for (int position = 0; position < sourceRefIds.size(); position++) {
      jdbcClient
          .sql(
              "INSERT INTO entity_relations "
                  + "(id, workspace_id, from_entity_type, from_entity_id, relation_type, "
                  + "to_entity_type, to_entity_id, created_at) "
                  + "VALUES (:id, :workspaceId, 'TASK', :taskId, 'LINKED_TO', "
                  + "'SOURCE_OBJECT', :sourceRefId, :createdAt)")
          .param("id", UUID.randomUUID())
          .param("workspaceId", workspaceId)
          .param("taskId", taskId)
          .param("sourceRefId", sourceRefIds.get(position))
          .param("createdAt", linkedAt.minusSeconds(position))
          .update();
    }
  }
}
